package com.berdikariintigemilang.pos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.data.remote.ProfitReportDto
import com.berdikariintigemilang.pos.data.remote.SalesReportRowDto
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

data class ReportsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val groupBy: String = "day",
    val rangeLabel: String = "7 hari terakhir",
    val rows: List<SalesReportRowDto> = emptyList(),
    val profit: ProfitReportDto? = null,
    val offline: Boolean = false,
    val error: String? = null
)

/**
 * Laporan adalah hasil agregasi server (penjualan per kelompok + laba), jadi
 * hanya tersedia saat online. Saat offline, seluruh kartu dikosongkan dan
 * ditampilkan pesan agar pengguna online. Otomatis dimuat ulang saat koneksi
 * kembali.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState(offline = !connectivity.isOnlineNow()))
    val state: StateFlow<ReportsUiState> = _state

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                if (online) load() else markOffline()
            }
        }
    }

    fun setGroupBy(groupBy: String) {
        _state.update { it.copy(groupBy = groupBy) }
        load()
    }

    fun load() = fetch(isRefresh = false)

    /** Muat ulang via tarik-ke-bawah. */
    fun refresh() = fetch(isRefresh = true)

    private fun markOffline() {
        _state.update {
            it.copy(loading = false, refreshing = false, offline = true, rows = emptyList(), profit = null, error = null)
        }
    }

    private fun fetch(isRefresh: Boolean) {
        if (!connectivity.isOnlineNow()) {
            markOffline()
            return
        }
        val from = LocalDate.now().minusDays(6).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val groupBy = _state.value.groupBy
        _state.update {
            if (isRefresh) it.copy(refreshing = true, error = null, offline = false)
            else it.copy(loading = true, error = null, offline = false)
        }
        viewModelScope.launch {
            when (val sales = reportRepository.sales(from, to, groupBy)) {
                is ApiResult.Success -> _state.update { it.copy(rows = sales.data) }
                is ApiResult.Error -> _state.update { it.copy(error = sales.message) }
            }
            (reportRepository.profit(from, to) as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(profit = res.data) }
            }
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }
}
