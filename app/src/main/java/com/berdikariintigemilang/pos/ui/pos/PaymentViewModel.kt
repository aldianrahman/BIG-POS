package com.berdikariintigemilang.pos.ui.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.datastore.SessionStore
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.pricing.LocalPricingCalculator
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.berdikariintigemilang.pos.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentUiState(
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val bundleDiscount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val taxInclusive: Boolean = true,
    val total: Double = 0.0,
    val cash: Long = 0,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val change: Long get() = (cash - total).toLong().coerceAtLeast(0)
    val sufficient: Boolean get() = cash >= total
}

/**
 * Pembayaran OFFLINE-FIRST: total (bundle + PPN) dihitung lokal dari katalog
 * yang di-cache, dan transaksi disimpan ke antrian lokal terlebih dahulu
 * (tidak langsung ke server). Pengiriman ke server dilakukan oleh WorkManager
 * saat ada koneksi, memakai idempotency key milik transaksi (anti-dobel).
 */
@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val cartManager: CartManager,
    private val offlineStore: OfflineTransactionStore,
    private val pricing: LocalPricingCalculator,
    private val sessionStore: SessionStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PaymentUiState> = _state

    /** Emit clientTxnId (UUID) transaksi tersimpan agar layar struk bisa memuatnya. */
    private val _success = Channel<String>(Channel.BUFFERED)
    val success = _success.receiveAsFlow()

    private var cashierName: String? = null

    init {
        viewModelScope.launch { cashierName = sessionStore.userFlow.first()?.fullName }
        // Hitung potongan bundle + PPN secara lokal agar total benar tanpa sinyal.
        viewModelScope.launch {
            val lines = cartManager.lines.value
            val p = pricing.price(lines, cartManager.discount.value)
            _state.update {
                it.copy(
                    subtotal = p.subtotal,
                    discount = p.discount,
                    bundleDiscount = p.bundleDiscount,
                    taxAmount = p.taxAmount,
                    taxInclusive = p.taxInclusive,
                    total = p.total
                )
            }
        }
    }

    private fun initialState(): PaymentUiState {
        val subtotal = cartManager.lines.value.sumOf { it.lineSubtotal }
        val discount = cartManager.discount.value
        return PaymentUiState(
            subtotal = subtotal,
            discount = discount,
            total = (subtotal - discount).coerceAtLeast(0.0)
        )
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
        val lines = cartManager.lines.value
        if (lines.isEmpty()) {
            _state.update { it.copy(error = "Keranjang kosong") }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                // Hitung ulang harga otoritatif (sumber yang sama dipakai saat menyimpan).
                val priced = pricing.price(lines, cartManager.discount.value)
                val cash = _state.value.cash.toDouble()
                if (cash < priced.total) {
                    _state.update { it.copy(submitting = false, error = "Uang diterima kurang dari total") }
                    return@launch
                }
                val entity = offlineStore.enqueue(
                    lines = lines,
                    discount = cartManager.discount.value,
                    cashReceived = cash,
                    notes = null,
                    cashierName = cashierName
                )
                cartManager.clear()
                // Coba kirim segera bila ada koneksi; bila tidak, WorkManager menunggu.
                SyncScheduler.syncNow(appContext)
                _state.update { it.copy(submitting = false) }
                _success.send(entity.clientTxnId)
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, error = e.message ?: "Gagal menyimpan transaksi") }
            }
        }
    }
}
