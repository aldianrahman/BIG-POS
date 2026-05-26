package com.berdikariintigemilang.pos.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.datastore.PrinterStore
import com.berdikariintigemilang.pos.core.datastore.SavedPrinter
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.printer.PrinterException
import com.berdikariintigemilang.pos.core.printer.PrinterManager
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptUiState(
    val loading: Boolean = true,
    val trxNo: String = "",
    val content: String = "",
    val error: String? = null,
    val printing: Boolean = false,
    val hasPrinter: Boolean = false
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val printerStore: PrinterStore,
    private val printerManager: PrinterManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val trxId: Long = savedStateHandle.get<String>("trxId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var savedPrinter: SavedPrinter? = null

    init {
        loadReceipt()
        viewModelScope.launch {
            printerStore.printerFlow.collect { p ->
                savedPrinter = p
                _state.update { it.copy(hasPrinter = p != null) }
            }
        }
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

    fun print() {
        val printer = savedPrinter
        if (printer == null) {
            viewModelScope.launch { _messages.send("Printer belum dipilih. Buka tab Pengaturan untuk memilih printer.") }
            return
        }
        if (_state.value.content.isBlank()) {
            viewModelScope.launch { _messages.send("Struk belum siap") }
            return
        }
        _state.update { it.copy(printing = true) }
        viewModelScope.launch {
            try {
                printerManager.printReceipt(printer.address, _state.value.content)
                _messages.send("Struk tercetak")
            } catch (e: PrinterException) {
                _messages.send(e.message ?: "Gagal mencetak")
            } catch (e: Exception) {
                _messages.send("Gagal mencetak: ${e.message}")
            } finally {
                _state.update { it.copy(printing = false) }
            }
        }
    }
}
