package com.berdikariintigemilang.pos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.ConnectivityObserver
import com.berdikariintigemilang.pos.data.remote.ProfitReportDto
import com.berdikariintigemilang.pos.data.remote.SalesReportRowDto
import com.berdikariintigemilang.pos.data.repository.ReportRepository
import com.berdikariintigemilang.pos.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Format tanggal untuk label rentang (mis. "20 Jun 2026"). */
private val RANGE_LABEL_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

data class ReportsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val groupBy: String = "day",
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val rows: List<SalesReportRowDto> = emptyList(),
    val profit: ProfitReportDto? = null,
    val offline: Boolean = false,
    val error: String? = null
) {
    /** Subjudul rentang: satu tanggal bila sama, atau "awal – akhir". */
    val rangeLabel: String
        get() = if (from == to) from.format(RANGE_LABEL_FMT)
        else "${from.format(RANGE_LABEL_FMT)} – ${to.format(RANGE_LABEL_FMT)}"
}

/**
 * Laporan adalah hasil agregasi server (penjualan per kelompok + laba), jadi
 * server adalah satu-satunya sumber kebenaran — angka di HP selalu mengikuti
 * server, persis sama dengan tampilan web. Hanya tersedia saat online; saat
 * offline seluruh kartu dikosongkan dan ditampilkan pesan agar pengguna online.
 *
 * Rentang tanggal bisa dipilih (Dari–Sampai) seperti di web, default "hari ini"
 * — sehingga laporan Android dapat disamakan ke rentang apa pun yang dilihat di
 * web. Konversi waktu mengikuti web: from = 00:00:00, to = 23:59:59.
 *
 * Agar tidak ada selisih dengan web ("HP tidak langsung update"), laporan
 * disegarkan otomatis pada tiga momen — tanpa perlu tarik-ke-bawah manual:
 *  1. Koneksi kembali  -> muat ulang snapshot server.
 *  2. Saat dibuka/refresh -> dorong dulu transaksi lokal yang belum tersinkron
 *     ke server, supaya server memuat penjualan terbaru sebelum laporan ditarik.
 *  3. Setiap sinkronisasi SELESAI -> tarik ulang laporan tanpa kedip layar,
 *     sehingga transaksi yang baru tersinkron (mis. penjualan dari tab Kasir)
 *     langsung tampak — sama seperti web yang selalu menampilkan data server.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val connectivity: ConnectivityObserver,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState(offline = !connectivity.isOnlineNow()))
    val state: StateFlow<ReportsUiState> = _state

    init {
        // Koneksi berubah: online -> muat ulang; offline -> kosongkan & beri pesan.
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                if (online) load() else markOffline()
            }
        }
        // Setiap sinkronisasi SELESAI (saat online), tarik ulang laporan tanpa kedip
        // agar transaksi yang baru tersinkron langsung tercermin — sama seperti web.
        viewModelScope.launch {
            var prev = false
            syncManager.syncing.collect { syncing ->
                if (prev && !syncing && connectivity.isOnlineNow()) fetch(isRefresh = false, silent = true)
                prev = syncing
            }
        }
    }

    fun setGroupBy(groupBy: String) {
        _state.update { it.copy(groupBy = groupBy) }
        load()
    }

    /** Ubah tanggal mulai; jaga agar tidak melewati tanggal akhir. */
    fun setFrom(date: LocalDate) {
        _state.update { it.copy(from = date, to = if (date.isAfter(it.to)) date else it.to) }
        load()
    }

    /** Ubah tanggal akhir; jaga agar tidak mendahului tanggal mulai. */
    fun setTo(date: LocalDate) {
        _state.update { it.copy(to = date, from = if (date.isBefore(it.from)) date else it.from) }
        load()
    }

    fun load() {
        pushPending()
        fetch(isRefresh = false)
    }

    /** Muat ulang via tarik-ke-bawah. */
    fun refresh() {
        pushPending()
        fetch(isRefresh = true)
    }

    private fun markOffline() {
        _state.update {
            it.copy(loading = false, refreshing = false, offline = true, rows = emptyList(), profit = null, error = null)
        }
    }

    /**
     * Dorong penjualan lokal yang belum tersinkron ke server lebih dulu, supaya
     * server (sumber laporan) memuat data terbaru. Setelah sinkronisasi selesai,
     * observer di [init] otomatis menarik ulang laporan, jadi di sini cukup
     * memicu dorongannya. Aman dipanggil berkali-kali (di-skip bila sedang sync).
     */
    private fun pushPending() {
        if (!connectivity.isOnlineNow() || syncManager.syncing.value) return
        viewModelScope.launch { runCatching { syncManager.syncPending() } }
    }

    private fun fetch(isRefresh: Boolean, silent: Boolean = false) {
        if (!connectivity.isOnlineNow()) {
            markOffline()
            return
        }
        val s = _state.value
        // Konversi rentang persis seperti web: dari 00:00:00 sampai 23:59:59.
        val from = s.from.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val to = s.to.atTime(23, 59, 59).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val groupBy = s.groupBy
        if (!silent) {
            _state.update {
                if (isRefresh) it.copy(refreshing = true, error = null, offline = false)
                else it.copy(loading = true, error = null, offline = false)
            }
        }
        viewModelScope.launch {
            when (val sales = reportRepository.sales(from, to, groupBy)) {
                is ApiResult.Success -> _state.update { it.copy(rows = sales.data, error = null) }
                is ApiResult.Error -> _state.update { it.copy(error = sales.message) }
            }
            (reportRepository.profit(from, to) as? ApiResult.Success)?.let { res ->
                _state.update { it.copy(profit = res.data) }
            }
            _state.update { it.copy(loading = false, refreshing = false, offline = false) }
        }
    }
}
