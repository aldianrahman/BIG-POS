package com.berdikariintigemilang.pos.core.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.berdikariintigemilang.pos.core.datastore.SavedPrinter
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class PrinterException(message: String) : RuntimeException(message)

/**
 * Pembungkus printer thermal Bluetooth ESC/POS (58mm, 32 kolom).
 * Memakai daftar perangkat yang sudah di-pair lewat setting Android.
 */
@Singleton
class PrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val adapter
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hasBtPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun pairedPrinters(): List<SavedPrinter> {
        val a = adapter ?: throw PrinterException("Perangkat tidak mendukung Bluetooth")
        if (!a.isEnabled) throw PrinterException("Bluetooth belum aktif")
        return a.bondedDevices.orEmpty().map { SavedPrinter(name = it.name ?: it.address, address = it.address) }
    }

    suspend fun printReceipt(address: String, content: String) = withContext(Dispatchers.IO) {
        connectAndPrint(address, content)
    }

    suspend fun testPrint(address: String) = withContext(Dispatchers.IO) {
        val sample = buildString {
            appendLine("        BIG GROUP - LUBY")
            appendLine("--------------------------------")
            appendLine("Test Print Berhasil")
            appendLine("Printer 58mm siap digunakan")
            appendLine("================================")
        }
        connectAndPrint(address, sample)
    }

    @SuppressLint("MissingPermission")
    private fun connectAndPrint(address: String, content: String) {
        val a = adapter ?: throw PrinterException("Perangkat tidak mendukung Bluetooth")
        if (!a.isEnabled) throw PrinterException("Bluetooth belum aktif")
        val device = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw PrinterException("Alamat printer tidak valid")
        }
        try {
            val connection = BluetoothConnection(device)
            // 203 dpi, lebar cetak 48mm (kertas 58mm), 32 karakter per baris.
            val printer = EscPosPrinter(connection, 203, 48f, 32)
            printer.printFormattedText(toEscPos(content))
            printer.disconnectPrinter()
        } catch (e: PrinterException) {
            throw e
        } catch (e: Exception) {
            throw PrinterException("Gagal mencetak: ${e.message ?: "koneksi printer terputus"}")
        }
    }

    /** Ubah teks struk polos menjadi format DantSu (rata kiri, monospace). */
    private fun toEscPos(text: String): String {
        val body = text.replace("\r\n", "\n").trimEnd('\n')
            .split("\n")
            .joinToString("\n") { "[L]" + it }
        // Tambah feed agar mudah disobek.
        return "$body\n[L]\n[L]\n"
    }
}
