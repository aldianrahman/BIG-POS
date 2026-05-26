package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.cart.CartManager
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

    // Event nama produk yang berhasil ditambahkan (untuk vibrate + kembali ke POS).
    private val _added = Channel<String>(Channel.BUFFERED)
    val added = _added.receiveAsFlow()

    @Volatile
    private var processing = false

    fun onBarcode(code: String) {
        if (processing || _state.value.notFoundCode != null) return
        processing = true
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = productRepository.byBarcode(code)) {
                is ApiResult.Success -> {
                    val ok = cartManager.addProduct(res.data)
                    _state.update { it.copy(loading = false) }
                    if (ok) {
                        _added.send(res.data.name)
                    } else {
                        _state.update { it.copy(notFoundCode = "Stok ${res.data.name} habis") }
                    }
                }
                is ApiResult.Error -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            notFoundCode = if (res.httpStatus == 404) "Produk barcode $code tidak ditemukan" else null,
                            error = if (res.httpStatus == 404) null else res.message
                        )
                    }
                    // error jaringan: izinkan scan ulang
                    if (res.httpStatus != 404) processing = false
                }
            }
        }
    }

    /** Tutup dialog & izinkan scan lagi. */
    fun dismissDialog() {
        _state.update { it.copy(notFoundCode = null, error = null) }
        processing = false
    }
}
