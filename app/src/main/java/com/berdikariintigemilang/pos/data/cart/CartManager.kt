package com.berdikariintigemilang.pos.data.cart

import com.berdikariintigemilang.pos.data.remote.ProductDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class CartLine(
    val productId: Long,
    val sku: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val stock: Int
) {
    val lineSubtotal: Double get() = unitPrice * quantity
}

/**
 * Keranjang in-memory yang dibagikan lintas layar POS (single cashier device).
 * Dibersihkan setelah transaksi selesai.
 */
@Singleton
class CartManager @Inject constructor() {

    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val _discount = MutableStateFlow(0.0)
    val discount: StateFlow<Double> = _discount.asStateFlow()

    /**
     * Tambah produk sebanyak [quantity] (akumulatif bila sudah ada di keranjang).
     * Return false bila melebihi stok tersedia.
     */
    fun addProduct(product: ProductDto, quantity: Int = 1): Boolean {
        if (quantity < 1) return false
        val stock = product.stockQuantity ?: Int.MAX_VALUE
        var added = true
        _lines.update { current ->
            val existing = current.find { it.productId == product.id }
            if (existing == null) {
                if (quantity > stock) { added = false; return@update current }
                current + CartLine(
                    productId = product.id,
                    sku = product.sku,
                    name = product.name,
                    unitPrice = product.sellingPrice,
                    quantity = quantity,
                    stock = stock
                )
            } else {
                val newQty = existing.quantity + quantity
                if (newQty > stock) { added = false; return@update current }
                current.map { if (it.productId == product.id) it.copy(quantity = newQty) else it }
            }
        }
        return added
    }

    fun setQuantity(productId: Long, quantity: Int) {
        _lines.update { current ->
            if (quantity <= 0) current.filterNot { it.productId == productId }
            else current.map {
                if (it.productId == productId) it.copy(quantity = quantity.coerceAtMost(it.stock)) else it
            }
        }
    }

    fun remove(productId: Long) {
        _lines.update { it.filterNot { line -> line.productId == productId } }
    }

    fun setDiscount(amount: Double) {
        _discount.value = amount.coerceAtLeast(0.0)
    }

    fun clear() {
        _lines.value = emptyList()
        _discount.value = 0.0
    }
}
