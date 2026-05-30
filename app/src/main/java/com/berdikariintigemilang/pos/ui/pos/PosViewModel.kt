package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.scanner.HardwareScannerBus
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.cart.DiscountMode
import com.berdikariintigemilang.pos.data.remote.AppliedBundleDto
import com.berdikariintigemilang.pos.data.remote.ReceiptSettingDto
import com.berdikariintigemilang.pos.data.remote.taxFor
import com.berdikariintigemilang.pos.data.remote.totalFor
import com.berdikariintigemilang.pos.data.repository.BundleRepository
import com.berdikariintigemilang.pos.data.repository.ProductRepository
import com.berdikariintigemilang.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
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

private data class BundleCalc(val discount: Double = 0.0, val applied: List<AppliedBundleDto> = emptyList())

@HiltViewModel
class PosViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val bundleRepository: BundleRepository,
    private val settingsRepository: SettingsRepository,
    private val productRepository: ProductRepository,
    scannerBus: HardwareScannerBus
) : ViewModel() {

    private val bundle = MutableStateFlow(BundleCalc())
    private val tax = MutableStateFlow<ReceiptSettingDto?>(null)

    /** Barcode dari alat scan fisik (HID). Dikoleksi oleh layar Kasir saja. */
    val scannedBarcodes: SharedFlow<String> = scannerBus.barcodes

    // Sinyal beep+getar saat sebuah item berhasil masuk keranjang via scan.
    private val _scanFeedback = Channel<Unit>(Channel.BUFFERED)
    val scanFeedback = _scanFeedback.receiveAsFlow()

    // Pesan singkat (mis. tidak ditemukan / stok kurang) untuk snackbar.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // Cegah pembacaan ganda super cepat (auto-sense / double-fire) untuk kode sama.
    private var lastScanCode: String? = null
    private var lastScanAt = 0L

    init {
        viewModelScope.launch {
            (settingsRepository.receiptSetting() as? ApiResult.Success)?.let { tax.value = it.data }
        }
        // Hitung ulang potongan bundle tiap keranjang berubah (debounce via collectLatest).
        viewModelScope.launch {
            cartManager.lines.collectLatest { lines ->
                if (lines.isEmpty()) {
                    bundle.value = BundleCalc()
                    return@collectLatest
                }
                delay(300)
                when (val res = bundleRepository.calculate(lines)) {
                    is ApiResult.Success -> bundle.value = BundleCalc(res.data.bundleDiscount, res.data.appliedBundles)
                    is ApiResult.Error -> bundle.value = BundleCalc()
                }
            }
        }
    }

    val state: StateFlow<PosCartState> =
        combine(
            cartManager.lines,
            cartManager.discount,
            cartManager.discountMode,
            cartManager.discountInput,
            combine(bundle, tax) { b, taxCfg -> b to taxCfg }
        ) { lines, discount, mode, input, bundleTax ->
            val (b, taxCfg) = bundleTax
            val subtotal = lines.sumOf { it.lineSubtotal }
            val base = (subtotal - discount - b.discount).coerceAtLeast(0.0)
            PosCartState(
                lines = lines,
                subtotal = subtotal,
                discount = discount,
                discountMode = mode,
                discountInput = input,
                bundleDiscount = b.discount,
                appliedBundles = b.applied,
                taxAmount = taxCfg.taxFor(base),
                taxInclusive = taxCfg?.taxInclusive ?: true,
                total = taxCfg.totalFor(base),
                itemCount = lines.sumOf { it.quantity }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosCartState())

    fun increment(line: CartLine) = cartManager.setQuantity(line.productId, line.quantity + 1)
    fun decrement(line: CartLine) = cartManager.setQuantity(line.productId, line.quantity - 1)
    fun setQuantity(productId: Long, qty: Int) = cartManager.setQuantity(productId, qty)
    fun remove(productId: Long) = cartManager.remove(productId)
    fun setDiscountInput(value: Double) = cartManager.setDiscountInput(value)
    fun setDiscountMode(mode: DiscountMode) = cartManager.setDiscountMode(mode)
    fun clear() = cartManager.clear()

    /**
     * Barcode hasil scan alat fisik: cari produk lalu langsung tambah 1 ke
     * keranjang (tanpa dialog). Beri umpan balik beep+getar saat berhasil;
     * tampilkan pesan bila tidak ditemukan atau stok tidak mencukupi.
     */
    fun onScannedBarcode(code: String) {
        val barcode = code.trim()
        if (barcode.isEmpty()) return
        val now = System.currentTimeMillis()
        if (barcode == lastScanCode && now - lastScanAt < SAME_CODE_DEBOUNCE_MS) return
        lastScanCode = barcode
        lastScanAt = now
        viewModelScope.launch {
            when (val res = productRepository.byBarcode(barcode)) {
                is ApiResult.Success -> {
                    if (cartManager.addProduct(res.data, 1)) {
                        _scanFeedback.send(Unit)
                    } else {
                        _messages.send("Stok ${res.data.name} tidak mencukupi")
                    }
                }
                is ApiResult.Error -> _messages.send(
                    if (res.httpStatus == 404) "Barcode $barcode tidak ditemukan" else res.message
                )
            }
        }
    }

    private companion object {
        const val SAME_CODE_DEBOUNCE_MS = 400L
    }
}
