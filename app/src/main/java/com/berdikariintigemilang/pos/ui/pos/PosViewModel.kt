package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.remote.AppliedBundleDto
import com.berdikariintigemilang.pos.data.remote.ReceiptSettingDto
import com.berdikariintigemilang.pos.data.remote.taxFor
import com.berdikariintigemilang.pos.data.remote.totalFor
import com.berdikariintigemilang.pos.data.repository.BundleRepository
import com.berdikariintigemilang.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PosCartState(
    val lines: List<CartLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val bundle = MutableStateFlow(BundleCalc())
    private val tax = MutableStateFlow<ReceiptSettingDto?>(null)

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
        combine(cartManager.lines, cartManager.discount, bundle, tax) { lines, discount, b, taxCfg ->
            val subtotal = lines.sumOf { it.lineSubtotal }
            val base = (subtotal - discount - b.discount).coerceAtLeast(0.0)
            PosCartState(
                lines = lines,
                subtotal = subtotal,
                discount = discount,
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
    fun setDiscount(amount: Double) = cartManager.setDiscount(amount)
    fun clear() = cartManager.clear()
}
