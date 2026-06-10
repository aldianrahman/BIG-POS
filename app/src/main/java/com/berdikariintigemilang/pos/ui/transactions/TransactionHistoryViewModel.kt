package com.berdikariintigemilang.pos.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.local.PendingTransactionEntity
import com.berdikariintigemilang.pos.data.local.SyncStatus
import com.berdikariintigemilang.pos.data.remote.RefundItemRequest
import com.berdikariintigemilang.pos.data.remote.RefundRequest
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.repository.AuthRepository
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Jenis status untuk pewarnaan chip. */
enum class TrxStatusKind { SYNCED, PENDING, CONFLICT, DONE, VOID, REFUND }

/** Baris riwayat terpadu: bisa dari transaksi lokal HP atau dari server. */
data class TrxRow(
    val ref: String,          // clientTxnId (lokal) atau id server (string) untuk navigasi struk
    val trxNo: String,
    val dateText: String,
    val cashierName: String?,
    val totalAmount: Double,
    val statusLabel: String,
    val statusKind: TrxStatusKind,
    val serverId: Long?,      // untuk void/refund & ambil struk resmi server
    val voided: Boolean,
    val paymentMethod: String = "CASH"
)

data class TransactionHistoryUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<TrxRow> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isAdmin: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val offlineStore: OfflineTransactionStore,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionHistoryUiState())
    val state: StateFlow<TransactionHistoryUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private var localRows: List<TrxRow> = emptyList()
    private var serverRows: List<TrxRow> = emptyList()

    init {
        viewModelScope.launch {
            _state.update { it.copy(isAdmin = authRepository.userFlow.first()?.isAdmin ?: false) }
        }
        load(reset = true)
    }

    private fun fromIso() = LocalDate.now().minusDays(30).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    private fun toIso() = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun load(reset: Boolean) {
        val online = connectivity.isOnlineNow()
        val nextPage = if (reset) 0 else _state.value.page + 1
        _state.update { it.copy(loading = reset, loadingMore = !reset, error = null, offline = !online) }
        viewModelScope.launch {
            // Transaksi HP ini selalu dimuat (tersedia walau offline).
            if (reset) localRows = offlineStore.all().map { it.toRow() }

            if (!online) {
                _state.update {
                    it.copy(items = buildItems(online = false), loading = false, loadingMore = false, hasMore = false)
                }
                return@launch
            }

            when (val res = transactionRepository.list(fromIso(), toIso(), nextPage)) {
                is ApiResult.Success -> {
                    val rows = res.data.content.map { it.toRow() }
                    serverRows = if (reset) rows else serverRows + rows
                    _state.update {
                        it.copy(
                            items = buildItems(online = true),
                            page = res.data.number,
                            hasMore = res.data.number < res.data.totalPages - 1,
                            loading = false,
                            loadingMore = false
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    // Online tapi gagal: tetap tampilkan transaksi lokal.
                    it.copy(
                        items = buildItems(online = false),
                        loading = false,
                        loadingMore = false,
                        hasMore = false,
                        error = if (localRows.isEmpty()) res.message else null
                    )
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.loading && !s.loadingMore && s.hasMore) load(reset = false)
    }

    fun void(serverId: Long, reason: String) {
        viewModelScope.launch {
            when (val res = transactionRepository.void(serverId, reason)) {
                is ApiResult.Success -> { _messages.send("Transaksi di-void, stok dikembalikan"); load(reset = true) }
                is ApiResult.Error -> _messages.send(res.message)
            }
        }
    }

    /** Transaksi yang sedang disiapkan untuk refund (memuat item & sisa qty). */
    private val _refundTarget = MutableStateFlow<TransactionDto?>(null)
    val refundTarget: StateFlow<TransactionDto?> = _refundTarget

    fun startRefund(serverId: Long) {
        viewModelScope.launch {
            when (val res = transactionRepository.getById(serverId)) {
                is ApiResult.Success -> _refundTarget.value = res.data
                is ApiResult.Error -> _messages.send(res.message)
            }
        }
    }

    fun cancelRefund() { _refundTarget.value = null }

    fun refund(serverId: Long, full: Boolean, reason: String, items: List<RefundItemRequest>) {
        viewModelScope.launch {
            when (val res = transactionRepository.refund(serverId, RefundRequest(full = full, reason = reason, items = items))) {
                is ApiResult.Success -> {
                    _refundTarget.value = null
                    _messages.send("Refund berhasil, stok dikembalikan")
                    load(reset = true)
                }
                is ApiResult.Error -> _messages.send(res.message)
            }
        }
    }

    /**
     * Online: transaksi lokal yang BELUM tersinkron (disematkan di atas) + daftar
     * server. Yang sudah tersinkron sudah ada di daftar server (tak digandakan).
     * Offline: semua transaksi lokal HP ini.
     */
    private fun buildItems(online: Boolean): List<TrxRow> {
        val local = if (online) {
            localRows.filter { it.statusKind == TrxStatusKind.PENDING || it.statusKind == TrxStatusKind.CONFLICT }
        } else {
            localRows
        }
        return local + serverRows
    }

    private fun PendingTransactionEntity.toRow(): TrxRow {
        val kind = when (status) {
            SyncStatus.SYNCED.name -> TrxStatusKind.SYNCED
            SyncStatus.CONFLICT.name -> TrxStatusKind.CONFLICT
            else -> TrxStatusKind.PENDING
        }
        val label = when (kind) {
            TrxStatusKind.SYNCED -> "Terkirim"
            TrxStatusKind.CONFLICT -> "Konflik"
            else -> "Tertunda"
        }
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault()).format(dateFmt)
        return TrxRow(
            ref = clientTxnId,
            trxNo = serverTrxNo ?: offlineTrxNo,
            dateText = date,
            cashierName = cashierName,
            totalAmount = totalAmount,
            statusLabel = label,
            statusKind = kind,
            serverId = serverId,
            voided = false,
            paymentMethod = paymentMethod
        )
    }

    private fun TransactionDto.toRow(): TrxRow {
        val kind = when (status) {
            "VOIDED" -> TrxStatusKind.VOID
            "REFUNDED" -> TrxStatusKind.REFUND
            else -> TrxStatusKind.DONE
        }
        val label = when (kind) {
            TrxStatusKind.VOID -> "Void"
            TrxStatusKind.REFUND -> "Refund"
            else -> if (refundedAmount > 0) "Refund sebagian" else "Selesai"
        }
        return TrxRow(
            ref = id.toString(),
            trxNo = trxNo,
            dateText = Formatters.displayDateTime(createdAt),
            cashierName = cashierName,
            totalAmount = totalAmount,
            statusLabel = label,
            statusKind = kind,
            serverId = id,
            voided = kind == TrxStatusKind.VOID,
            paymentMethod = paymentMethod
        )
    }
}
