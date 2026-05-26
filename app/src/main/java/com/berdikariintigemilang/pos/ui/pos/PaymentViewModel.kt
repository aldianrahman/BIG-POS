package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.remote.TransactionItemRequest
import com.berdikariintigemilang.pos.data.remote.TransactionRequest
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PaymentUiState(
    val total: Double = 0.0,
    val cash: Long = 0,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val change: Long get() = (cash - total).toLong().coerceAtLeast(0)
    val sufficient: Boolean get() = cash >= total
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    // Idempotency key tetap selama layar ini hidup (retry aman tak dobel).
    private val idempotencyKey = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(
        PaymentUiState(total = computeTotal())
    )
    val state: StateFlow<PaymentUiState> = _state

    private val _success = Channel<Long>(Channel.BUFFERED)
    val success = _success.receiveAsFlow()

    private fun computeTotal(): Double {
        val subtotal = cartManager.lines.value.sumOf { it.lineSubtotal }
        return (subtotal - cartManager.discount.value).coerceAtLeast(0.0)
    }

    fun appendDigit(d: String) {
        _state.update {
            val newVal = (it.cash.toString().takeIf { c -> c != "0" }.orEmpty() + d)
                .take(12).toLongOrNull() ?: it.cash
            it.copy(cash = newVal, error = null)
        }
    }

    fun backspace() {
        _state.update {
            val s = it.cash.toString()
            val n = if (s.length <= 1) 0L else s.dropLast(1).toLongOrNull() ?: 0L
            it.copy(cash = n, error = null)
        }
    }

    fun clearCash() = _state.update { it.copy(cash = 0, error = null) }

    fun setAmount(amount: Long) = _state.update { it.copy(cash = amount, error = null) }

    fun setExact() = _state.update { it.copy(cash = it.total.toLong(), error = null) }

    fun confirm() {
        val s = _state.value
        if (!s.sufficient) {
            _state.update { it.copy(error = "Uang diterima kurang dari total") }
            return
        }
        val lines = cartManager.lines.value
        if (lines.isEmpty()) {
            _state.update { it.copy(error = "Keranjang kosong") }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val request = TransactionRequest(
                items = lines.map { TransactionItemRequest(it.productId, it.quantity) },
                discountAmount = cartManager.discount.value,
                cashReceived = s.cash.toDouble()
            )
            when (val res = transactionRepository.create(idempotencyKey, request)) {
                is ApiResult.Success -> {
                    cartManager.clear()
                    _state.update { it.copy(submitting = false) }
                    _success.send(res.data.id)
                }
                is ApiResult.Error -> _state.update { it.copy(submitting = false, error = res.message) }
            }
        }
    }
}
