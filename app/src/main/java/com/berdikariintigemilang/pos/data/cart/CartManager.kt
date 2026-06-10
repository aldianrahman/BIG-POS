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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    /** Harga efektif yang ditagih (harga master, atau harga sales bila diubah). */
    val unitPrice: Double,
    val quantity: Int,
    val stock: Int,
    /**
     * Harga master (harga jual asli dari master produk). Jadi acuan batas atas
     * saat mengubah harga & dicatat di log. Tidak ikut berubah saat harga diedit.
     */
    val masterPrice: Double = unitPrice,
    /** Id user (mis. 95/99/118) yang menurunkan harga baris ini; null bila masih harga master. */
    val priceEditedByUserId: Long? = null,
    /** Username/nama karyawan yang mengubah harga (untuk log & tampilan). */
    val priceEditedByName: String? = null
) {
    val lineSubtotal: Double get() = unitPrice * quantity
    /** True bila harga baris ini sudah diturunkan dari harga master oleh sales. */
    val isPriceEdited: Boolean get() = priceEditedByUserId != null && unitPrice < masterPrice
}

/** Cara input diskon: nominal Rupiah atau persentase dari subtotal. */
enum class DiscountMode { RUPIAH, PERCENT }

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
        val DISCOUNT_INPUT = doublePreferencesKey("cart_discount_input")
        val DISCOUNT_MODE = stringPreferencesKey("cart_discount_mode")
    }

    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    /** Mode input diskon (Rp atau %). */
    private val _discountMode = MutableStateFlow(DiscountMode.RUPIAH)
    val discountMode: StateFlow<DiscountMode> = _discountMode.asStateFlow()

    /** Nilai mentah yang diketik kasir (Rupiah pada mode RUPIAH, atau persen pada mode PERCENT). */
    private val _discountInput = MutableStateFlow(0.0)
    val discountInput: StateFlow<Double> = _discountInput.asStateFlow()

    /** Diskon nominal (Rp) hasil hitung dari mode+input, dibatasi 0..subtotal. */
    val discount: StateFlow<Double> =
        combine(_lines, _discountMode, _discountInput) { lines, mode, input ->
            nominalDiscount(lines.sumOf { it.lineSubtotal }, mode, input)
        }.stateIn(scope, SharingStarted.Eagerly, 0.0)

    init {
        scope.launch {
            // Pulihkan dulu isi keranjang tersimpan SEBELUM mulai menyimpan
            // perubahan, agar state kosong awal tidak menimpa data tersimpan.
            restore()
            combine(_lines, _discountMode, _discountInput) { lines, mode, input ->
                Triple(lines, mode, input)
            }.collect { (lines, mode, input) -> persist(lines, mode, input) }
        }
    }

    /** Hitung diskon nominal (Rp) dari mode+input, dibatasi maksimal subtotal. */
    private fun nominalDiscount(subtotal: Double, mode: DiscountMode, input: Double): Double {
        val raw = when (mode) {
            DiscountMode.PERCENT -> subtotal * (input.coerceIn(0.0, 100.0) / 100.0)
            DiscountMode.RUPIAH -> input
        }
        return raw.coerceIn(0.0, subtotal)
    }

    private suspend fun restore() {
        val prefs = context.cartDataStore.data.first()
        val saved = prefs[Keys.LINES]?.let { json ->
            runCatching { linesAdapter.fromJson(json) }.getOrNull()
        } ?: emptyList()
        _lines.value = saved
        _discountInput.value = prefs[Keys.DISCOUNT_INPUT] ?: 0.0
        _discountMode.value = runCatching {
            DiscountMode.valueOf(prefs[Keys.DISCOUNT_MODE] ?: DiscountMode.RUPIAH.name)
        }.getOrDefault(DiscountMode.RUPIAH)
    }

    private suspend fun persist(lines: List<CartLine>, mode: DiscountMode, input: Double) {
        context.cartDataStore.edit { prefs ->
            prefs[Keys.LINES] = linesAdapter.toJson(lines)
            prefs[Keys.DISCOUNT_INPUT] = input
            prefs[Keys.DISCOUNT_MODE] = mode.name
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
                    stock = stock,
                    masterPrice = product.sellingPrice
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

    /**
     * Ubah harga satuan satu baris keranjang menjadi [newPrice] (harga jual sales)
     * untuk transaksi ini saja — TIDAK mengubah harga master produk. Perubahan
     * dicatat atas nama [byUserId]/[byName]. Harga baru dibatasi 0..masterPrice;
     * mengembalikan false bila [newPrice] melebihi harga master (tidak diterapkan).
     */
    fun overrideLinePrice(productId: Long, newPrice: Double, byUserId: Long, byName: String): Boolean {
        var applied = false
        _lines.update { current ->
            current.map { line ->
                when {
                    line.productId != productId -> line
                    newPrice > line.masterPrice -> line // tolak: tidak boleh lebih besar dari harga master
                    else -> {
                        applied = true
                        line.copy(
                            unitPrice = newPrice.coerceIn(0.0, line.masterPrice),
                            priceEditedByUserId = byUserId,
                            priceEditedByName = byName
                        )
                    }
                }
            }
        }
        return applied
    }

    fun remove(productId: Long) {
        _lines.update { it.filterNot { line -> line.productId == productId } }
    }

    fun setDiscountMode(mode: DiscountMode) {
        _discountMode.value = mode
        // Ganti mode mereset nilai agar tak salah tafsir (mis. 5000 jadi 5000%).
        _discountInput.value = 0.0
    }

    fun setDiscountInput(value: Double) {
        _discountInput.value = value.coerceAtLeast(0.0)
    }

    /**
     * Muat ulang isi keranjang dari sebuah snapshot (mis. saat melanjutkan
     * transaksi gantung). Menimpa isi keranjang aktif saat ini.
     */
    fun load(lines: List<CartLine>, discountMode: DiscountMode, discountInput: Double) {
        _lines.value = lines
        _discountMode.value = discountMode
        _discountInput.value = discountInput.coerceAtLeast(0.0)
    }

    fun clear() {
        _lines.value = emptyList()
        _discountInput.value = 0.0
        _discountMode.value = DiscountMode.RUPIAH
    }
}
