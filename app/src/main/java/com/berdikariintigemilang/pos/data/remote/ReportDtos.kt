package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DashboardSummaryDto(
    val date: String = "",
    val totalSales: Double = 0.0,
    val totalTransactions: Int = 0,
    val totalProfit: Double = 0.0,
    val avgTransactionValue: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class SalesReportRowDto(
    val key: String = "",
    val label: String = "",
    val totalSales: Double = 0.0,
    val totalTransactions: Int = 0,
    val quantitySold: Int = 0
)

@JsonClass(generateAdapter = true)
data class ProfitReportDto(
    val revenue: Double = 0.0,
    val cogs: Double = 0.0,
    val profit: Double = 0.0,
    val marginPercent: Double = 0.0
)
