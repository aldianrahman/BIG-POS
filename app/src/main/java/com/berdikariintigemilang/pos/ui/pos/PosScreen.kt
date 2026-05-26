package com.berdikariintigemilang.pos.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.EmptyState
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cari produk (nama / SKU)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledIconButton(
                onClick = onScanClick,
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
            }
            FilledIconButton(
                onClick = onHistory,
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = "Riwayat transaksi")
            }
        }

        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Outlined.ShoppingCart,
                title = "Keranjang kosong",
                subtitle = "Scan atau cari produk untuk mulai transaksi",
                modifier = Modifier.weight(1f)
            )
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
            bundleDiscount = state.bundleDiscount,
            bundleLabels = state.appliedBundles.map { "${it.name} x${it.count}" },
            taxAmount = state.taxAmount,
            taxInclusive = state.taxInclusive,
            total = state.total,
            canCheckout = state.canCheckout,
            onDiscountChange = viewModel::setDiscount,
            onCheckout = onCheckout
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
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.rupiah(line.unitPrice)} · stok ${line.stock}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    Formatters.rupiah(line.lineSubtotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            QtyStepper(quantity = line.quantity, onDecrement = onDecrement, onIncrement = onIncrement)
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun QtyStepper(quantity: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepIcon(Icons.Filled.Remove, "Kurangi", onDecrement)
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        StepIcon(Icons.Filled.Add, "Tambah", onIncrement)
    }
}

@Composable
private fun StepIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = desc, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CheckoutCard(
    subtotal: Double,
    discount: Double,
    bundleDiscount: Double,
    bundleLabels: List<String>,
    taxAmount: Double,
    taxInclusive: Boolean,
    total: Double,
    canCheckout: Boolean,
    onDiscountChange: (Double) -> Unit,
    onCheckout: () -> Unit
) {
    var discountText by remember { mutableStateOf(if (discount > 0) discount.toLong().toString() else "") }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatters.rupiah(subtotal), style = MaterialTheme.typography.bodyLarge)
            }
            if (bundleDiscount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Potongan Bundle", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                        bundleLabels.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("-${Formatters.rupiah(bundleDiscount)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
            OutlinedTextField(
                value = discountText,
                onValueChange = { v ->
                    discountText = v.filter { it.isDigit() }
                    onDiscountChange(discountText.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Diskon (Rp)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (taxAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (taxInclusive) "PPN (termasuk)" else "PPN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.rupiah(taxAmount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOTAL", style = MaterialTheme.typography.titleMedium)
                Text(
                    Formatters.rupiah(total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            PrimaryButton(
                text = "BAYAR",
                icon = Icons.Filled.Payments,
                modifier = Modifier.fillMaxWidth(),
                enabled = canCheckout,
                onClick = onCheckout
            )
        }
    }
}
