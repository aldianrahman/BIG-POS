package com.berdikariintigemilang.pos.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAdjust by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.deleted.collect { onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.product == null -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text(state.error ?: "Produk tidak ditemukan", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                val p = state.product!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow("SKU", p.sku)
                            InfoRow("Barcode", p.barcode ?: "-")
                            InfoRow("Kategori", p.categoryName ?: "-")
                            InfoRow("Brand", p.brand)
                            InfoRow("Satuan", p.unit)
                            InfoRow("Harga Beli", Formatters.rupiah(p.purchasePrice))
                            InfoRow("Harga Jual", Formatters.rupiah(p.sellingPrice))
                            InfoRow("Stok", "${p.stockQuantity ?: 0}")
                            InfoRow("Min Stok", "${p.minStock ?: 0}")
                            InfoRow("Status", if (p.isActive) "Aktif" else "Nonaktif")
                        }
                    }

                    if (state.isAdmin) {
                        Button(onClick = { showAdjust = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving) {
                            Text("Sesuaikan Stok")
                        }
                        OutlinedButton(onClick = { onEdit(p.id) }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving) {
                            Text("Edit Produk")
                        }
                        OutlinedButton(
                            onClick = { showDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving && p.isActive
                        ) {
                            Text("Hapus Produk", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(
                            "Hanya admin yang dapat mengubah produk/stok.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    if (showAdjust) {
        AdjustDialog(
            onDismiss = { showAdjust = false },
            onConfirm = { qty, notes -> viewModel.adjustStock(qty, notes); showAdjust = false }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus Produk") },
            text = { Text("Nonaktifkan produk \"${state.product?.name ?: ""}\"? Produk tidak akan muncul lagi di kasir.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; viewModel.delete() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdjustDialog(onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var qty by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Penyesuaian Stok") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qty,
                    onValueChange = { v -> qty = v.filter { it.isDigit() || it == '-' } },
                    label = { Text("Jumlah (+/-)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Catatan") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(qty.toIntOrNull() ?: 0, notes.trim()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
