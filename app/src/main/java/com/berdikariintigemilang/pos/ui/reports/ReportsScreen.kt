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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.berdikariintigemilang.pos.ui.components.EmptyState
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.SectionTitle
import com.berdikariintigemilang.pos.ui.components.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val groups = listOf("day" to "Hari", "hour" to "Jam", "category" to "Kategori", "product" to "Produk")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(
            title = "Laporan (${state.rangeLabel})",
            icon = Icons.Filled.Assessment
        )

        // Profit summary stat cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Omset",
                value = Formatters.rupiah(state.profit?.revenue),
                icon = Icons.Filled.Payments,
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
                accentContainer = MaterialTheme.colorScheme.secondaryContainer
            )
            StatCard(
                label = "HPP",
                value = Formatters.rupiah(state.profit?.cogs),
                icon = Icons.Filled.BarChart,
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary,
                accentContainer = MaterialTheme.colorScheme.tertiaryContainer
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Profit",
                value = Formatters.rupiah(state.profit?.profit),
                icon = Icons.Filled.TrendingUp,
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
                accentContainer = MaterialTheme.colorScheme.secondaryContainer
            )
            StatCard(
                label = "Margin",
                value = "${(state.profit?.marginPercent ?: 0.0)}%",
                icon = Icons.Filled.Assessment,
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
                accentContainer = MaterialTheme.colorScheme.primaryContainer
            )
        }

        SectionTitle(title = "Penjualan per", icon = Icons.Filled.BarChart)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (key, label) ->
                FilterChip(
                    selected = state.groupBy == key,
                    onClick = { viewModel.setGroupBy(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        when {
            state.loading -> FullScreenLoading(modifier = Modifier.padding(vertical = 32.dp))
            state.error != null -> AppCard {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            state.rows.isEmpty() -> EmptyState(
                icon = Icons.Filled.BarChart,
                title = "Tidak ada data",
                subtitle = "Tidak ada data pada periode ini"
            )
            else -> AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "Item",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Qty",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Text(
                            "Omset",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.rows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                row.label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                Formatters.number(row.quantitySold),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                Formatters.rupiah(row.totalSales),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
