package com.berdikariintigemilang.pos.ui.shift

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.ShiftDto
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftCloseUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val shift: ShiftDto? = null,
    val error: String? = null
)

@HiltViewModel
class ShiftCloseViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val shiftId: Long = savedStateHandle.get<String>("shiftId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ShiftCloseUiState())
    val state: StateFlow<ShiftCloseUiState> = _state

    private val _closed = Channel<Long>(Channel.BUFFERED)
    val closed = _closed.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val res = shiftRepository.get(shiftId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, shift = res.data) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun close(closingCash: Double, notes: String?) {
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val res = shiftRepository.close(shiftId, closingCash, notes)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    _closed.send(shiftId)
                }
                is ApiResult.Error -> _state.update { it.copy(submitting = false, error = res.message) }
            }
        }
    }
}
