package com.berdikariintigemilang.pos.data.cart

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.heldSalesDataStore by preferencesDataStore(name = "pos_held_sales")

/**
 * Satu transaksi yang "digantung" (hold/park sale): snapshot keranjang yang
 * disimpan sementara sehingga kasir bisa melayani pelanggan lain dulu, lalu
 * melanjutkannya kembali. Murni lokal di perangkat — tidak dikirim ke server
 * (belum jadi penjualan; baru tercatat setelah dibayar lewat alur normal).
 */
@JsonClass(generateAdapter = true)
data class HeldSale(
    val id: String,
    /** Keterangan opsional (mis. nama pelanggan / nomor antrian). Boleh kosong. */
    val label: String,
    val lines: List<CartLine>,
    val discountMode: DiscountMode,
    val discountInput: Double,
    val createdAt: Long,
    /** Nama kasir yang menggantung transaksi (untuk struk gantung). */
    val cashierName: String? = null
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val subtotal: Double get() = lines.sumOf { it.lineSubtotal }
}

/**
 * Daftar transaksi gantung (antrian "struk gantung").
 *
 * Disimpan ke DataStore (bukan Room) agar tahan saat aplikasi di-kill tanpa
 * perlu mengubah skema database — sejalan dengan cara [CartManager] menyimpan
 * keranjang aktif. Satu perangkat kasir.
 */
@Singleton
class HeldSaleStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listType: Type = Types.newParameterizedType(List::class.java, HeldSale::class.java)
    private val adapter: JsonAdapter<List<HeldSale>> = moshi.adapter(listType)

    private object Keys {
        val SALES = stringPreferencesKey("held_sales")
    }

    private val _sales = MutableStateFlow<List<HeldSale>>(emptyList())
    /** Antrian transaksi gantung; entri terbaru di urutan paling atas. */
    val sales: StateFlow<List<HeldSale>> = _sales.asStateFlow()

    init {
        scope.launch {
            // Pulihkan dulu SEBELUM mulai menyimpan, agar daftar kosong awal tidak
            // menimpa data tersimpan (pola sama seperti CartManager).
            restore()
            _sales.collect { persist(it) }
        }
    }

    private suspend fun restore() {
        val prefs = context.heldSalesDataStore.data.first()
        _sales.value = prefs[Keys.SALES]?.let { json ->
            runCatching { adapter.fromJson(json) }.getOrNull()
        } ?: emptyList()
    }

    private suspend fun persist(sales: List<HeldSale>) {
        context.heldSalesDataStore.edit { prefs ->
            prefs[Keys.SALES] = adapter.toJson(sales)
        }
    }

    /**
     * Gantung sebuah keranjang. Entri baru ditaruh di awal daftar agar mudah
     * ditemukan. Mengembalikan entri yang dibuat.
     */
    fun add(
        label: String,
        lines: List<CartLine>,
        discountMode: DiscountMode,
        discountInput: Double,
        cashierName: String? = null
    ): HeldSale {
        val sale = HeldSale(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            lines = lines,
            discountMode = discountMode,
            discountInput = discountInput,
            createdAt = System.currentTimeMillis(),
            cashierName = cashierName
        )
        _sales.update { listOf(sale) + it }
        return sale
    }

    fun get(id: String): HeldSale? = _sales.value.find { it.id == id }

    fun remove(id: String) {
        _sales.update { it.filterNot { sale -> sale.id == id } }
    }
}
