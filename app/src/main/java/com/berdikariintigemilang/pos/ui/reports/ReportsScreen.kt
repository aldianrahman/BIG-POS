package com.berdikariintigemilang.pos.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val groups = listOf("day" to "Hari", "hour" to "Jam", "category" to "Kategori", "product" to "Produk")

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Laporan (${state.rangeLabel})", style = MaterialTheme.typography.headlineSmall)

        // Profit summary
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Omset", Formatters.rupiah(state.profit?.revenue), Modifier.weight(1f))
            MetricCard("HPP", Formatters.rupiah(state.profit?.cogs), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Profit", Formatters.rupiah(state.profit?.profit), Modifier.weight(1f))
            MetricCard("Margin", "${(state.profit?.marginPercent ?: 0.0)}%", Modifier.weight(1f))
        }

        Text("Penjualan per", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (key, label) ->
                FilterChip(
                    selected = state.groupBy == key,
                    onClick = { viewModel.setGroupBy(key) },
                    label = { Text(label) }
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) {
                    Text("Memuat...", color = MaterialTheme.colorScheme.outline)
                } else if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                } else if (state.rows.isEmpty()) {
                    Text("Tidak ada data pada periode ini", color = MaterialTheme.colorScheme.outline)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Item", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Qty", fontWeight = FontWeight.Bold)
                        Text("   Omset", fontWeight = FontWeight.Bold)
                    }
                    state.rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(row.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Formatters.number(row.quantitySold))
                            Text(Formatters.rupiah(row.totalSales))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
