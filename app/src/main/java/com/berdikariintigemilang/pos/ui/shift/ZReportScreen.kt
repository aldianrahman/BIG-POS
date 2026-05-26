package com.berdikariintigemilang.pos.ui.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.remote.ZReportDto
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.berdikariintigemilang.pos.ui.components.SectionTitle
import com.berdikariintigemilang.pos.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZReportScreen(
    onDone: () -> Unit,
    viewModel: ZReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Z-Report (Laporan Shift)",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.report != null -> ReportContent(
                report = state.report!!,
                onDone = onDone,
                modifier = Modifier.padding(padding)
            )
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        state.error ?: "Gagal memuat laporan",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                PrimaryButton(
                    text = "SELESAI",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDone
                )
            }
        }
    }
}

@Composable
private fun ReportContent(report: ZReportDto, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Shift header ─────────────────────────────────────────────────────
        report.shift?.let { s ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            s.cashierName ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusChip(
                            text = "Selesai",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.secondary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReportInfoRow(
                        label = "Dibuka",
                        value = Formatters.displayDateTime(s.openedAt)
                    )
                    ReportInfoRow(
                        label = "Ditutup",
                        value = Formatters.displayDateTime(s.closedAt)
                    )
                }
            }
        }

        // ── Ringkasan kas ─────────────────────────────────────────────────────
        SectionTitle(title = "Ringkasan Kas", icon = Icons.Filled.AttachMoney)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportInfoRow(
                    label = "Modal Awal",
                    value = Formatters.rupiah(report.openingCash),
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReportInfoRow(
                    label = "Total Penjualan",
                    value = Formatters.rupiah(report.totalSales),
                    valueColor = MaterialTheme.colorScheme.secondary,
                    valueBold = true
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReportInfoRow(
                    label = "Jumlah Transaksi",
                    value = report.totalTransactions.toString(),
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReportInfoRow(
                    label = "Kas Masuk",
                    value = Formatters.rupiah(report.totalCashIn),
                    valueColor = MaterialTheme.colorScheme.secondary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReportInfoRow(
                    label = "Kas Keluar",
                    value = Formatters.rupiah(report.totalCashOut),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }
        }

        // ── Rekonsiliasi kas ──────────────────────────────────────────────────
        SectionTitle(title = "Rekonsiliasi Kas", icon = Icons.Filled.Receipt)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportInfoRow(
                    label = "Kas Diharapkan",
                    value = Formatters.rupiah(report.expectedCash),
                    valueColor = MaterialTheme.colorScheme.primary,
                    valueBold = true
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReportInfoRow(
                    label = "Kas Aktual",
                    value = Formatters.rupiah(report.closingCash),
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReportInfoRow(
                    label = "Selisih",
                    value = Formatters.rupiah(report.cashDifference),
                    valueColor = if (report.cashDifference >= 0)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.error,
                    valueBold = true
                )
            }
        }

        // ── Produk terlaris ───────────────────────────────────────────────────
        if (report.topProducts.isNotEmpty()) {
            SectionTitle(title = "Produk Terlaris", icon = Icons.Filled.Check)
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.topProducts.forEachIndexed { index, p ->
                        ReportInfoRow(
                            label = "${p.name} (${p.quantitySold})",
                            value = Formatters.rupiah(p.totalSales),
                            valueColor = MaterialTheme.colorScheme.secondary
                        )
                        if (index < report.topProducts.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        PrimaryButton(
            text = "SELESAI",
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Print,
            onClick = onDone
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReportInfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    valueBold: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (valueBold) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor
        )
    }
}
