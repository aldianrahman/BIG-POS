package com.berdikariintigemilang.pos.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    onPaid: (Long) -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.success.collect { onPaid(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pembayaran") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.bundleDiscount > 0) {
                        AmountRow("Subtotal", Formatters.rupiah(state.subtotal))
                        AmountRow("Potongan Bundle", "-" + Formatters.rupiah(state.bundleDiscount), color = MaterialTheme.colorScheme.secondary)
                    }
                    if (state.taxAmount > 0) {
                        AmountRow(if (state.taxInclusive) "PPN (termasuk)" else "PPN", Formatters.rupiah(state.taxAmount))
                    }
                    AmountRow("Total", Formatters.rupiah(state.total), big = true, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AmountRow("Uang Diterima", Formatters.rupiah(state.cash))
                    AmountRow(
                        "Kembalian",
                        Formatters.rupiah(state.change),
                        big = true,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Quick amounts
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickButton("50rb", Modifier.weight(1f)) { viewModel.setAmount(50_000) }
                QuickButton("100rb", Modifier.weight(1f)) { viewModel.setAmount(100_000) }
                QuickButton("200rb", Modifier.weight(1f)) { viewModel.setAmount(200_000) }
                QuickButton("Uang Pas", Modifier.weight(1f)) { viewModel.setExact() }
            }

            NumPad(
                modifier = Modifier.weight(1f),
                onDigit = viewModel::appendDigit,
                onBackspace = viewModel::backspace,
                onClear = viewModel::clearCash
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            }

            PrimaryButton(
                text = "KONFIRMASI BAYAR",
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.fillMaxWidth(),
                loading = state.submitting,
                enabled = state.sufficient,
                onClick = viewModel::confirm
            )
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String, big: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (big) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuickButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NumPad(
    modifier: Modifier = Modifier,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "000")
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "C" -> onClear()
                                else -> onDigit(key)
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).aspectRatio(1.6f)
                    ) {
                        Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                if (row.contains("000")) {
                    IconButton(onClick = onBackspace, modifier = Modifier.weight(0.6f)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Hapus")
                    }
                }
            }
        }
    }
}
