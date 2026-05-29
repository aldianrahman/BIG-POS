package com.berdikariintigemilang.pos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.DashboardSummaryDto
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.remote.TopProductDto
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.data.repository.DashboardRepository
import com.berdikariintigemilang.pos.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init { load() }

    fun load() = fetch(isRefresh = false)

    /** Muat ulang via tarik-ke-bawah (indikator refresh, konten tetap tampil). */
    fun refresh() = fetch(isRefresh = true)

    private fun fetch(isRefresh: Boolean) {
        val date = LocalDate.now().toString()
        _state.update {
            if (isRefresh) it.copy(refreshing = true, error = null) else it.copy(loading = true, error = null)
        }
        viewModelScope.launch {
            when (val summary = dashboardRepository.summary(date)) {
                is ApiResult.Success -> _state.update { it.copy(summary = summary.data) }
                is ApiResult.Error -> _state.update { it.copy(error = summary.message) }
            }
            (dashboardRepository.topProducts(date) as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(topProducts = res.data) }
            }
            (dashboardRepository.lowStock() as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(lowStock = res.data) }
            }
            loadTodayTransactions()
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    /**
     * Ambil semua transaksi HARI INI (urut pertama → terakhir). Tiap transaksi
     * menjadi satu titik grafik; nilainya = totalAmount transaksi tersebut.
     */
    private suspend fun loadTodayTransactions() {
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

        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val points = all
            .filter { !it.status.contains("VOID", ignoreCase = true) }
            .sortedBy { it.id }
            .map { trx ->
                val time = trx.createdAt?.let { iso ->
                    runCatching { LocalDateTime.parse(iso).format(timeFmt) }.getOrNull()
                } ?: ""
                ChartPoint(label = time, value = trx.totalAmount)
            }
        _state.update { it.copy(todayTransactions = points) }
    }
}
