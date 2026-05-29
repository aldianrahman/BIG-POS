package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MeDto(
    val id: Long = 0,
    val username: String = "",
    val fullName: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val isAdmin: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ShiftDto(
    val id: Long = 0,
    val userId: Long = 0,
    val cashierName: String? = null,
    val openingCash: Double = 0.0,
    val closingCash: Double? = null,
    val expectedCash: Double? = null,
    val cashDifference: Double? = null,
    val totalSales: Double = 0.0,
    val totalTransactions: Int = 0,
    val status: String = "OPEN",
    val openedAt: String? = null,
    val closedAt: String? = null,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenShiftRequest(val openingCash: Double)

@JsonClass(generateAdapter = true)
data class CloseShiftRequest(val closingCash: Double, val notes: String? = null)

@JsonClass(generateAdapter = true)
data class CashMovementDto(
    val id: Long = 0,
    val shiftId: Long = 0,
    val movementType: String = "CASH_IN",
    val amount: Double = 0.0,
    val notes: String = "",
    val userId: Long = 0,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TopProductDto(
    val productId: Long = 0,
    val sku: String = "",
    val name: String = "",
    val quantitySold: Int = 0,
    val totalSales: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class ZReportDto(
    val shift: ShiftDto? = null,
    val openingCash: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalTransactions: Int = 0,
    val totalCashIn: Double = 0.0,
    val totalCashOut: Double = 0.0,
    val expectedCash: Double = 0.0,
    val closingCash: Double? = null,
    val cashDifference: Double? = null,
    val cashMovements: List<CashMovementDto> = emptyList(),
    val topProducts: List<TopProductDto> = emptyList()
)
