package com.berdikariintigemilang.pos.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.ui.components.AppCard
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.berdikariintigemilang.pos.ui.components.SectionTitle
import com.berdikariintigemilang.pos.ui.components.StatusChip
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
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
    // Android 12+: connect butuh BLUETOOTH_CONNECT, dan cancelDiscovery saat
    // menyambung printer butuh BLUETOOTH_SCAN.
    val btPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    )
    // Aksi yang menunggu izin BT diberikan.
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Profil pengguna ──────────────────────────────────────────────
            AppCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            state.user?.fullName ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "@${state.user?.username ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val roles = state.user?.roles?.joinToString(", ") { it.removePrefix("ROLE_") } ?: ""
                        if (roles.isNotBlank()) {
                            StatusChip(
                                text = roles,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ── Perangkat ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(
                    title = "Perangkat",
                    icon = Icons.Filled.Bluetooth
                )
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        SettingsRow(
                            icon = Icons.Filled.Print,
                            iconTint = MaterialTheme.colorScheme.primary,
                            iconBackground = MaterialTheme.colorScheme.primaryContainer,
                            title = "Pilih Printer (Bluetooth)",
                            subtitle = state.savedPrinter?.let { "${it.name}  •  ${it.address}" }
                                ?: "Belum ada printer dipilih",
                            subtitleColor = if (state.savedPrinter != null)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.outline,
                            onClick = { withBt { viewModel.loadPairedDevices(); showPickerDialog = true } }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                        SettingsRow(
                            icon = Icons.Filled.Print,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                            title = if (state.printing) "Mencetak..." else "Test Print",
                            subtitle = "Cetak halaman uji ke printer terpilih",
                            enabled = state.savedPrinter != null && !state.printing,
                            onClick = { withBt { viewModel.testPrint() } }
                        )
                    }
                }
            }

            // ── Transaksi ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(title = "Transaksi", icon = Icons.Filled.Receipt)
                AppCard(onClick = onTransactions) {
                    SettingsRowContent(
                        icon = Icons.Filled.Receipt,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                        title = "Riwayat Transaksi & Cetak Ulang",
                        subtitle = "Lihat dan cetak ulang transaksi lama"
                    )
                }
            }

            // ── Shift ────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(title = "Shift", icon = Icons.Filled.PointOfSale)
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

            // ── Akun ─────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(title = "Akun")
                AppCard(onClick = { showLogoutConfirm = true }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            "Logout",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
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

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconBackground: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsRowContent(
            icon = icon,
            iconTint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
            iconBackground = if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant,
            title = title,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            showChevron = true
        )
    }
}

@Composable
private fun SettingsRowContent(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconBackground: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
            }
        }
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
