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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.berdikariintigemilang.pos.data.remote.ProductRequest
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("LUBY") }
    var unit by remember { mutableStateOf("PCS") }
    var buy by remember { mutableStateOf("0") }
    var sell by remember { mutableStateOf("0") }
    var minStock by remember { mutableStateOf("0") }
    var initialStock by remember { mutableStateOf("0") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }

    // Prefill sekali setelah data dimuat.
    LaunchedEffect(state.loading, state.product, state.categories) {
        if (!state.loading && !prefilled) {
            state.product?.let { p ->
                sku = p.sku; barcode = p.barcode ?: ""; name = p.name; desc = p.description ?: ""
                brand = p.brand; unit = p.unit; buy = p.purchasePrice.toLong().toString()
                sell = p.sellingPrice.toLong().toString(); minStock = (p.minStock ?: 0).toString()
                categoryId = p.categoryId
            }
            if (categoryId == null) categoryId = state.categories.firstOrNull()?.id
            prefilled = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit Produk" else "Tambah Produk") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Field("SKU*", sku) { sku = it }
            Field("Barcode", barcode) { barcode = it }
            Field("Nama*", name) { name = it }
            Field("Deskripsi", desc) { desc = it }
            CategoryDropdown(
                categories = state.categories,
                selectedId = categoryId,
                onSelect = { categoryId = it }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Brand", brand, Modifier.weight(1f)) { brand = it }
                Field("Unit", unit, Modifier.weight(1f)) { unit = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Harga Beli", buy, Modifier.weight(1f)) { buy = it }
                NumField("Harga Jual", sell, Modifier.weight(1f)) { sell = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Min Stok", minStock, Modifier.weight(1f)) { minStock = it }
                if (!state.isEdit) NumField("Stok Awal", initialStock, Modifier.weight(1f)) { initialStock = it }
            }

            PrimaryButton(
                text = if (state.isEdit) "SIMPAN PERUBAHAN" else "SIMPAN PRODUK",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                loading = state.saving,
                enabled = sku.isNotBlank() && name.isNotBlank() && categoryId != null,
                onClick = {
                    viewModel.save(
                        ProductRequest(
                            sku = sku.trim(),
                            barcode = barcode.trim().ifBlank { null },
                            name = name.trim(),
                            description = desc.trim().ifBlank { null },
                            categoryId = categoryId ?: 0,
                            brand = brand.trim().ifBlank { "LUBY" },
                            unit = unit.trim().ifBlank { "PCS" },
                            purchasePrice = buy.toDoubleOrNull() ?: 0.0,
                            sellingPrice = sell.toDoubleOrNull() ?: 0.0,
                            minStock = minStock.toIntOrNull() ?: 0,
                            initialStock = if (state.isEdit) null else (initialStock.toIntOrNull() ?: 0)
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth())
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<com.berdikariintigemilang.pos.data.remote.CategoryDto>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: "Pilih kategori"
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelect(c.id); expanded = false })
            }
        }
    }
}
