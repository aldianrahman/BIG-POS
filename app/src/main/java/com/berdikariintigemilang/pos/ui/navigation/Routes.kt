package com.berdikariintigemilang.pos.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SHIFT_OPEN = "shift_open"
    const val MAIN = "main"
    const val SHIFT_CLOSE = "shift_close/{shiftId}"
    const val Z_REPORT = "z_report/{shiftId}"

    const val SCAN = "scan"
    const val PRODUCT_SEARCH = "product_search"
    const val PAYMENT = "payment"
    const val RECEIPT = "receipt/{trxId}"
    const val PRODUCT_DETAIL = "product_detail/{productId}"
    const val PRODUCT_EDIT = "product_edit/{productId}"
    const val TRANSACTIONS = "transactions"
    const val HELD_SALES = "held_sales"

    fun shiftClose(shiftId: Long) = "shift_close/$shiftId"
    fun zReport(shiftId: Long) = "z_report/$shiftId"
    fun receipt(trxId: Long) = "receipt/$trxId"
    fun receipt(clientTxnId: String) = "receipt/$clientTxnId"
    fun productDetail(productId: Long) = "product_detail/$productId"
    fun productEdit(productId: Long) = "product_edit/$productId"
}

/** Tab di bottom navigation MainScreen. */
enum class MainTab(val route: String, val label: String) {
    POS("tab_pos", "Kasir"),
    INVENTORY("tab_inventory", "Stok"),
    DASHBOARD("tab_dashboard", "Dashboard"),
    REPORTS("tab_reports", "Laporan"),
    SETTINGS("tab_settings", "Pengaturan")
}
