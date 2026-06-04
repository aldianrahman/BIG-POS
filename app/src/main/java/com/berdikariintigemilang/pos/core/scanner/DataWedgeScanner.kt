package com.berdikariintigemilang.pos.core.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Integrasi scanner hardware Zebra via DataWedge (mode Intent output).
 *
 * Saat dipakai (mis. halaman kasir tampil), app otomatis:
 *  1. Membuat/menyetel profil DataWedge khusus app ini — input BARCODE aktif,
 *     output INTENT broadcast ke [ACTION_SCAN] (kasir tidak perlu setup manual).
 *  2. Meneruskan string barcode hasil scan ke callback.
 *
 * Pada perangkat non-Zebra (tanpa DataWedge), broadcast konfigurasi diabaikan
 * sistem sehingga aman — tidak ada efek samping.
 */
object DataWedge {
    const val PROFILE_NAME = "BIGPOS"

    /** Action broadcast yang dipancarkan DataWedge berisi hasil scan. */
    const val ACTION_SCAN = "com.berdikariintigemilang.pos.SCAN"

    /** Extra berisi data barcode terdekode. */
    const val EXTRA_DATA_STRING = "com.symbol.datawedge.data_string"

    private const val DW_ACTION = "com.symbol.datawedge.api.ACTION"

    @Volatile
    private var configured = false

    /** Buat & konfigurasi profil DataWedge. Idempoten, cukup sekali per proses. */
    fun configureProfile(context: Context) {
        if (configured) return
        configured = true
        val app = context.applicationContext

        // 1) Buat profil (diabaikan bila sudah ada).
        app.sendBroadcast(
            Intent(DW_ACTION).putExtra("com.symbol.datawedge.api.CREATE_PROFILE", PROFILE_NAME)
        )

        // 2) Asosiasikan ke app ini, aktifkan BARCODE, dan keluarkan via INTENT broadcast.
        val barcode = Bundle().apply {
            putString("PLUGIN_NAME", "BARCODE")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply { putString("scanner_input_enabled", "true") })
        }
        val intentOut = Bundle().apply {
            putString("PLUGIN_NAME", "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("intent_output_enabled", "true")
                putString("intent_action", ACTION_SCAN)
                putString("intent_delivery", "2") // 2 = Broadcast
            })
        }
        val appConfig = Bundle().apply {
            putString("PACKAGE_NAME", app.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val config = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
            putParcelableArray("APP_LIST", arrayOf(appConfig))
            putParcelableArrayList("PLUGIN_CONFIG", arrayListOf(barcode, intentOut))
        }
        app.sendBroadcast(
            Intent(DW_ACTION).putExtra("com.symbol.datawedge.api.SET_CONFIG", config)
        )
    }
}

/**
 * Mengaktifkan penerimaan scan hardware Zebra selama composable ini ada di
 * komposisi. Memanggil [onScan] dengan string barcode setiap kali ada scan.
 */
@Composable
fun DataWedgeScanner(onScan: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnScan by rememberUpdatedState(onScan)

    DisposableEffect(Unit) {
        DataWedge.configureProfile(context)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == DataWedge.ACTION_SCAN) {
                    intent.getStringExtra(DataWedge.EXTRA_DATA_STRING)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { currentOnScan(it) }
                }
            }
        }
        // targetSdk 34: broadcast dari app lain (DataWedge) wajib RECEIVER_EXPORTED.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DataWedge.ACTION_SCAN),
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}
