package com.berdikariintigemilang.pos.ui.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.remote.ZReportDto
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZReportScreen(
    onDone: () -> Unit,
    viewModel: ZReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Z-Report (Laporan Shift)") }) }) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.report != null -> ReportContent(
                report = state.report!!,
                onDone = onDone,
                modifier = Modifier.padding(padding)
            )
            else -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text(state.error ?: "Gagal memuat laporan", color = MaterialTheme.colorScheme.error)
                PrimaryButton("SELESAI", Modifier.fillMaxWidth().padding(top = 16.dp), onClick = onDone)
            }
        }
    }
}

@Composable
private fun ReportContent(report: ZReportDto, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        report.shift?.let {
            Text(
                "Kasir: ${it.cashierName ?: "-"}",
                style = MaterialTheme.typography.titleLarge
            )
            Text("Dibuka: ${Formatters.displayDateTime(it.openedAt)}")
            Text("Ditutup: ${Formatters.displayDateTime(it.closedAt)}")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Line("Modal Awal", Formatters.rupiah(report.openingCash))
                Line("Total Penjualan", Formatters.rupiah(report.totalSales))
                Line("Jumlah Transaksi", report.totalTransactions.toString())
                Line("Kas Masuk", Formatters.rupiah(report.totalCashIn))
                Line("Kas Keluar", Formatters.rupiah(report.totalCashOut))
                HorizontalDivider()
                Line("Kas Diharapkan", Formatters.rupiah(report.expectedCash), bold = true)
                Line("Kas Aktual", Formatters.rupiah(report.closingCash))
                Line("Selisih", Formatters.rupiah(report.cashDifference), bold = true)
            }
        }

        if (report.topProducts.isNotEmpty()) {
            Text("Produk Terlaris", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.topProducts.forEach { p ->
                        Line("${p.name} (${p.quantitySold})", Formatters.rupiah(p.totalSales))
                    }
                }
            }
        }

        PrimaryButton("SELESAI", Modifier.fillMaxWidth(), onClick = onDone)
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
