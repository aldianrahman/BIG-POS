package com.berdikariintigemilang.pos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.data.local.PendingTransactionEntity
import com.berdikariintigemilang.pos.data.remote.DashboardSummaryDto
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.remote.TopProductDto
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.repository.DashboardRepository
import com.berdikariintigemilang.pos.data.repository.InventoryRepository
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
 * Dashboard yang sepenuhnya offline-capable & realtime: SEMUA kartu (omzet,
 * transaksi, laba, rata-rata, grafik, produk terlaris, stok menipis) langsung
 * mencerminkan transaksi lokal — termasuk penjualan offline yang belum
 * tersinkron. Saat online, angka server menjadi basis dan transaksi lokal yang
 * belum tersinkron ditambahkan di atasnya (tanpa dobel). Saat offline, seluruh
 * angka dihitung dari data lokal HP ini.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val transactionRepository: TransactionRepository,
    private val inventoryRepository: InventoryRepository,
    private val offlineStore: OfflineTransactionStore,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Cache data server terakhir + transaksi lokal terkini.
    private var serverSummary: DashboardSummaryDto? = null
    private var serverTop: List<TopProductDto> = emptyList()
    private var serverLowStock: List<StockDto> = emptyList()
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
            viewModelScope.launch { rebuild() }
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
            (dashboardRepository.topProducts(date) as? ApiResult.Success)?.let { serverTop = it.data }
            (dashboardRepository.lowStock() as? ApiResult.Success)?.let { serverLowStock = it.data }
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
     * Gabungkan data server + transaksi lokal HARI INI ke seluruh kartu dashboard.
     * Online: basis = server, overlay = transaksi lokal belum tersinkron.
     * Offline: seluruhnya dari transaksi lokal hari ini.
     */
    private suspend fun rebuild() {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val serverAvailable = online && serverFresh
        val includeSynced = !serverAvailable

        val agg = offlineStore.aggregateLocalSales(todayStart, includeSynced)

        // Titik grafik dari transaksi lokal (filter sama dengan agregat).
        val overlayPoints = localTxns
            .filter { it.createdAt >= todayStart && (includeSynced || it.status != "SYNCED") }
            .sortedBy { it.createdAt }
            .map {
                ChartPoint(
                    label = LocalDateTime.ofInstant(Instant.ofEpochMilli(it.createdAt), ZoneId.systemDefault()).format(timeFmt),
                    value = it.totalAmount
                )
            }

        // ── Ringkasan ──
        val baseSales = if (serverAvailable) serverSummary?.totalSales ?: 0.0 else 0.0
        val baseCount = if (serverAvailable) serverSummary?.totalTransactions ?: 0 else 0
        val baseProfit = if (serverAvailable) serverSummary?.totalProfit ?: 0.0 else 0.0
        val totalSales = baseSales + agg.totalSales
        val totalCount = baseCount + agg.count
        val totalProfit = baseProfit + agg.totalProfit
        val summary = DashboardSummaryDto(
            date = LocalDate.now().toString(),
            totalSales = totalSales,
            totalTransactions = totalCount,
            totalProfit = totalProfit,
            avgTransactionValue = if (totalCount > 0) totalSales / totalCount else 0.0
        )

        // ── Produk terlaris (gabung server + lokal, urut ulang) ──
        val merged = LinkedHashMap<Long, TopProductDto>()
        if (serverAvailable) serverTop.forEach { merged[it.productId] = it }
        agg.products.forEach { lp ->
            val ex = merged[lp.productId]
            merged[lp.productId] = ex?.copy(
                quantitySold = ex.quantitySold + lp.quantitySold,
                totalSales = ex.totalSales + lp.totalSales
            ) ?: TopProductDto(lp.productId, lp.sku, lp.name, lp.quantitySold, lp.totalSales)
        }
        val topProducts = merged.values.sortedByDescending { it.quantitySold }.take(5)

        // ── Stok menipis dari cache lokal (realtime); fallback server bila cache kosong ──
        val localLow = inventoryRepository.localStockList(null, lowStock = true)
        val lowStock = if (localLow.isNotEmpty() || serverLowStock.isEmpty()) localLow else serverLowStock

        val basePoints = if (serverAvailable) serverPoints else emptyList()

        _state.update {
            it.copy(
                summary = summary,
                topProducts = topProducts,
                lowStock = lowStock,
                todayTransactions = basePoints + overlayPoints,
                offline = !online
            )
        }
    }
}
