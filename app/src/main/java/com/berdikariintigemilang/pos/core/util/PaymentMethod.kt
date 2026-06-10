package com.berdikariintigemilang.pos.core.util

/** Metode pembayaran transaksi POS. [apiValue] = nilai yang dikirim ke server. */
enum class PaymentMethod(val apiValue: String, val label: String, val isCash: Boolean) {
    CASH("CASH", "Tunai", true),
    QRIS("QRIS", "QRIS", false),
    CARD("CARD", "Kartu", false);

    companion object {
        fun fromApi(value: String?): PaymentMethod =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) } ?: CASH
    }
}
