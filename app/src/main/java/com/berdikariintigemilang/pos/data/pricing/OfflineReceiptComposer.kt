package com.berdikariintigemilang.pos.data.pricing

import com.berdikariintigemilang.pos.data.local.CachedReceiptSettingEntity
import com.berdikariintigemilang.pos.data.remote.AppliedBundleDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/** Satu baris item pada struk. */
data class ReceiptLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double
)

/** Data lengkap untuk menyusun struk offline. */
data class ReceiptData(
    val trxNo: String,
    val dateMillis: Long,
    val cashierName: String?,
    val lines: List<ReceiptLine>,
    val subtotal: Double,
    val discount: Double,
    val bundleDiscount: Double,
    val appliedBundles: List<AppliedBundleDto>,
    val taxAmount: Double,
    val taxInclusive: Boolean,
    val total: Double,
    val cashReceived: Double,
    val change: Double,
    val pendingSync: Boolean = true
)

/**
 * Menyusun teks struk 58mm (32 kolom) untuk dicetak saat offline.
 * Format direplikasi persis dari PosReceiptFormatter di backend agar struk
 * offline identik dengan struk yang dihasilkan server (termasuk pemakaian
 * pemisah ribuan ala Locale.US "%,d").
 */
@Singleton
class OfflineReceiptComposer @Inject constructor() {

    private val width = 32
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun compose(data: ReceiptData, setting: CachedReceiptSettingEntity?): String {
        val sb = StringBuilder()
        val storeName = setting?.storeName?.takeIf { it.isNotBlank() } ?: "STRUK"
        sb.appendLine(center(storeName))
        setting?.address?.takeIf { it.isNotBlank() }?.let { sb.appendLine(center(it)) }
        setting?.phone?.takeIf { it.isNotBlank() }?.let { sb.appendLine(center(it)) }
        sb.appendLine(sep())
        sb.appendLine("No   : ${data.trxNo}")
        sb.appendLine("Tgl  : ${formatDate(data.dateMillis)}")
        sb.appendLine("Kasir: ${data.cashierName ?: "-"}")
        if (data.pendingSync) sb.appendLine(center("(BELUM SINKRON)"))
        sb.appendLine(sep())
        for (item in data.lines) {
            sb.appendLine(truncate(item.name))
            val qtyPrice = "  ${item.quantity} x ${money(item.unitPrice)}"
            sb.appendLine(leftRight(qtyPrice, money(item.subtotal)))
        }
        sb.appendLine(sep())
        sb.appendLine(leftRight("Subtotal", money(data.subtotal)))
        sb.appendLine(leftRight("Diskon", money(data.discount)))
        if (data.bundleDiscount > 0.0) {
            data.appliedBundles.forEach { b ->
                sb.appendLine(truncate("Bundle: ${b.name} x${b.count}"))
                sb.appendLine(leftRight("  Potongan", "-" + money(b.discountAmount)))
            }
        }
        if (data.taxAmount > 0.0) {
            sb.appendLine(leftRight(if (data.taxInclusive) "PPN (termasuk)" else "PPN", money(data.taxAmount)))
        }
        sb.appendLine(sep())
        sb.appendLine(leftRight("TOTAL", money(data.total)))
        sb.appendLine(leftRight("Tunai", money(data.cashReceived)))
        sb.appendLine(leftRight("Kembali", money(data.change)))
        sb.appendLine(sep())
        setting?.footer?.replace("|", "\n")?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { sb.appendLine(center(it)) }
        sb.appendLine("=".repeat(width))
        return sb.toString()
    }

    private fun formatDate(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(dateFmt)

    private fun sep() = "-".repeat(width)

    private fun center(text: String): String {
        val t = truncate(text)
        if (t.length >= width) return t
        val pad = (width - t.length) / 2
        return " ".repeat(pad) + t
    }

    private fun leftRight(left: String, right: String): String {
        val l = if (left.length > width - 1) left.substring(0, width - 1) else left
        val space = width - l.length - right.length
        return if (space < 1) "$l $right" else l + " ".repeat(space) + right
    }

    private fun truncate(text: String): String =
        if (text.length > width) text.substring(0, width) else text

    private fun money(value: Double): String =
        String.format(Locale.US, "%,d", value.roundToLong())
}
