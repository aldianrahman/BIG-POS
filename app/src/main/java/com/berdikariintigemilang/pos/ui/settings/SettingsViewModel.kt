package com.berdikariintigemilang.pos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.datastore.PrinterStore
import com.berdikariintigemilang.pos.core.datastore.SavedPrinter
import com.berdikariintigemilang.pos.core.datastore.SessionUser
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.core.printer.PrinterException
import com.berdikariintigemilang.pos.core.printer.PrinterManager
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.CatalogCacheRepository
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import com.berdikariintigemilang.pos.data.sync.SyncManager
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
    val printing: Boolean = false,
    // Sinkronisasi
    val pendingCount: Int = 0,
    val isOnline: Boolean = false,
    val syncing: Boolean = false,
    val refreshingCatalog: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shiftRepository: ShiftRepository,
    private val printerStore: PrinterStore,
    private val printerManager: PrinterManager,
    private val offlineStore: OfflineTransactionStore,
    private val syncManager: SyncManager,
    private val catalogCache: CatalogCacheRepository,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(isOnline = connectivity.isOnlineNow()))
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
        viewModelScope.launch {
            offlineStore.observeUnsyncedCount().collect { c -> _state.update { it.copy(pendingCount = c) } }
        }
        viewModelScope.launch {
            connectivity.isOnline.collect { on -> _state.update { it.copy(isOnline = on) } }
        }
        viewModelScope.launch {
            syncManager.syncing.collect { s -> _state.update { it.copy(syncing = s) } }
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

    /** Kirim antrian transaksi sekarang & beri umpan balik. */
    fun syncNow() {
        if (!connectivity.isOnlineNow()) {
            viewModelScope.launch { send("Tidak ada koneksi internet") }
            return
        }
        viewModelScope.launch {
            val o = syncManager.syncPending()
            val msg = if (o.synced == 0 && o.conflict == 0 && o.retriable == 0) {
                "Tidak ada transaksi untuk dikirim"
            } else {
                buildString {
                    append("Terkirim ${o.synced}")
                    if (o.conflict > 0) append(", konflik ${o.conflict}")
                    if (o.retriable > 0) append(", gagal ${o.retriable}")
                }
            }
            send(msg)
        }
    }

    /** Tarik ulang katalog & stok dari server (persiapan offline). */
    fun refreshCatalog() {
        if (!connectivity.isOnlineNow()) {
            viewModelScope.launch { send("Tidak ada koneksi internet") }
            return
        }
        _state.update { it.copy(refreshingCatalog = true) }
        viewModelScope.launch {
            val r = catalogCache.refreshAll()
            _state.update { it.copy(refreshingCatalog = false) }
            send(if (r is ApiResult.Success) "Katalog & stok diperbarui" else (r as ApiResult.Error).message)
        }
    }

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
