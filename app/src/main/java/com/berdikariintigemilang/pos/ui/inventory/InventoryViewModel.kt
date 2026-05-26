package com.berdikariintigemilang.pos.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val loading: Boolean = true,
    val search: String = "",
    val lowStockOnly: Boolean = false,
    val items: List<StockDto> = emptyList(),
    val isAdmin: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(isAdmin = authRepository.userFlow.first()?.isAdmin ?: false) }
        }
    }

    fun onSearchChange(q: String) {
        _state.update { it.copy(search = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); load() }
    }

    fun toggleLowStock(value: Boolean) {
        _state.update { it.copy(lowStockOnly = value) }
        load()
    }

    fun load() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = inventoryRepository.stocks(s.search, s.lowStockOnly)) {
                is ApiResult.Success -> _state.update { it.copy(items = res.data, loading = false) }
                is ApiResult.Error -> _state.update { it.copy(error = res.message, loading = false) }
            }
        }
    }
}
