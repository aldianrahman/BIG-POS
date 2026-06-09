package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.cart.HeldSale
import com.berdikariintigemilang.pos.data.cart.HeldSaleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Layar antrian transaksi gantung: menampilkan daftar [HeldSale], melanjutkan
 * salah satunya ke keranjang aktif, atau menghapusnya.
 */
@HiltViewModel
class HeldSalesViewModel @Inject constructor(
    private val heldStore: HeldSaleStore,
    private val cartManager: CartManager
) : ViewModel() {

    /** Antrian transaksi gantung (terbaru di atas). */
    val sales: StateFlow<List<HeldSale>> = heldStore.sales

    /**
     * Lanjutkan transaksi gantung [id]: muat kembali ke keranjang aktif lalu
     * hapus dari antrian. Bila keranjang aktif sedang berisi, keranjang itu
     * digantung otomatis terlebih dahulu agar isinya tidak hilang.
     */
    fun resume(id: String) {
        val sale = heldStore.get(id) ?: return
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
    }

    fun delete(id: String) = heldStore.remove(id)

    private companion object {
        const val AUTO_HOLD_LABEL = "Keranjang berjalan (otomatis)"
    }
}
