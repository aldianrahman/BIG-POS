package com.berdikariintigemilang.pos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.datastore.PrinterStore
import com.berdikariintigemilang.pos.core.datastore.SavedPrinter
import com.berdikariintigemilang.pos.core.datastore.SessionUser
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.printer.PrinterException
import com.berdikariintigemilang.pos.core.printer.PrinterManager
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: SessionUser? = null,
    val currentShiftId: Long? = null,
    val loadingShift: Boolean = true,
    val savedPrinter: SavedPrinter? = null,
    val pairedDevices: List<SavedPrinter> = emptyList(),
    val loadingDevices: Boolean = false,
    val printing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val printerStore: PrinterStore,
    private val printerManager: PrinterManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.userFlow.collect { user -> _state.update { it.copy(user = user) } }
        }
        viewModelScope.launch {
            printerStore.printerFlow.collect { p -> _state.update { it.copy(savedPrinter = p) } }
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

    /** Dipanggil setelah izin Bluetooth diberikan. */
    fun loadPairedDevices() {
        _state.update { it.copy(loadingDevices = true) }
        viewModelScope.launch {
            try {
                val devices = printerManager.pairedPrinters()
                _state.update { it.copy(pairedDevices = devices, loadingDevices = false) }
                if (devices.isEmpty()) {
                    send("Belum ada perangkat ter-pair. Pair printer dulu di Setting Bluetooth Android.")
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingDevices = false) }
                send(e.message ?: "Gagal membaca perangkat Bluetooth")
            }
        }
    }

    fun selectPrinter(printer: SavedPrinter) {
        viewModelScope.launch {
            printerStore.save(printer)
            send("Printer \"${printer.name}\" disimpan")
        }
    }

    fun testPrint() {
        val printer = _state.value.savedPrinter
        if (printer == null) {
            viewModelScope.launch { send("Pilih printer dulu") }
            return
        }
        _state.update { it.copy(printing = true) }
        viewModelScope.launch {
            try {
                printerManager.testPrint(printer.address)
                send("Test print terkirim")
            } catch (e: PrinterException) {
                send(e.message ?: "Gagal test print")
            } catch (e: Exception) {
                send("Gagal test print: ${e.message}")
            } finally {
                _state.update { it.copy(printing = false) }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    private suspend fun send(msg: String) = _messages.send(msg)
}
