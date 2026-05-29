package com.berdikariintigemilang.pos.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.remote.StockDto
import com.berdikariintigemilang.pos.data.remote.TopProductDto
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.LineChart
import com.berdikariintigemilang.pos.ui.components.ScreenHeader
import com.berdikariintigemilang.pos.ui.components.SoftBadge
import com.berdikariintigemilang.pos.ui.components.StatTile

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ScreenHeader(title = "Dashboard", subtitle = "Ringkasan penjualan hari ini")

        if (state.loading) {
            FullScreenLoading(Modifier.weight(1f))
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            val s = state.summary
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Omset", Formatters.rupiah(s?.totalSales), Icons.Filled.Payments, Modifier.weight(1f))
                StatTile("Transaksi", Formatters.number(s?.totalTransactions), Icons.Filled.ReceiptLong, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Profit", Formatters.rupiah(s?.totalProfit), Icons.Filled.TrendingUp, Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.secondary
                )
                StatTile("Rata-rata", Formatters.rupiah(s?.avgTransactionValue), Icons.Filled.BarChart, Modifier.weight(1f))
            }

            if (state.salesTrend.isNotEmpty()) {
                SalesTrendCard(state.salesTrend)
            }

            IconSectionHeader(Icons.Filled.LocalFireDepartment, MaterialTheme.colorScheme.primary, "Produk Terlaris")
            ListCard {
                if (state.topProducts.isEmpty()) {
                    EmptyHint("Belum ada penjualan hari ini")
                } else {
                    state.topProducts.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        TopProductRow(p)
                    }
                }
            }

            IconSectionHeader(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "Stok Menipis")
            ListCard {
                if (state.lowStock.isEmpty()) {
                    EmptyHint("Semua stok aman")
                } else {
                    state.lowStock.forEachIndexed { i, st ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        LowStockRow(st)
                    }
                }
            }
        }
    }
}

@Composable
private fun IconSectionHeader(icon: ImageVector, iconTint: androidx.compose.ui.graphics.Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SalesTrendCard(trend: List<SalesTrendPoint>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tren Omset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("7 hari terakhir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    Formatters.rupiah(trend.sumOf { it.value }),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            if (trend.all { it.value <= 0.0 }) {
                Text(
                    "Belum ada penjualan dalam 7 hari terakhir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LineChart(
                    values = trend.map { it.value.toFloat() },
                    labels = trend.map { it.label }
                )
            }
        }
    }
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { content() }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun TopProductRow(p: TopProductDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Terjual ${p.quantitySold}x hari ini", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Formatters.rupiah(p.totalSales), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun LowStockRow(s: StockDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.sku, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SoftBadge(
            text = "Sisa ${Formatters.number(s.quantity)}",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.tertiary
        )
    }
}
