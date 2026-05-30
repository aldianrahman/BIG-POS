package com.berdikariintigemilang.pos.ui.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.cart.DiscountMode
import com.berdikariintigemilang.pos.data.pricing.LocalPricingCalculator
import com.berdikariintigemilang.pos.data.remote.AppliedBundleDto
import com.berdikariintigemilang.pos.data.repository.CatalogCacheRepository
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.berdikariintigemilang.pos.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PosCartState(
    val lines: List<CartLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val discountMode: DiscountMode = DiscountMode.RUPIAH,
    val discountInput: Double = 0.0,
    val bundleDiscount: Double = 0.0,
    val appliedBundles: List<AppliedBundleDto> = emptyList(),
    val taxAmount: Double = 0.0,
    val taxInclusive: Boolean = true,
    val total: Double = 0.0,
    val itemCount: Int = 0
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val canCheckout: Boolean get() = lines.isNotEmpty() && total >= 0.0
}

/**
 * Keranjang POS dengan perhitungan total (bundle + PPN) sepenuhnya lokal,
 * sehingga harga tetap benar tanpa sinyal. Saat ada koneksi, katalog di-cache
 * (untuk persiapan offline) dan antrian transaksi dikirim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PosViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val pricing: LocalPricingCalculator,
    private val catalogCache: CatalogCacheRepository,
    private val connectivity: ConnectivityObserver,
    private val offlineStore: OfflineTransactionStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private data class Inputs(
        val lines: List<CartLine>,
        val discount: Double,
        val mode: DiscountMode,
        val input: Double
    )

    val state: StateFlow<PosCartState> =
        combine(
            cartManager.lines,
            cartManager.discount,
            cartManager.discountMode,
            cartManager.discountInput
        ) { lines, discount, mode, input -> Inputs(lines, discount, mode, input) }
            .mapLatest { inp ->
                val p = pricing.price(inp.lines, inp.discount)
                PosCartState(
                    lines = inp.lines,
                    subtotal = p.subtotal,
                    discount = inp.discount,
                    discountMode = inp.mode,
                    discountInput = inp.input,
                    bundleDiscount = p.bundleDiscount,
                    appliedBundles = p.appliedBundles,
                    taxAmount = p.taxAmount,
                    taxInclusive = p.taxInclusive,
                    total = p.total,
                    itemCount = inp.lines.sumOf { it.quantity }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosCartState())

    /** Jumlah transaksi yang belum tersinkron (untuk indikator status). */
    val pendingCount: StateFlow<Int> =
        offlineStore.observeUnsyncedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Status koneksi internet. */
    val isOnline: StateFlow<Boolean> =
        connectivity.isOnline
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), connectivity.isOnlineNow())

    init {
        // Tiap kali ada koneksi (termasuk saat sinyal kembali): segarkan katalog bila
        // belum ada, refresh stok lokal (agar restock/penyesuaian dari server masuk),
        // lalu kirim antrian transaksi.
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                if (online) {
                    if (!catalogCache.isCatalogReady()) catalogCache.refreshAll()
                    catalogCache.refreshStock()
                    SyncScheduler.syncNow(appContext)
                }
            }
        }
    }

    /** Tarik ulang katalog dari server (untuk tombol "Perbarui katalog" / persiapan offline). */
    fun refreshCatalog() {
        viewModelScope.launch { catalogCache.refreshAll() }
    }

    /** Picu pengiriman antrian transaksi sekarang (saat ada koneksi). */
    fun syncNow() = SyncScheduler.syncNow(appContext)

    fun increment(line: CartLine) = cartManager.setQuantity(line.productId, line.quantity + 1)
    fun decrement(line: CartLine) = cartManager.setQuantity(line.productId, line.quantity - 1)
    fun setQuantity(productId: Long, qty: Int) = cartManager.setQuantity(productId, qty)
    fun remove(productId: Long) = cartManager.remove(productId)
    fun setDiscountInput(value: Double) = cartManager.setDiscountInput(value)
    fun setDiscountMode(mode: DiscountMode) = cartManager.setDiscountMode(mode)
    fun clear() = cartManager.clear()
}
