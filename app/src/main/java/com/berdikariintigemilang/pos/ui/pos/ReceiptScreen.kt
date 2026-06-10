package com.berdikariintigemilang.pos.ui.pos

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
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

    val total = remember(state.content) { extractReceiptTotal(state.content) }
    val payLabel = remember(state.content) { extractPaymentLabel(state.content) }
    val subtitle = if (total != null) "$payLabel · ${Formatters.rupiah(total)}" else payLabel

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!state.loading) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Column(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { doPrint() },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                enabled = !state.printing,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (state.printing) "Mencetak..." else "Cetak struk",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            PrimaryButton(
                                text = "SELESAI",
                                icon = Icons.Outlined.CheckCircle,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onDone
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header sukses ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Transaksi Berhasil",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // ── Struk (bingkai putus-putus, teks monospace) ─────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedReceiptBorder(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        cornerRadius = 20.dp
                    )
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.error != null) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = state.content,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Ambil nominal pada baris "TOTAL" dari teks struk (best-effort). */
private fun extractReceiptTotal(content: String): Long? {
    if (content.isBlank()) return null
    val line = content.lineSequence().firstOrNull { it.trimStart().uppercase().startsWith("TOTAL") }
        ?: return null
    val amount = line.trim().split(Regex("\\s+")).lastOrNull() ?: return null
    val digits = amount.filter { it.isDigit() }
    return digits.toLongOrNull()
}

/** Tentukan label metode bayar dari isi struk (best-effort) untuk subjudul. */
private fun extractPaymentLabel(content: String): String {
    val lines = content.lineSequence().map { it.trimStart() }.toList()
    return when {
        lines.any { it.startsWith("QRIS") } -> "Pembayaran QRIS"
        lines.any { it.startsWith("Kartu") } -> "Pembayaran Kartu"
        else -> "Pembayaran tunai"
    }
}

/** Bingkai garis putus-putus membulat ala kertas struk. */
private fun Modifier.dashedReceiptBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp
): Modifier = drawBehind {
    val sw = strokeWidth.toPx()
    val r = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(
            width = sw,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
        )
    )
}
