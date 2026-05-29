package com.berdikariintigemilang.pos.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.CategoryDto
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.data.remote.ProductRequest
import com.berdikariintigemilang.pos.data.repository.CategoryRepository
import com.berdikariintigemilang.pos.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductEditUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val categories: List<CategoryDto> = emptyList(),
    val product: ProductDto? = null,
    val saving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<String>("productId")?.toLongOrNull() ?: 0L
    val isEdit: Boolean = productId != 0L

    private val _state = MutableStateFlow(ProductEditUiState(isEdit = isEdit))
    val state: StateFlow<ProductEditUiState> = _state

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            // kategori (untuk dropdown) - hanya sub-kategori (tempat produk)
            val cats = when (val res = categoryRepository.listFlat()) {
                is ApiResult.Success -> res.data.filter { it.parentId != null }.ifEmpty { res.data }
                is ApiResult.Error -> emptyList()
            }
            var product: ProductDto? = null
            if (isEdit) {
                when (val res = productRepository.getById(productId)) {
                    is ApiResult.Success -> product = res.data
                    is ApiResult.Error -> _state.update { it.copy(error = res.message) }
                }
            }
            _state.update { it.copy(loading = false, categories = cats, product = product) }
        }
    }

    fun save(request: ProductRequest) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val res = if (isEdit) productRepository.update(productId, request) else productRepository.create(request)
            when (res) {
                is ApiResult.Success -> { _messages.send(if (isEdit) "Produk diperbarui" else "Produk dibuat"); _saved.send(Unit) }
                is ApiResult.Error -> { _state.update { it.copy(saving = false) }; _messages.send(res.message) }
            }
        }
    }
}
