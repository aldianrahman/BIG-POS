package com.berdikariintigemilang.pos.data.cart

/**
 * Skema isi QR pada struk transaksi gantung. QR berisi id transaksi gantung
 * dengan awalan penanda, agar mudah dibedakan dari barcode produk biasa saat
 * discan di halaman kasir.
 */
object HoldQr {
    private const val PREFIX = "BIGPOS:HOLD:"

    fun encode(id: String): String = PREFIX + id

    /** Kembalikan id transaksi gantung bila [raw] adalah QR struk gantung; selain itu null. */
    fun parse(raw: String): String? {
        val s = raw.trim()
        return if (s.startsWith(PREFIX)) s.removePrefix(PREFIX).takeIf { it.isNotBlank() } else null
    }
}
