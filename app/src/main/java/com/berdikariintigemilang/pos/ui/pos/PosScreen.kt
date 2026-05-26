package com.berdikariintigemilang.pos.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.ui.components.PrimaryButton

@Composable
fun PosScreen(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCheckout: () -> Unit = {},
    onHistory: () -> Unit = {},
    viewModel: PosViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onSearchClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("Cari produk (nama / SKU)", style = MaterialTheme.typography.bodyLarge)
                }
            }
            FilledIconButton(onClick = onScanClick, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
            }
            IconButton(onClick = onHistory, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = "Riwayat transaksi")
            }
        }

        if (state.isEmpty) {
            EmptyCart(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.lines, key = { it.productId }) { line ->
                    CartItemRow(
                        line = line,
                        onIncrement = { viewModel.increment(line) },
                        onDecrement = { viewModel.decrement(line) },
                        onRemove = { viewModel.remove(line.productId) }
                    )
                }
            }
        }

        CheckoutCard(
            subtotal = state.subtotal,
            discount = state.discount,
            total = state.total,
            canCheckout = state.canCheckout,
            onDiscountChange = viewModel::setDiscount,
            onCheckout = onCheckout
        )
    }
}

@Composable
private fun EmptyCart(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            "Scan atau cari produk untuk mulai",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun CartItemRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.rupiah(line.unitPrice)} · stok ${line.stock}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    Formatters.rupiah(line.lineSubtotal),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi") }
            Text(line.quantity.toString(), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = "Tambah") }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CheckoutCard(
    subtotal: Double,
    discount: Double,
    total: Double,
    canCheckout: Boolean,
    onDiscountChange: (Double) -> Unit,
    onCheckout: () -> Unit
) {
    var discountText by remember { mutableStateOf(if (discount > 0) discount.toLong().toString() else "") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", style = MaterialTheme.typography.bodyLarge)
                Text(Formatters.rupiah(subtotal), style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedTextField(
                value = discountText,
                onValueChange = { v ->
                    discountText = v.filter { it.isDigit() }
                    onDiscountChange(discountText.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Diskon (Rp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL", style = MaterialTheme.typography.titleLarge)
                Text(
                    Formatters.rupiah(total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            PrimaryButton(
                text = "BAYAR",
                modifier = Modifier.fillMaxWidth(),
                enabled = canCheckout,
                onClick = onCheckout
            )
        }
    }
}
