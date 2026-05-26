package com.berdikariintigemilang.pos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.datastore.SessionUser
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: SessionUser? = null,
    val currentShiftId: Long? = null,
    val loadingShift: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            authRepository.userFlow.collect { user -> _state.update { it.copy(user = user) } }
        }
        refreshShift()
    }

    fun refreshShift() {
        _state.update { it.copy(loadingShift = true) }
        viewModelScope.launch {
            val id = when (val res = shiftRepository.current()) {
                is ApiResult.Success -> res.data?.id
                is ApiResult.Error -> null
            }
            _state.update { it.copy(currentShiftId = id, loadingShift = false) }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
