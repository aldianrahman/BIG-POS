package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.data.cart.HeldSale
import com.berdikariintigemilang.pos.data.cart.HeldSaleStore
import com.berdikariintigemilang.pos.data.cart.ResumeHeldSale
import com.berdikariintigemilang.pos.data.repository.HoldPrintResult
import com.berdikariintigemilang.pos.data.repository.HoldTicketPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Layar antrian transaksi gantung: menampilkan daftar [HeldSale], melanjutkan
 * salah satunya ke keranjang aktif, mencetak ulang struk (berisi QR), atau
 * menghapusnya.
 */
@HiltViewModel
class HeldSalesViewModel @Inject constructor(
    private val heldStore: HeldSaleStore,
    private val resumeHeldSale: ResumeHeldSale,
    private val holdTicketPrinter: HoldTicketPrinter
) : ViewModel() {

    /** Antrian transaksi gantung (terbaru di atas). */
    val sales: StateFlow<List<HeldSale>> = heldStore.sales

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /**
     * Lanjutkan transaksi gantung [id] ke keranjang aktif. Bila keranjang aktif
     * sedang berisi, keranjang itu digantung otomatis dulu agar tidak hilang.
     */
    fun resume(id: String) {
        resumeHeldSale(id)
    }

    fun delete(id: String) = heldStore.remove(id)

    /** Cetak ulang struk gantung (mis. bila printer mati saat transaksi digantung). */
    fun reprint(id: String) {
        val sale = heldStore.get(id) ?: return
        viewModelScope.launch {
            val msg = when (val r = holdTicketPrinter.print(sale)) {
                HoldPrintResult.Printed -> "Struk gantung tercetak"
                HoldPrintResult.NoPrinter -> "Printer belum diatur. Buka tab Pengaturan untuk memilih printer."
                is HoldPrintResult.Failed -> "Gagal mencetak: ${r.message}"
            }
            _messages.send(msg)
        }
    }
}
