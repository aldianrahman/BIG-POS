package com.berdikariintigemilang.pos.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftOpenUiState(
    val checking: Boolean = true,
    val submitting: Boolean = false,
    val cashierName: String = "",
    val error: String? = null
)

@HiltViewModel
class ShiftOpenViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ShiftOpenUiState())
    val state: StateFlow<ShiftOpenUiState> = _state

    private val _goToMain = Channel<Unit>(Channel.BUFFERED)
    val goToMain = _goToMain.receiveAsFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.userFlow.first()
            _state.update { it.copy(cashierName = user?.fullName ?: "") }
            when (val current = shiftRepository.current()) {
                is ApiResult.Success -> {
                    if (current.data != null) _goToMain.send(Unit)
                    else _state.update { it.copy(checking = false) }
                }
                is ApiResult.Error -> _state.update { it.copy(checking = false) }
            }
        }
    }

    fun openShift(openingCash: Double) {
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val res = shiftRepository.open(openingCash)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    _goToMain.send(Unit)
                }
                is ApiResult.Error -> _state.update { it.copy(submitting = false, error = res.message) }
            }
        }
    }
}
