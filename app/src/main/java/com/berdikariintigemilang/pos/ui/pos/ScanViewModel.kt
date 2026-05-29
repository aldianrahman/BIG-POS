package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanState(
    val loading: Boolean = false,
    val pendingProduct: ProductDto? = null,
    val notFoundCode: String? = null,
    val error: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val cartManager: CartManager
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    // Event ketika item benar-benar masuk keranjang (untuk beep + getar).
    private val _added = Channel<Unit>(Channel.BUFFERED)
    val added = _added.receiveAsFlow()

    // Pesan singkat untuk snackbar.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    @Volatile
    private var processing = false

    fun onBarcode(code: String) {
        // Abaikan bila sedang proses / dialog produk atau "tidak ditemukan" terbuka.
        if (processing || _state.value.pendingProduct != null || _state.value.notFoundCode != null) return
        processing = true
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = productRepository.byBarcode(code)) {
                is ApiResult.Success -> {
                    // Tampilkan dialog konfirmasi jumlah (belum ditambah ke keranjang).
                    _state.update { it.copy(loading = false, pendingProduct = res.data) }
                }
                is ApiResult.Error -> {
                    if (res.httpStatus == 404) {
                        _state.update { it.copy(loading = false, notFoundCode = "Produk barcode $code tidak ditemukan") }
                    } else {
                        _state.update { it.copy(loading = false, error = res.message) }
                        processing = false // izinkan scan ulang
                    }
                }
            }
        }
    }

    /** Konfirmasi tambah dari dialog. Tetap di layar scan agar bisa lanjut scan. */
    fun confirmAdd(quantity: Int) {
        val product = _state.value.pendingProduct ?: return
        val ok = cartManager.addProduct(product, quantity)
        _state.update { it.copy(pendingProduct = null) }
        processing = false
        viewModelScope.launch {
            if (ok) {
                _added.send(Unit)
                _messages.send("${product.name} +$quantity ditambahkan")
            } else {
                _messages.send("Stok ${product.name} tidak mencukupi")
            }
        }
    }

    fun cancelPending() {
        _state.update { it.copy(pendingProduct = null) }
        processing = false
    }

    /** Tutup dialog "tidak ditemukan"/error & izinkan scan lagi. */
    fun dismissDialog() {
        _state.update { it.copy(notFoundCode = null, error = null) }
        processing = false
    }
}
