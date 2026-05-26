package com.berdikariintigemilang.pos.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onCloseShift: (Long) -> Unit,
    onLoggedOut: () -> Unit,
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

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.user?.fullName ?: "-", style = MaterialTheme.typography.titleLarge)
                    Text("@${state.user?.username ?: "-"}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.user?.roles?.joinToString(", ") { it.removePrefix("ROLE_") } ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ===== Printer thermal =====
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Printer Thermal", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.savedPrinter?.let { "Terpilih: ${it.name}\n${it.address}" }
                            ?: "Belum ada printer dipilih",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.savedPrinter != null) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outline
                    )
                    OutlinedButton(
                        onClick = { withBt { viewModel.loadPairedDevices(); showPickerDialog = true } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null)
                        Text("  Pilih Printer (Bluetooth)")
                    }
                    Button(
                        onClick = { withBt { viewModel.testPrint() } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.savedPrinter != null && !state.printing
                    ) {
                        Text(if (state.printing) "Mencetak..." else "Test Print")
                    }
                }
            }

            val shiftId = state.currentShiftId
            Button(
                onClick = { shiftId?.let(onCloseShift) },
                modifier = Modifier.fillMaxWidth(),
                enabled = shiftId != null && !state.loadingShift
            ) {
                Icon(Icons.Filled.PointOfSale, contentDescription = null)
                Text("  Tutup Shift")
            }

            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, tint = Color.White)
                Text("  Logout", color = Color.White)
            }
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
                }) { Text("Logout") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Batal") } }
        )
    }
}
