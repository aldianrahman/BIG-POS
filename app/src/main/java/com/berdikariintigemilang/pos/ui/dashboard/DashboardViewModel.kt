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

/** Satu titik grafik (label sumbu-x + nilai). */
data class ChartPoint(val label: String, val value: Double)

data class DashboardUiState(
    val loading: Boolean = true,
    val summary: DashboardSummaryDto? = null,
    val topProducts: List<TopProductDto> = emptyList(),
    val lowStock: List<StockDto> = emptyList(),
    val hourlyTransactions: List<ChartPoint> = emptyList(),
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
            loadHourlyTransactions()
            _state.update { it.copy(loading = false) }
        }
    }

    /** Jumlah transaksi per jam untuk HARI INI (melihat jam ramai). */
    private suspend fun loadHourlyTransactions() {
        val from = LocalDate.now().atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val rows = (reportRepository.sales(from, to, "hour") as? ApiResult.Success)?.data ?: return
        val points = rows
            .sortedBy { hourOf(it.label) ?: Int.MAX_VALUE }
            .map { row ->
                val h = hourOf(row.label)
                val label = if (h != null) "%02d".format(h) else row.label.takeLast(5)
                ChartPoint(label = label, value = row.totalTransactions.toDouble())
            }
        _state.update { it.copy(hourlyTransactions = points) }
    }
}

/** Ambil angka jam (0-23) dari label apa pun: "14:00", "...T14", atau "14". */
private fun hourOf(label: String): Int? {
    Regex("(\\d{1,2})[:.]\\d{2}").find(label)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    Regex("[ T](\\d{1,2})(?!\\d)").find(label)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return label.trim().toIntOrNull()
}
