package com.berdikariintigemilang.pos.core.scanner

import android.view.KeyEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menangkap input dari alat scan barcode fisik yang bekerja sebagai keyboard
 * (HID), mis. VSC BT-395S. Scanner "mengetik" karakter barcode dengan sangat
 * cepat lalu menekan Enter; karakter dikumpulkan di sini dan dipancarkan sebagai
 * satu barcode utuh begitu Enter diterima.
 *
 * Di-feed dari [com.berdikariintigemilang.pos.MainActivity.dispatchKeyEvent].
 * Karakter biasa TIDAK dikonsumsi (agar field teks lain tetap berfungsi); hanya
 * Enter penutup dari sebuah rentetan yang dikenali sebagai scan yang ditelan.
 *
 * Rentetan dianggap berasal dari scanner (bukan ketikan manusia) bila seluruh
 * karakter masuk dalam waktu sangat singkat — lihat [MAX_SCAN_DURATION_MS].
 */
@Singleton
class HardwareScannerBus @Inject constructor() {

    private val _barcodes = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Barcode utuh hasil scan. Tanpa replay: hanya kolektor aktif yang menerima. */
    val barcodes: SharedFlow<String> = _barcodes.asSharedFlow()

    private val buffer = StringBuilder()
    private var bufferStartTime = 0L
    private var lastKeyTime = 0L

    /**
     * Proses satu [KeyEvent].
     *
     * @return true bila event ini adalah Enter penutup dari scan yang berhasil
     * dirakit (pemanggil harus mengonsumsinya); false untuk event lainnya.
     */
    fun feed(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val now = event.eventTime

        if (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            val code = buffer.toString()
            val duration = lastKeyTime - bufferStartTime
            buffer.setLength(0)
            return if (code.length >= MIN_LENGTH && duration <= MAX_SCAN_DURATION_MS) {
                _barcodes.tryEmit(code)
                true
            } else {
                false
            }
        }

        val ch = event.unicodeChar
        if (ch != 0) {
            // Mulai buffer baru bila ini karakter pertama atau jeda dari karakter
            // sebelumnya terlalu lama (mis. ketikan manusia / tombol nyasar).
            if (buffer.isEmpty() || now - lastKeyTime > RESET_GAP_MS) {
                buffer.setLength(0)
                bufferStartTime = now
            }
            buffer.append(ch.toChar())
            lastKeyTime = now
        }
        return false
    }

    private companion object {
        /** Panjang minimal agar sebuah rentetan dianggap barcode. */
        const val MIN_LENGTH = 3

        /** Jeda maksimal antar karakter; lebih dari ini dianggap rentetan baru. */
        const val RESET_GAP_MS = 200L

        /** Durasi total maksimal satu rentetan agar dianggap berasal dari scanner. */
        const val MAX_SCAN_DURATION_MS = 600L
    }
}
