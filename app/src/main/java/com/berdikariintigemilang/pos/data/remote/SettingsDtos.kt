package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

/** Subset pengaturan struk yang dibutuhkan app (untuk hitung PPN agar total sinkron). */
@JsonClass(generateAdapter = true)
data class ReceiptSettingDto(
    val storeName: String = "",
    val address: String = "",
    val phone: String = "",
    val footer: String = "",
    val taxEnabled: Boolean = false,
    val taxPercent: Double = 0.0,
    val taxInclusive: Boolean = true
)

private fun round2(x: Double): Double = Math.round(x * 100.0) / 100.0

/** PPN dari nilai dasar (setelah diskon & bundle), cocok dgn perhitungan backend. */
fun ReceiptSettingDto?.taxFor(base: Double): Double {
    if (this == null || !taxEnabled || taxPercent <= 0.0) return 0.0
    val raw = if (taxInclusive) base * taxPercent / (100.0 + taxPercent) else base * taxPercent / 100.0
    return round2(raw)
}

/** Total yang ditagih: inclusive = base (tetap); exclusive = base + PPN. */
fun ReceiptSettingDto?.totalFor(base: Double): Double =
    if (this != null && taxEnabled && taxPercent > 0.0 && !taxInclusive) round2(base + taxFor(base)) else base
