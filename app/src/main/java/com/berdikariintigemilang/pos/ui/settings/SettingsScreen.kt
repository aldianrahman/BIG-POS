package com.berdikariintigemilang.pos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onCloseShift: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
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

        OutlinedButton(
            onClick = { /* Printer pairing tersedia di Task 6 */ },
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Icon(Icons.Filled.Print, contentDescription = null)
            Text("  Printer Thermal (Task 6)")
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
