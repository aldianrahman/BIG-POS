package com.berdikariintigemilang.pos.ui.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton

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
        topBar = {
            TopAppBar(
                title = { Text("Tutup Shift") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.shift?.let { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Modal Awal", Formatters.rupiah(s.openingCash))
                        InfoRow("Total Penjualan", Formatters.rupiah(s.totalSales))
                        InfoRow("Jumlah Transaksi", s.totalTransactions.toString())
                    }
                }
            }

            OutlinedTextField(
                value = cash,
                onValueChange = { v -> cash = v.filter { it.isDigit() } },
                label = { Text("Kas Akhir Aktual (Rp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan (opsional)") },
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
            }

            PrimaryButton(
                text = "TUTUP SHIFT",
                modifier = Modifier.fillMaxWidth(),
                loading = state.submitting,
                enabled = cash.isNotBlank(),
                onClick = { viewModel.close(cash.toDoubleOrNull() ?: 0.0, notes.ifBlank { null }) }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
