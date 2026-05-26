package com.berdikariintigemilang.pos.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.SectionTitle
import com.berdikariintigemilang.pos.ui.components.StatCard
import com.berdikariintigemilang.pos.ui.theme.PosInfo
import com.berdikariintigemilang.pos.ui.theme.PosInfoContainer

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.loading) {
        FullScreenLoading(modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Ringkasan penjualan hari ini",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Omset", Formatters.rupiah(state.summary?.totalSales), Icons.Filled.Payments,
                Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
                accentContainer = MaterialTheme.colorScheme.primaryContainer
            )
            StatCard(
                "Transaksi", Formatters.number(state.summary?.totalTransactions), Icons.Filled.ReceiptLong,
                Modifier.weight(1f),
                accent = PosInfo, accentContainer = PosInfoContainer
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Profit", Formatters.rupiah(state.summary?.totalProfit), Icons.Filled.TrendingUp,
                Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
                accentContainer = MaterialTheme.colorScheme.secondaryContainer
            )
            StatCard(
                "Rata-rata", Formatters.rupiah(state.summary?.avgTransactionValue), Icons.Filled.Analytics,
                Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary,
                accentContainer = MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        SectionTitle("Produk Terlaris", icon = Icons.Outlined.LocalFireDepartment)
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.topProducts.isEmpty()) {
                    Text("Belum ada penjualan hari ini", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else state.topProducts.forEach { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${p.name} · ${p.quantitySold}x",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(Formatters.rupiah(p.totalSales), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        SectionTitle("Stok Menipis", icon = Icons.Filled.Warning)
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.lowStock.isEmpty()) {
                    Text("Semua stok aman", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else state.lowStock.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text("${s.quantity} / min ${s.minStock}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
