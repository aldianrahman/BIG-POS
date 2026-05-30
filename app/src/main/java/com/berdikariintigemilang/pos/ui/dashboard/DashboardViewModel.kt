package com.berdikariintigemilang.pos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.data.local.PendingTransactionEntity
import com.berdikariintigemilang.pos.data.local.SyncStatus
import com.berdikariintigemilang.pos.data.remote.DashboardSummaryDto
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.remote.TopProductDto
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.repository.DashboardRepository
import com.berdikariintigemilang.pos.data.repository.OfflineTransactionStore
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Satu titik grafik: nilai transaksi + waktu (label sumbu-x). */
data class ChartPoint(val label: String, val value: Double)

data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val summary: DashboardSummaryDto? = null,
    val topProducts: List<TopProductDto> = emptyList(),
    val lowStock: List<StockDto> = emptyList(),
    val todayTransactions: List<ChartPoint> = emptyList(),
    val offline: Boolean = false,
    val error: String? = null
)

/**
 * Dashboard yang menggabungkan data server dengan transaksi lokal HP ini,
 * sehingga angka "hari ini" langsung mencerminkan penjualan offline (yang
 * belum tersinkron) dan diperbarui otomatis tiap ada transaksi baru. Saat
 * koneksi kembali, data server ditarik ulang.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val transactionRepository: TransactionRepository,
    private val offlineStore: OfflineTransactionStore,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Data server terakhir + transaksi lokal terkini.
    private var serverSummary: DashboardSummaryDto? = null
    private var serverPoints: List<ChartPoint> = emptyList()
    private var serverFresh = false
    private var localTxns: List<PendingTransactionEntity> = emptyList()
    private var online = connectivity.isOnlineNow()

    init {
        // Perubahan transaksi lokal (mis. penjualan offline) langsung tercermin.
        viewModelScope.launch {
            offlineStore.observeAll().collect { txns -> localTxns = txns; rebuild() }
        }
        // Koneksi kembali -> tarik ulang data server otomatis.
        viewModelScope.launch {
            connectivity.isOnline.collect { on ->
                val was = online
                online = on
                _state.update { it.copy(offline = !on) }
                if (on && !was) load() else rebuild()
            }
        }
        load()
    }

    fun load() = fetch(isRefresh = false)

    /** Muat ulang via tarik-ke-bawah. */
    fun refresh() = fetch(isRefresh = true)

    private fun fetch(isRefresh: Boolean) {
        if (!connectivity.isOnlineNow()) {
            online = false
            _state.update { it.copy(loading = false, refreshing = false, offline = true) }
            rebuild()
            return
        }
        val date = LocalDate.now().toString()
        _state.update {
            if (isRefresh) it.copy(refreshing = true, error = null) else it.copy(loading = true, error = null)
        }
        viewModelScope.launch {
            when (val summary = dashboardRepository.summary(date)) {
                is ApiResult.Success -> { serverSummary = summary.data; serverFresh = true }
                is ApiResult.Error -> _state.update { it.copy(error = summary.message) }
            }
            (dashboardRepository.topProducts(date) as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(topProducts = res.data) }
            }
            (dashboardRepository.lowStock() as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(lowStock = res.data) }
            }
            serverPoints = loadServerTodayPoints()
            online = true
            _state.update { it.copy(loading = false, refreshing = false, offline = false) }
            rebuild()
        }
    }

    /** Titik grafik transaksi hari ini dari server (sudah tersinkron). */
    private suspend fun loadServerTodayPoints(): List<ChartPoint> {
        val from = LocalDate.now().atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val all = mutableListOf<TransactionDto>()
        var page = 0
        while (page < 20) {
            val res = transactionRepository.list(from, to, page, 100)
            if (res !is ApiResult.Success) break
            all += res.data.content
            if (page >= res.data.totalPages - 1) break
            page++
        }
        return all
            .filter { !it.status.contains("VOID", ignoreCase = true) }
            .sortedBy { it.id }
            .map { trx ->
                val time = trx.createdAt?.let { iso ->
                    runCatching { LocalDateTime.parse(iso).format(timeFmt) }.getOrNull()
                } ?: ""
                ChartPoint(label = time, value = trx.totalAmount)
            }
    }

    /**
     * Gabungkan data server dengan transaksi lokal HARI INI:
     * - Online & data server ada: tambahkan transaksi lokal yang BELUM tersinkron
     *   (yang sudah tersinkron sudah masuk angka server -> tak digandakan).
     * - Offline: pakai seluruh transaksi lokal hari ini sebagai sumber angka.
     */
    private fun rebuild() {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val localToday = localTxns.filter { it.createdAt >= todayStart }
        val serverAvailable = online && serverFresh
        val overlay = if (serverAvailable) localToday.filter { it.status != SyncStatus.SYNCED.name } else localToday

        val overlayPoints = overlay.sortedBy { it.createdAt }.map {
            ChartPoint(
                label = LocalDateTime.ofInstant(Instant.ofEpochMilli(it.createdAt), ZoneId.systemDefault()).format(timeFmt),
                value = it.totalAmount
            )
        }

        val baseSales = if (serverAvailable) serverSummary?.totalSales ?: 0.0 else 0.0
        val baseCount = if (serverAvailable) serverSummary?.totalTransactions ?: 0 else 0
        val baseProfit = if (serverAvailable) serverSummary?.totalProfit ?: 0.0 else 0.0

        val totalSales = baseSales + overlay.sumOf { it.totalAmount }
        val totalCount = baseCount + overlay.size
        val avg = if (totalCount > 0) totalSales / totalCount else 0.0

        val merged = DashboardSummaryDto(
            date = LocalDate.now().toString(),
            totalSales = totalSales,
            totalTransactions = totalCount,
            totalProfit = baseProfit,
            avgTransactionValue = avg
        )
        val basePoints = if (serverAvailable) serverPoints else emptyList()
        _state.update { it.copy(summary = merged, todayTransactions = basePoints + overlayPoints) }
    }
}
