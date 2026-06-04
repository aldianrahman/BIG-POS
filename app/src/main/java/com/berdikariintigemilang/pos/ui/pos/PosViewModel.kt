package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
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
    private val productRepository: ProductRepository
) : ViewModel() {

    private val bundle = MutableStateFlow(BundleCalc())
    private val tax = MutableStateFlow<ReceiptSettingDto?>(null)

    // Event scan hardware sukses (untuk beep + getar) & pesan singkat (toast).
    private val _scanned = Channel<Unit>(Channel.BUFFERED)
    val scanned = _scanned.receiveAsFlow()
    private val _scanMessages = Channel<String>(Channel.BUFFERED)
    val scanMessages = _scanMessages.receiveAsFlow()

    // Anti dobel-baca dari satu kali scan (DataWedge kadang mengirim ganda).
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
