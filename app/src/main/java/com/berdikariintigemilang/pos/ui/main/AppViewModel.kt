package com.berdikariintigemilang.pos.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.AuthEventBus
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    authEventBus: AuthEventBus,
    private val authRepository: AuthRepository
) : ViewModel() {

    /** Dipancarkan saat sesi berakhir (401) sehingga UI pindah ke Login. */
    val loggedOut: SharedFlow<Unit> = authEventBus.loggedOut

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
