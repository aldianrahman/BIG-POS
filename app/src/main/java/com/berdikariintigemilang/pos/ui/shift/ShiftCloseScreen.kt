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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.berdikariintigemilang.pos.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftCloseScreen(
    onBack: () -> Unit,
    onClosed: (Long) -> Unit,
    viewModel: ShiftCloseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var cash by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.closed.collect { onClosed(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tutup Shift",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.shift?.let { s ->
                SectionTitle(title = "Ringkasan Shift", icon = Icons.Filled.PointOfSale)
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow(
                            label = "Modal Awal",
                            value = Formatters.rupiah(s.openingCash),
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            label = "Total Penjualan",
                            value = Formatters.rupiah(s.totalSales),
                            valueColor = MaterialTheme.colorScheme.secondary,
                            valueBold = true
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            label = "Jumlah Transaksi",
                            value = s.totalTransactions.toString(),
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            SectionTitle(title = "Kas Penutupan", icon = Icons.Filled.AttachMoney)

            OutlinedTextField(
                value = cash,
                onValueChange = { v -> cash = v.filter { it.isDigit() } },
                label = { Text("Kas Akhir Aktual (Rp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan (opsional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            state.error?.let {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            PrimaryButton(
                text = "TUTUP SHIFT",
                modifier = Modifier.fillMaxWidth(),
                loading = state.submitting,
                enabled = cash.isNotBlank(),
                icon = Icons.Filled.Receipt,
                onClick = { viewModel.close(cash.toDoubleOrNull() ?: 0.0, notes.ifBlank { null }) }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(
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
