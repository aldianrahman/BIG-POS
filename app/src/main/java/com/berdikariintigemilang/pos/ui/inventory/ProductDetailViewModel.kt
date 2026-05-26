package com.berdikariintigemilang.pos.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.data.remote.ProductRequest
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.InventoryRepository
import com.berdikariintigemilang.pos.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val loading: Boolean = true,
    val product: ProductDto? = null,
    val isAdmin: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<String>("productId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ProductDetailUiState())
    val state: StateFlow<ProductDetailUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val admin = authRepository.userFlow.first()?.isAdmin ?: false
            _state.update { it.copy(isAdmin = admin) }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = productRepository.getById(productId)) {
                is ApiResult.Success -> _state.update { it.copy(product = res.data, loading = false) }
                is ApiResult.Error -> _state.update { it.copy(error = res.message, loading = false) }
            }
        }
    }

    fun adjustStock(quantity: Int, notes: String) {
        if (quantity == 0) { send("Jumlah tidak boleh 0"); return }
        if (notes.isBlank()) { send("Catatan wajib diisi"); return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val res = inventoryRepository.adjust(productId, quantity, notes)) {
                is ApiResult.Success -> { _messages.send("Stok disesuaikan"); load() }
                is ApiResult.Error -> _messages.send(res.message)
            }
            _state.update { it.copy(saving = false) }
        }
    }

    fun updatePrice(purchasePrice: Double, sellingPrice: Double, minStock: Int) {
        val p = _state.value.product ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val request = ProductRequest(
                sku = p.sku,
                barcode = p.barcode,
                name = p.name,
                description = p.description,
                categoryId = p.categoryId,
                brand = p.brand,
                unit = p.unit,
                purchasePrice = purchasePrice,
                sellingPrice = sellingPrice,
                minStock = minStock
            )
            when (val res = productRepository.update(productId, request)) {
                is ApiResult.Success -> { _messages.send("Produk diperbarui"); load() }
                is ApiResult.Error -> _messages.send(res.message)
            }
            _state.update { it.copy(saving = false) }
        }
    }

    private fun send(msg: String) {
        viewModelScope.launch { _messages.send(msg) }
    }
}
