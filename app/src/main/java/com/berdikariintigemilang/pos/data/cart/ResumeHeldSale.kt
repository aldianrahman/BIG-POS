package com.berdikariintigemilang.pos.data.cart

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lanjutkan transaksi gantung ke keranjang aktif. Bila keranjang aktif sedang
 * berisi, keranjang itu digantung otomatis terlebih dahulu agar isinya tidak
 * hilang. Dipakai bersama oleh layar daftar gantung dan saat scan QR struk
 * gantung di halaman kasir.
 */
@Singleton
class ResumeHeldSale @Inject constructor(
    private val cartManager: CartManager,
    private val heldStore: HeldSaleStore
) {
    /** Mengembalikan [HeldSale] yang dilanjutkan, atau null bila id tidak ditemukan. */
    operator fun invoke(id: String): HeldSale? {
        val sale = heldStore.get(id) ?: return null
        val current = cartManager.lines.value
        if (current.isNotEmpty()) {
            heldStore.add(
                label = AUTO_HOLD_LABEL,
                lines = current,
                discountMode = cartManager.discountMode.value,
                discountInput = cartManager.discountInput.value
            )
        }
        cartManager.load(sale.lines, sale.discountMode, sale.discountInput)
        heldStore.remove(id)
        return sale
    }

    private companion object {
        const val AUTO_HOLD_LABEL = "Keranjang berjalan (otomatis)"
    }
}
