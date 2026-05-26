package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptUiState(
    val loading: Boolean = true,
    val trxNo: String = "",
    val content: String = "",
    val error: String? = null
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val trxId: Long = savedStateHandle.get<String>("trxId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = _state

    init {
        loadReceipt()
    }

    fun loadReceipt() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = transactionRepository.receipt(trxId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, trxNo = res.data.trxNo, content = res.data.content)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }
}
