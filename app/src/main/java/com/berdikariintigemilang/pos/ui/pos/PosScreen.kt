package com.berdikariintigemilang.pos.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.core.util.scanFeedback
import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.cart.DiscountMode
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
    var editingLine by remember { mutableStateOf<CartLine?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Alat scan barcode fisik (HID) memancarkan barcode utuh; tiap scan langsung
    // menambah 1 ke keranjang. Kolektor ini hanya aktif selagi layar Kasir tampil.
    LaunchedEffect(Unit) {
        viewModel.scannedBarcodes.collect { viewModel.onScannedBarcode(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.scanFeedback.collect { scanFeedback(context) }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // ── Pencarian + aksi (latar putih) ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onSearchClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cari produk / SKU", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledIconButton(
                onClick = onScanClick,
                modifier = Modifier.size(54.dp),
                shape = MaterialTheme.shapes.medium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.CropFree, contentDescription = "Scan barcode")
            }
            FilledIconButton(
                onClick = onHistory,
                modifier = Modifier.size(54.dp),
                shape = MaterialTheme.shapes.medium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Filled.History, contentDescription = "Riwayat transaksi")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // ── Area keranjang (latar putih) ────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.Outlined.PointOfSale,
                    title = "Keranjang kosong",
                    subtitle = "Scan atau cari produk untuk mulai transaksi",
                    modifier = Modifier.weight(1f),
                    action = {
                        OutlinedButton(
                            onClick = onSearchClick,
                            shape = MaterialTheme.shapes.medium,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tambah produk", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.lines.size} ITEM DI KERANJANG",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Kosongkan",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { viewModel.clear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.lines, key = { it.productId }) { line ->
                        CartItemRow(
                            line = line,
                            onIncrement = { viewModel.increment(line) },
                            onDecrement = { viewModel.decrement(line) },
                            onRemove = { viewModel.remove(line.productId) },
                            onEditQuantity = { editingLine = line }
                        )
                    }
                }
            }
        }

        // ── Ringkasan + bayar (latar putih) ─────────────────────────────────
        CheckoutSection(
            subtotal = state.subtotal,
            discount = state.discount,
            discountMode = state.discountMode,
            discountInput = state.discountInput,
            bundleDiscount = state.bundleDiscount,
            bundleLabels = state.appliedBundles.map { "${it.name} x${it.count}" },
            taxAmount = state.taxAmount,
            taxInclusive = state.taxInclusive,
            total = state.total,
            canCheckout = state.canCheckout,
            onDiscountInputChange = viewModel::setDiscountInput,
            onDiscountModeChange = viewModel::setDiscountMode,
            onCheckout = onCheckout
        )
    }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // Ubah jumlah manual (mis. dari 20 ke 10 tanpa klik berkali-kali).
    editingLine?.let { line ->
        QuantityDialog(
            productName = line.name,
            unitPrice = line.unitPrice,
            stock = line.stock,
            initialQuantity = line.quantity,
            title = "Ubah Jumlah",
            confirmLabel = "Simpan",
            onDismiss = { editingLine = null },
            onConfirm = { qty ->
                viewModel.setQuantity(line.productId, qty)
                editingLine = null
            }
        )
    }
}

@Composable
private fun CartItemRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onEditQuantity: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    line.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Formatters.rupiah(line.unitPrice)} · stok ${line.stock}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Formatters.rupiah(line.lineSubtotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            QtyStepper(
                quantity = line.quantity,
                onDecrement = onDecrement,
                onIncrement = onIncrement,
                onQuantityClick = onEditQuantity
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun QtyStepper(
    quantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onQuantityClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StepButton(Icons.Filled.Remove, "Kurangi", MaterialTheme.colorScheme.onSurfaceVariant, onDecrement)
        // Ketuk angka untuk mengubah jumlah secara manual.
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onQuantityClick)
                .widthIn(min = 40.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )
        StepButton(Icons.Filled.Add, "Tambah", MaterialTheme.colorScheme.primary, onIncrement)
    }
}

@Composable
private fun StepButton(icon: ImageVector, desc: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CheckoutSection(
    subtotal: Double,
    discount: Double,
    discountMode: DiscountMode,
    discountInput: Double,
    bundleDiscount: Double,
    bundleLabels: List<String>,
    taxAmount: Double,
    taxInclusive: Boolean,
    total: Double,
    canCheckout: Boolean,
    onDiscountInputChange: (Double) -> Unit,
    onDiscountModeChange: (DiscountMode) -> Unit,
    onCheckout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatters.rupiah(subtotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            DiscountField(
                mode = discountMode,
                input = discountInput,
                discount = discount,
                onInputChange = onDiscountInputChange,
                onModeChange = onDiscountModeChange
            )
            if (taxAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (taxInclusive) "PPN (termasuk)" else "PPN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.rupiah(taxAmount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    Formatters.rupiah(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (canCheckout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrimaryButton(
                text = "BAYAR",
                icon = Icons.Outlined.AccountBalanceWallet,
                modifier = Modifier.fillMaxWidth(),
                enabled = canCheckout,
                onClick = onCheckout
            )
        }
    }
}

/** Kotak diskon dengan toggle Rp/%, input angka, dan info potongan saat mode %. */
@Composable
private fun DiscountField(
    mode: DiscountMode,
    input: Double,
    discount: Double,
    onInputChange: (Double) -> Unit,
    onModeChange: (DiscountMode) -> Unit
) {
    var text by remember { mutableStateOf(formatDiscountInput(input)) }
    // Sinkronkan bila nilai direset dari luar (ganti mode / "Kosongkan").
    LaunchedEffect(input) {
        val expected = formatDiscountInput(input)
        if ((text.toDoubleOrNull() ?: 0.0) != input) text = expected
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Diskon", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(10.dp))
                DiscountModeToggle(mode = mode, onChange = onModeChange)
                Spacer(Modifier.weight(1f))
                BasicTextField(
                    value = text,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }
                        val limited = if (mode == DiscountMode.PERCENT)
                            (digits.toIntOrNull()?.coerceAtMost(100)?.toString() ?: "")
                        else digits.take(12)
                        text = limited
                        onInputChange(limited.toDoubleOrNull() ?: 0.0)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp)
                ) { inner ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (text.isEmpty()) {
                            Text(
                                "0",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
                if (mode == DiscountMode.PERCENT) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            val hint = when {
                mode == DiscountMode.PERCENT && discount > 0 -> "Potongan ${Formatters.rupiah(discount)}"
                mode == DiscountMode.RUPIAH && input > discount -> "Maks. ${Formatters.rupiah(discount)}"
                else -> null
            }
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DiscountModeToggle(mode: DiscountMode, onChange: (DiscountMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DiscountModeCell("Rp", mode == DiscountMode.RUPIAH) { onChange(DiscountMode.RUPIAH) }
        DiscountModeCell("%", mode == DiscountMode.PERCENT) { onChange(DiscountMode.PERCENT) }
    }
}

@Composable
private fun DiscountModeCell(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDiscountInput(value: Double): String =
    if (value > 0) value.toLong().toString() else ""
