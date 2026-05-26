package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.cart.CartManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PosCartState(
    val lines: List<CartLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val itemCount: Int = 0
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val canCheckout: Boolean get() = lines.isNotEmpty() && total >= 0.0
}

@HiltViewModel
class PosViewModel @Inject constructor(
    private val cartManager: CartManager
) : ViewModel() {

    val state: StateFlow<PosCartState> = combine(cartManager.lines, cartManager.discount) { lines, discount ->
        val subtotal = lines.sumOf { it.lineSubtotal }
        val total = (subtotal - discount).coerceAtLeast(0.0)
        PosCartState(
            lines = lines,
            subtotal = subtotal,
            discount = discount,
            total = total,
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
