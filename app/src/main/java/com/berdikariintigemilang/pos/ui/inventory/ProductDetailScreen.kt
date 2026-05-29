package com.berdikariintigemilang.pos.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.remote.ProductDto
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.berdikariintigemilang.pos.ui.components.SectionTitle
import com.berdikariintigemilang.pos.ui.components.StatusChip

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detail Produk") },
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                p.sku,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(
                            text = if (p.isActive) "Aktif" else "Nonaktif",
                            container = if (p.isActive) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                            content = if (p.isActive) MaterialTheme.colorScheme.onSecondaryContainer
                                      else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Info card
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionTitle(title = "Informasi Produk", icon = Icons.Filled.Info)
                            Spacer(Modifier.height(8.dp))
                            InfoRow("Barcode", p.barcode ?: "-")
                            InfoRow("Kategori", p.categoryName ?: "-")
                            InfoRow("Brand", p.brand)
                            InfoRow("Satuan", p.unit)
                        }
                    }

                    // Stock card
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionTitle(title = "Stok", icon = Icons.Filled.Inventory2)
                            Spacer(Modifier.height(8.dp))
                            InfoRow("Stok Saat Ini", "${p.stockQuantity ?: 0}")
                            InfoRow("Min Stok", "${p.minStock ?: 0}")
                            val isLow = (p.stockQuantity ?: 0) <= (p.minStock ?: 0)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "Status Stok",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StatusChip(
                                    text = if (isLow) "Menipis" else "Tersedia",
                                    container = if (isLow) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.secondaryContainer,
                                    content = if (isLow) MaterialTheme.colorScheme.error
                                              else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Pricing card
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionTitle(title = "Harga", icon = Icons.Filled.AttachMoney)
                            Spacer(Modifier.height(8.dp))
                            InfoRow("Harga Beli", Formatters.rupiah(p.purchasePrice))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "Harga Jual",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    Formatters.rupiah(p.sellingPrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Actions
                    if (state.isAdmin) {
                        PrimaryButton(
                            text = "Sesuaikan Stok",
                            modifier = Modifier.fillMaxWidth(),
                            loading = state.saving,
                            enabled = !state.saving,
                            onClick = { showAdjust = true }
                        )
                        OutlinedButton(
                            onClick = { onEdit(p.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("  Edit Produk")
                        }
                        OutlinedButton(
                            onClick = { showDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving && p.isActive,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("  Hapus Produk", color = MaterialTheme.colorScheme.error)
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
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(qty.toIntOrNull() ?: 0, notes.trim()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
