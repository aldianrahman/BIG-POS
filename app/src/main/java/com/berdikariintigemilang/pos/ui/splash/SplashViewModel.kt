package com.berdikariintigemilang.pos.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplashDestination { LOGIN, SHIFT_OPEN, MAIN }

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination

    init {
        decide()
    }

    private fun decide() {
        viewModelScope.launch {
            val token = authRepository.tokenFlow.first()
            if (token.isNullOrBlank()) {
                _destination.value = SplashDestination.LOGIN
                return@launch
            }
            if (!authRepository.validateSession()) {
                authRepository.logout()
                _destination.value = SplashDestination.LOGIN
                return@launch
            }
            val shift = shiftRepository.current()
            _destination.value = if (shift is ApiResult.Success && shift.data != null)
                SplashDestination.MAIN else SplashDestination.SHIFT_OPEN
        }
    }
}
