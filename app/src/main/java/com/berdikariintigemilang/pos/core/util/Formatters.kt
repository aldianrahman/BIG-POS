package com.berdikariintigemilang.pos.core.util

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val idLocale = Locale("id", "ID")
    private val displayDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /** Format Rupiah tanpa desimal, mis. "Rp 28.000". */
    fun rupiah(value: Number?): String {
        val nf = NumberFormat.getCurrencyInstance(idLocale)
        nf.maximumFractionDigits = 0
        val s = nf.format((value ?: 0).toDouble())
        // NumberFormat id-ID memberi "Rp28.000"; rapikan jadi "Rp 28.000".
        return s.replace("Rp", "Rp ").replace("  ", " ").trim()
    }

    fun number(value: Number?): String =
        NumberFormat.getNumberInstance(idLocale).format((value ?: 0).toLong())

    /** Format persen ala id-ID, maksimal 2 desimal, mis. "40,27%" atau "40%". */
    fun percent(value: Number?): String {
        val nf = NumberFormat.getNumberInstance(idLocale)
        nf.maximumFractionDigits = 2
        nf.minimumFractionDigits = 0
        return nf.format((value ?: 0).toDouble()) + "%"
    }

    /** Parse ISO-8601 (tanpa zona) dari backend ke tampilan dd/MM/yyyy HH:mm. */
    fun displayDateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "-"
        return try {
            LocalDateTime.parse(iso).format(displayDateFmt)
        } catch (e: Exception) {
            iso
        }
    }

    /** Format epoch millis (jam HP) ke tampilan dd/MM/yyyy HH:mm. */
    fun displayDateTime(millis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
            .format(displayDateFmt)
}
