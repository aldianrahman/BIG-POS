package com.berdikariintigemilang.pos.data.cart

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cartDataStore by preferencesDataStore(name = "pos_cart")

@JsonClass(generateAdapter = true)
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
 * Keranjang yang dibagikan lintas layar POS (single cashier device).
 *
 * Dipertahankan ke DataStore agar isi keranjang tidak hilang saat aplikasi
 * ditutup / di-kill; dipulihkan otomatis saat aplikasi dibuka kembali, dan
 * dibersihkan setelah transaksi selesai.
 */
@Singleton
class CartManager @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val linesType: Type = Types.newParameterizedType(List::class.java, CartLine::class.java)
    private val linesAdapter: JsonAdapter<List<CartLine>> = moshi.adapter(linesType)

    private object Keys {
        val LINES = stringPreferencesKey("cart_lines")
        val DISCOUNT = doublePreferencesKey("cart_discount")
    }

    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val _discount = MutableStateFlow(0.0)
    val discount: StateFlow<Double> = _discount.asStateFlow()

    init {
        scope.launch {
            // Pulihkan dulu isi keranjang tersimpan SEBELUM mulai menyimpan
            // perubahan, agar state kosong awal tidak menimpa data tersimpan.
            restore()
            combine(_lines, _discount) { lines, discount -> lines to discount }
                .collect { (lines, discount) -> persist(lines, discount) }
        }
    }

    private suspend fun restore() {
        val prefs = context.cartDataStore.data.first()
        val saved = prefs[Keys.LINES]?.let { json ->
            runCatching { linesAdapter.fromJson(json) }.getOrNull()
        } ?: emptyList()
        _lines.value = saved
        _discount.value = prefs[Keys.DISCOUNT] ?: 0.0
    }

    private suspend fun persist(lines: List<CartLine>, discount: Double) {
        context.cartDataStore.edit { prefs ->
            prefs[Keys.LINES] = linesAdapter.toJson(lines)
            prefs[Keys.DISCOUNT] = discount
        }
    }

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
