package com.berdikariintigemilang.pos.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.berdikariintigemilang.pos.ui.components.ScreenHeader
import com.berdikariintigemilang.pos.ui.components.SectionLabel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onCloseShift: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    onTransactions: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showPickerDialog by remember { mutableStateOf(false) }

    val needsBtPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val btPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    )
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(btPermissions.allPermissionsGranted) {
        if (btPermissions.allPermissionsGranted) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    fun withBt(action: () -> Unit) {
        if (!needsBtPermission || btPermissions.allPermissionsGranted) action()
        else { pendingAction = action; btPermissions.launchMultiplePermissionRequest() }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Pengaturan")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ProfileCard(
                    name = state.user?.fullName ?: "-",
                    username = state.user?.username ?: "-",
                    roles = state.user?.roles?.map { it.removePrefix("ROLE_") } ?: emptyList()
                )

                // ── Perangkat ───────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Perangkat")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Filled.Print,
                            title = "Pilih Printer (Bluetooth)",
                            subtitle = state.savedPrinter?.let { "${it.name} · ${it.address}" }
                                ?: "Belum ada printer dipilih",
                            subtitleColor = if (state.savedPrinter != null)
                                MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { withBt { viewModel.loadPairedDevices(); showPickerDialog = true } }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        SettingsRow(
                            icon = Icons.Filled.Bluetooth,
                            title = if (state.printing) "Mencetak..." else "Test Print",
                            subtitle = "Cetak halaman uji ke printer terpilih",
                            enabled = state.savedPrinter != null && !state.printing,
                            onClick = { withBt { viewModel.testPrint() } }
                        )
                    }
                }

                // ── Transaksi ───────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Transaksi")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Filled.History,
                            title = "Riwayat Transaksi & Cetak Ulang",
                            subtitle = "Lihat & cetak ulang transaksi lama",
                            onClick = onTransactions
                        )
                    }
                }

                // ── Sinkronisasi ────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Sinkronisasi Data")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Filled.Sync,
                            title = if (state.syncing) "Menyinkronkan..." else "Sinkron Sekarang",
                            subtitle = if (state.isOnline)
                                (if (state.pendingCount > 0) "${state.pendingCount} transaksi menunggu kirim"
                                else "Semua transaksi tersinkron")
                            else "Mode offline · ${state.pendingCount} transaksi menunggu",
                            subtitleColor = if (state.pendingCount > 0) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            enabled = !state.syncing,
                            onClick = viewModel::syncNow
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        SettingsRow(
                            icon = Icons.Filled.CloudDownload,
                            title = if (state.refreshingCatalog) "Memperbarui..." else "Perbarui Katalog & Stok",
                            subtitle = "Unduh produk & stok terbaru untuk persiapan offline",
                            enabled = !state.refreshingCatalog,
                            onClick = viewModel::refreshCatalog
                        )
                    }
                }

                // ── Shift ───────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Shift")
                    val shiftId = state.currentShiftId
                    PrimaryButton(
                        text = "TUTUP SHIFT",
                        modifier = Modifier.fillMaxWidth(),
                        loading = state.loadingShift,
                        enabled = shiftId != null && !state.loadingShift,
                        icon = Icons.Filled.PointOfSale,
                        onClick = { shiftId?.let(onCloseShift) }
                    )
                }

                // ── Akun ────────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Akun")
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = "Logout",
                            titleColor = MaterialTheme.colorScheme.error,
                            iconTint = MaterialTheme.colorScheme.error,
                            iconBackground = MaterialTheme.colorScheme.errorContainer,
                            onClick = { showLogoutConfirm = true }
                        )
                    }
                }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPickerDialog = false },
            title = { Text("Pilih Printer Ter-pair") },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (state.loadingDevices) {
                        Text("Memuat perangkat...")
                    } else if (state.pairedDevices.isEmpty()) {
                        Text("Tidak ada perangkat ter-pair. Pair printer dulu lewat Setting Bluetooth Android, lalu coba lagi.")
                    } else {
                        state.pairedDevices.forEach { device ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectPrinter(device)
                                        showPickerDialog = false
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                Text(device.address, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPickerDialog = false }) { Text("Tutup") } }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout") },
            text = { Text("Yakin ingin keluar? Shift yang sedang berjalan tidak akan ditutup otomatis.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout(onLoggedOut)
                }) { Text("Logout", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Batal") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(name: String, username: String, roles: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("@$username", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (roles.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roles.forEach { RoleChip(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (enabled) titleColor else MaterialTheme.colorScheme.outline)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
    }
}
