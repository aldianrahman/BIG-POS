package com.berdikariintigemilang.pos.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.BuildConfig
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.repository.AppVersionRepository
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.VersionCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Info versi saat aplikasi tertahan karena usang. */
data class UpdateInfo(val current: String, val required: String)

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Versi aplikasi saat ini (untuk ditampilkan di layar login). */
    val appVersion: String = BuildConfig.VERSION_NAME,
    val checkingVersion: Boolean = false,
    /** Jika != null, login ditahan karena versi aplikasi usang. */
    val updateRequired: UpdateInfo? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appVersionRepository: AppVersionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loginSuccess = Channel<Unit>(Channel.BUFFERED)
    val loginSuccess = _loginSuccess.receiveAsFlow()

    /**
     * Isi otomatis username & password dari login terakhir yang berhasil
     * (membantu kasir yang lupa kredensialnya). Dipanggil saat layar login
     * tampil; tidak menimpa bila user sudah mulai mengetik.
     */
    fun prefillFromSaved() {
        viewModelScope.launch {
            authRepository.savedCredentials()?.let { c ->
                _state.update { cur ->
                    if (cur.username.isBlank() && cur.password.isBlank())
                        cur.copy(username = c.username, password = c.password)
                    else cur
                }
            }
        }
    }

    fun onUsernameChange(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }

    /**
     * Cek versi aplikasi terhadap versi minimum di server. Bila usang, login
     * ditahan ([LoginUiState.updateRequired] terisi). Fail-open: bila server tak
     * terjangkau, login tetap diizinkan (penting untuk mode offline).
     */
    fun checkVersion() {
        _state.update { it.copy(checkingVersion = true) }
        viewModelScope.launch {
            val info = when (val r = appVersionRepository.check()) {
                is VersionCheck.Outdated -> UpdateInfo(current = r.current, required = r.required)
                VersionCheck.Ok -> null
            }
            _state.update { it.copy(checkingVersion = false, updateRequired = info) }
        }
    }

    fun login() {
        // Tahan login bila versi aplikasi usang.
        if (_state.value.updateRequired != null) return
        val s = _state.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Username dan password wajib diisi") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = authRepository.login(s.username, s.password)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    _loginSuccess.send(Unit)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }
}
