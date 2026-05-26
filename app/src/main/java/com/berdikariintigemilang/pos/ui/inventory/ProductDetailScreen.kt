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
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAdjust by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

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
                        OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving) {
                            Text("Edit Harga & Min Stok")
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
    if (showEdit) {
        state.product?.let { p ->
            EditPriceDialog(
                product = p,
                onDismiss = { showEdit = false },
                onConfirm = { buy, sell, min -> viewModel.updatePrice(buy, sell, min); showEdit = false }
            )
        }
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

@Composable
private fun EditPriceDialog(product: ProductDto, onDismiss: () -> Unit, onConfirm: (Double, Double, Int) -> Unit) {
    var buy by remember { mutableStateOf(product.purchasePrice.toLong().toString()) }
    var sell by remember { mutableStateOf(product.sellingPrice.toLong().toString()) }
    var min by remember { mutableStateOf((product.minStock ?: 0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Harga & Min Stok") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = buy, onValueChange = { v -> buy = v.filter { it.isDigit() } }, label = { Text("Harga Beli") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = sell, onValueChange = { v -> sell = v.filter { it.isDigit() } }, label = { Text("Harga Jual") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = min, onValueChange = { v -> min = v.filter { it.isDigit() } }, label = { Text("Min Stok") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(buy.toDoubleOrNull() ?: 0.0, sell.toDoubleOrNull() ?: 0.0, min.toIntOrNull() ?: 0)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
