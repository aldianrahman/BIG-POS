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
import com.berdikariintigemilang.pos.data.remote.ReceiptSettingDto
import com.berdikariintigemilang.pos.data.remote.taxFor
import com.berdikariintigemilang.pos.data.remote.totalFor
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.BundleRepository
import com.berdikariintigemilang.pos.data.repository.ProductRepository
import com.berdikariintigemilang.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import com.berdikariintigemilang.pos.data.repository.CatalogCacheRepository
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.berdikariintigemilang.pos.data.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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
 * State dialog "Ubah Harga" satu baris keranjang. [line] non-null berarti dialog
 * terbuka untuk baris tersebut; [submitting] saat verifikasi kredensial berjalan;
 * [error] pesan bila verifikasi gagal / tidak berwenang / harga tidak valid.
 */
data class PriceEditState(
    val line: CartLine? = null,
    val submitting: Boolean = false,
    val error: String? = null
)

/**
 * Keranjang POS dengan perhitungan total (bundle + PPN) sepenuhnya lokal,
 * sehingga harga tetap benar tanpa sinyal. Saat ada koneksi, katalog di-cache
 * (untuk persiapan offline) dan antrian transaksi dikirim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PosViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val bundleRepository: BundleRepository,
    private val settingsRepository: SettingsRepository,
    private val productRepository: ProductRepository,
    private val authRepository: AuthRepository,
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

    // Event scan hardware sukses (untuk beep + getar) & pesan singkat (toast).
    private val _scanned = Channel<Unit>(Channel.BUFFERED)
    val scanned = _scanned.receiveAsFlow()
    private val _scanMessages = Channel<String>(Channel.BUFFERED)
    val scanMessages = _scanMessages.receiveAsFlow()

    // Dialog ubah harga per baris keranjang (verifikasi sales 38/54/60).
    private val _priceEditState = MutableStateFlow(PriceEditState())
    val priceEditState: StateFlow<PriceEditState> = _priceEditState
    private val _priceEditMessages = Channel<String>(Channel.BUFFERED)
    val priceEditMessages = _priceEditMessages.receiveAsFlow()

    // Anti dobel-baca dari satu kali scan (DataWedge kadang mengirim ganda).
    private var lastScanCode: String? = null
    private var lastScanAt = 0L

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

    // ── Ubah harga per baris (butuh verifikasi sales berwenang) ───────────────
    fun startPriceEdit(line: CartLine) { _priceEditState.value = PriceEditState(line = line) }
    fun dismissPriceEdit() { _priceEditState.value = PriceEditState() }

    /**
     * Verifikasi kredensial sales lalu terapkan harga baru ke baris keranjang.
     * Hanya id berwenang (Constants.PRICE_EDIT_AUTHORIZED_IDS) yang diterima, dan
     * harga baru tidak boleh melebihi harga master. Total otomatis ikut diperbarui
     * karena keranjang berubah. Setiap perubahan dicatat ke log saat pembayaran.
     */
    fun confirmPriceEdit(username: String, password: String, newPrice: Double) {
        val line = _priceEditState.value.line ?: return
        val invalid = when {
            username.isBlank() || password.isBlank() -> "Username & password wajib diisi"
            newPrice <= 0.0 -> "Harga harus lebih dari 0"
            newPrice > line.masterPrice -> "Harga tidak boleh lebih besar dari harga master"
            else -> null
        }
        if (invalid != null) {
            _priceEditState.value = _priceEditState.value.copy(error = invalid)
            return
        }
        _priceEditState.value = _priceEditState.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val res = authRepository.verifyPriceEditor(username, password)) {
                is ApiResult.Success -> {
                    val ok = cartManager.overrideLinePrice(line.productId, newPrice, res.data.id, res.data.username)
                    if (ok) {
                        _priceEditState.value = PriceEditState()
                        _priceEditMessages.send("Harga ${line.name} diperbarui")
                    } else {
                        _priceEditState.value = _priceEditState.value.copy(
                            submitting = false,
                            error = "Gagal menerapkan harga"
                        )
                    }
                }
                is ApiResult.Error -> _priceEditState.value =
                    _priceEditState.value.copy(submitting = false, error = res.message)
            }
        }
    }

    /**
     * Scan dari scanner hardware Zebra (DataWedge) di halaman kasir: cari produk
     * berdasarkan barcode, lalu langsung tambah qty 1 ke keranjang (akumulatif
     * bila sudah ada). Umpan balik via [scanned] (beep/getar) & [scanMessages].
     */
    fun onHardwareScan(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        val now = System.currentTimeMillis()
        if (code == lastScanCode && now - lastScanAt < 700) return
        lastScanCode = code
        lastScanAt = now
        viewModelScope.launch {
            when (val res = productRepository.byBarcode(code)) {
                is ApiResult.Success -> {
                    val product = res.data
                    if (cartManager.addProduct(product, 1)) {
                        _scanned.send(Unit)
                        _scanMessages.send("${product.name} +1")
                    } else {
                        _scanMessages.send("Stok ${product.name} tidak mencukupi")
                    }
                }
                is ApiResult.Error -> _scanMessages.send(
                    if (res.httpStatus == 404) "Barcode $code tidak ada di database" else res.message
                )
            }
        }
    }
}
