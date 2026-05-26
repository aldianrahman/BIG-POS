package com.berdikariintigemilang.pos.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TransactionHistoryUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<TransactionDto> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isAdmin: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionHistoryUiState())
    val state: StateFlow<TransactionHistoryUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(isAdmin = authRepository.userFlow.first()?.isAdmin ?: false) }
        }
        load(reset = true)
    }

    private fun fromIso() = LocalDate.now().minusDays(30).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    private fun toIso() = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun load(reset: Boolean) {
        val nextPage = if (reset) 0 else _state.value.page + 1
        _state.update { it.copy(loading = reset, loadingMore = !reset, error = null) }
        viewModelScope.launch {
            when (val res = transactionRepository.list(fromIso(), toIso(), nextPage)) {
                is ApiResult.Success -> {
                    val pageData = res.data
                    _state.update {
                        it.copy(
                            items = if (reset) pageData.content else it.items + pageData.content,
                            page = pageData.number,
                            hasMore = pageData.number < pageData.totalPages - 1,
                            loading = false,
                            loadingMore = false
                        )
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, loadingMore = false, error = res.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.loading && !s.loadingMore && s.hasMore) load(reset = false)
    }

    fun void(id: Long, reason: String) {
        viewModelScope.launch {
            when (val res = transactionRepository.void(id, reason)) {
                is ApiResult.Success -> { _messages.send("Transaksi di-void, stok dikembalikan"); load(reset = true) }
                is ApiResult.Error -> _messages.send(res.message)
            }
        }
    }
}
