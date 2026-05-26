package com.berdikariintigemilang.pos.ui.pos

import android.Manifest
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ReceiptScreen(
    onDone: () -> Unit,
    viewModel: ReceiptViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val needsBtPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val btPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    )
    var pendingPrint by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(btPermissions.allPermissionsGranted) {
        if (btPermissions.allPermissionsGranted && pendingPrint) {
            pendingPrint = false
            viewModel.print()
        }
    }

    fun doPrint() {
        if (!needsBtPermission || btPermissions.allPermissionsGranted) viewModel.print()
        else { pendingPrint = true; btPermissions.launchMultiplePermissionRequest() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Transaksi Berhasil",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                } else {
                    Surface(color = Color(0xFF101418), modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = state.content,
                            color = Color(0xFFB9F6CA),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { doPrint() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.printing
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Text(
                    when {
                        state.printing -> "  Mencetak..."
                        !state.hasPrinter -> "  Cetak (atur printer di Pengaturan)"
                        else -> "  Cetak Struk"
                    }
                )
            }

            PrimaryButton(text = "SELESAI", modifier = Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}
