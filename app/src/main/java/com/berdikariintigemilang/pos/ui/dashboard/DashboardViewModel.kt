package com.berdikariintigemilang.pos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.DashboardSummaryDto
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.remote.TopProductDto
import com.berdikariintigemilang.pos.data.repository.DashboardRepository
import com.berdikariintigemilang.pos.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Satu titik tren omset harian (untuk grafik garis). */
data class SalesTrendPoint(val label: String, val value: Double)

data class DashboardUiState(
    val loading: Boolean = true,
    val summary: DashboardSummaryDto? = null,
    val topProducts: List<TopProductDto> = emptyList(),
    val lowStock: List<StockDto> = emptyList(),
    val salesTrend: List<SalesTrendPoint> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init { load() }

    fun load() {
        val date = LocalDate.now().toString()
        _state.update { it.copy(loading = true, error = null) }
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
            loadTrend()
            _state.update { it.copy(loading = false) }
        }
    }

    /** Ambil omset 7 hari terakhir, isi-nol hari tanpa transaksi agar garis kontinu. */
    private suspend fun loadTrend() {
        val from = LocalDate.now().minusDays(6).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val rows = (reportRepository.sales(from, to, "day") as? ApiResult.Success)?.data ?: return
        val shortFmt = DateTimeFormatter.ofPattern("dd/MM")
        val trend = (6 downTo 0).map { back ->
            val day = LocalDate.now().minusDays(back.toLong())
            val iso = day.toString()
            val sales = rows.firstOrNull { it.label.startsWith(iso) }?.totalSales ?: 0.0
            SalesTrendPoint(label = day.format(shortFmt), value = sales)
        }
        _state.update { it.copy(salesTrend = trend) }
    }
}
