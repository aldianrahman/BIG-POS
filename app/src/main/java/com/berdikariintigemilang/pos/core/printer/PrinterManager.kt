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

    /**
     * Cetak struk transaksi gantung: teks [content] diikuti QR berisi [qrContent]
     * yang dipindai di kasir untuk melanjutkan transaksi.
     */
    suspend fun printHoldTicket(address: String, content: String, qrContent: String) =
        withContext(Dispatchers.IO) { connectAndPrintHold(address, content, qrContent) }

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

    @SuppressLint("MissingPermission")
    private fun connectAndPrintHold(address: String, content: String, qrContent: String) {
        val a = adapter ?: throw PrinterException("Perangkat tidak mendukung Bluetooth")
        if (!a.isEnabled) throw PrinterException("Bluetooth belum aktif")
        val device = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw PrinterException("Alamat printer tidak valid")
        }
        try {
            val connection = BluetoothConnection(device)
            val printer = EscPosPrinter(connection, 203, 48f, 32)
            // QR di-render printer sebagai gambar (di tengah), di bawah daftar item.
            val formatted = bodyToEscPos(content) +
                "\n[C]<qrcode size='25'>$qrContent</qrcode>\n[L]\n[L]\n"
            printer.printFormattedText(formatted)
            printer.disconnectPrinter()
        } catch (e: PrinterException) {
            throw e
        } catch (e: Exception) {
            throw PrinterException("Gagal mencetak: ${e.message ?: "koneksi printer terputus"}")
        }
    }

    /** Ubah teks struk polos menjadi format DantSu (rata kiri, monospace) + feed. */
    private fun toEscPos(text: String): String = bodyToEscPos(text) + "\n[L]\n[L]\n"

    /** Map tiap baris teks ke baris rata-kiri DantSu, tanpa feed tambahan. */
    private fun bodyToEscPos(text: String): String =
        text.replace("\r\n", "\n").trimEnd('\n')
            .split("\n")
            .joinToString("\n") { "[L]" + it }
}
