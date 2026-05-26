package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductSearchState(
    val query: String = "",
    val items: List<ProductDto> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProductSearchViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val cartManager: CartManager
) : ViewModel() {

    private val _state = MutableStateFlow(ProductSearchState())
    val state: StateFlow<ProductSearchState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load(reset = true)
        }
    }

    fun load(reset: Boolean) {
        val current = _state.value
        val nextPage = if (reset) 0 else current.page + 1
        _state.update { it.copy(loading = reset, loadingMore = !reset, error = null) }
        viewModelScope.launch {
            when (val res = productRepository.search(current.query.ifBlank { null }, nextPage)) {
                is ApiResult.Success -> {
                    val pageData = res.data
                    _state.update {
                        it.copy(
                            items = if (reset) pageData.content else it.items + pageData.content,
                            page = pageData.page,
                            hasMore = pageData.page < pageData.totalPages - 1,
                            loading = false,
                            loadingMore = false
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, loadingMore = false, error = res.message)
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.loading && !s.loadingMore && s.hasMore) load(reset = false)
    }

    fun addToCart(product: ProductDto, quantity: Int) {
        val ok = cartManager.addProduct(product, quantity)
        viewModelScope.launch {
            _messages.send(if (ok) "${product.name} +$quantity ditambahkan" else "Stok ${product.name} tidak mencukupi")
        }
    }
}
