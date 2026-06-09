package com.berdikariintigemilang.pos.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.remote.RefundItemRequest
import com.berdikariintigemilang.pos.data.remote.TransactionDto
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.EmptyState
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    viewModel: TransactionHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val refundTarget by viewModel.refundTarget.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var voidTarget by remember { mutableStateOf<TrxRow?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.items.size - 3 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Transaksi", style = MaterialTheme.typography.titleLarge) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> FullScreenLoading()
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Receipt,
                    title = "Belum ada transaksi",
                    subtitle = "Transaksi yang selesai akan muncul di sini"
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.offline) {
                        item { OfflineNotice() }
                    }
                    items(state.items, key = { it.ref }) { trx ->
                        // Aksi admin untuk transaksi tersinkron yang belum final:
                        // Kartu -> Void; QRIS/Tunai -> Refund.
                        val canAct = state.isAdmin && !state.offline && trx.serverId != null &&
                            trx.statusKind == TrxStatusKind.DONE
                        TransactionRow(
                            trx = trx,
                            showVoid = canAct && trx.paymentMethod == "CARD",
                            showRefund = canAct && trx.paymentMethod != "CARD",
                            onReprint = { onOpenReceipt(trx.ref) },
                            onVoid = { voidTarget = trx },
                            onRefund = { trx.serverId?.let { viewModel.startRefund(it) } }
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    voidTarget?.let { trx ->
        VoidDialog(
            trxNo = trx.trxNo,
            onDismiss = { voidTarget = null },
            onConfirm = { reason ->
                trx.serverId?.let { viewModel.void(it, reason) }
                voidTarget = null
            }
        )
    }

    refundTarget?.let { trx ->
        RefundDialog(
            transaction = trx,
            onDismiss = { viewModel.cancelRefund() },
            onConfirm = { full, reason, items -> viewModel.refund(trx.id, full, reason, items) }
        )
    }
}

@Composable
private fun OfflineNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.height(18.dp)
            )
            Text(
                "Mode offline · menampilkan transaksi dari HP ini",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(
    trx: TrxRow,
    showVoid: Boolean,
    showRefund: Boolean,
    onReprint: () -> Unit,
    onVoid: () -> Unit,
    onRefund: () -> Unit
) {
    AppCard(onClick = onReprint) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    trx.trxNo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                val (container, content) = statusColors(trx.statusKind)
                StatusChip(text = trx.statusLabel, container = container, content = content)
            }
            Text(
                trx.dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Kasir: ${trx.cashierName ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    Formatters.rupiah(trx.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onReprint) {
                        Text("Cetak Ulang", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    if (showRefund) {
                        TextButton(onClick = onRefund) {
                            Text("Refund", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (showVoid) {
                        TextButton(onClick = onVoid) {
                            Text("Void", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColors(kind: TrxStatusKind): Pair<Color, Color> = when (kind) {
    TrxStatusKind.DONE, TrxStatusKind.SYNCED ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    TrxStatusKind.PENDING, TrxStatusKind.REFUND ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    TrxStatusKind.CONFLICT, TrxStatusKind.VOID ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
}

@Composable
private fun VoidDialog(trxNo: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void $trxNo", style = MaterialTheme.typography.titleMedium) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Alasan void") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (reason.isNotBlank()) onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text("Void", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun RefundDialog(
    transaction: TransactionDto,
    onDismiss: () -> Unit,
    onConfirm: (full: Boolean, reason: String, items: List<RefundItemRequest>) -> Unit
) {
    val hasBundle = transaction.bundleDiscount > 0.0
    var reason by remember { mutableStateOf("") }
    val qty = remember {
        mutableStateMapOf<Long, Int>().apply { transaction.items.forEach { put(it.productId, 0) } }
    }
    val anySelected = qty.values.any { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Refund ${transaction.trxNo}", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (hasBundle) "Transaksi memakai bundle — hanya bisa Refund Penuh."
                    else "Atur jumlah unit yang di-refund, atau pilih Refund Penuh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasBundle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                transaction.items.forEach { item ->
                    val remaining = item.quantity - item.refundedQuantity
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                item.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "sisa $remaining dari ${item.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = (qty[item.productId] ?: 0).toString(),
                            onValueChange = { v ->
                                val n = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                                qty[item.productId] = n.coerceIn(0, remaining)
                            },
                            enabled = !hasBundle && remaining > 0,
                            singleLine = true,
                            modifier = Modifier.width(76.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan refund") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!hasBundle) {
                    TextButton(
                        onClick = { onConfirm(false, reason.trim(), qty.filter { it.value > 0 }.map { RefundItemRequest(it.key, it.value) }) },
                        enabled = reason.isNotBlank() && anySelected
                    ) { Text("Sebagian", color = MaterialTheme.colorScheme.secondary) }
                }
                TextButton(
                    onClick = { onConfirm(true, reason.trim(), emptyList()) },
                    enabled = reason.isNotBlank()
                ) { Text("Refund Penuh", color = MaterialTheme.colorScheme.primary) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}
