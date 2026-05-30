package com.berdikariintigemilang.pos

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.berdikariintigemilang.pos.core.scanner.HardwareScannerBus
import com.berdikariintigemilang.pos.ui.navigation.PosNavGraph
import com.berdikariintigemilang.pos.ui.theme.PosTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scannerBus: HardwareScannerBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                PosNavGraph()
            }
        }
    }

    // Alat scan barcode fisik (HID) mengirim barcode sebagai ketikan keyboard
    // diakhiri Enter. Teruskan ke [scannerBus]; tombol biasa tetap diproses normal.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (scannerBus.feed(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
