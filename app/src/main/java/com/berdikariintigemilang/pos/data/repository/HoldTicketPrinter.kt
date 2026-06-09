package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.datastore.PrinterStore
import com.berdikariintigemilang.pos.core.printer.PrinterManager
import com.berdikariintigemilang.pos.data.cart.HeldSale
import com.berdikariintigemilang.pos.data.cart.HoldQr
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.pricing.OfflineReceiptComposer
import com.berdikariintigemilang.pos.data.pricing.ReceiptLine
import javax.inject.Inject
import javax.inject.Singleton

/** Hasil percetakan struk transaksi gantung. */
sealed interface HoldPrintResult {
    data object Printed : HoldPrintResult
    data object NoPrinter : HoldPrintResult
    data class Failed(val message: String) : HoldPrintResult
}

/**
 * Menyusun & mencetak struk transaksi gantung (daftar item + QR berisi id).
 * Best-effort: tidak pernah melempar; kegagalan dikembalikan sebagai
 * [HoldPrintResult] agar proses menggantung transaksi tetap berhasil.
 */
@Singleton
class HoldTicketPrinter @Inject constructor(
    private val catalogDao: CatalogDao,
    private val composer: OfflineReceiptComposer,
    private val printerStore: PrinterStore,
    private val printerManager: PrinterManager
) {
    suspend fun print(sale: HeldSale): HoldPrintResult {
        val address = printerStore.get()?.address ?: return HoldPrintResult.NoPrinter
        val setting = catalogDao.receiptSetting()
        val text = composer.composeHoldTicket(
            label = sale.label,
            dateMillis = sale.createdAt,
            lines = sale.lines.map { ReceiptLine(it.name, it.quantity, it.unitPrice, it.lineSubtotal) },
            subtotal = sale.subtotal,
            itemCount = sale.itemCount,
            setting = setting
        )
        return try {
            printerManager.printHoldTicket(address, text, HoldQr.encode(sale.id))
            HoldPrintResult.Printed
        } catch (e: Exception) {
            HoldPrintResult.Failed(e.message ?: "Gagal mencetak")
        }
    }
}
