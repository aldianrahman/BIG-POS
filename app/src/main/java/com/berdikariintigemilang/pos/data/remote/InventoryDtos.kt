package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StockDto(
    val productId: Long = 0,
    val sku: String = "",
    val name: String = "",
    val unit: String = "PCS",
    val quantity: Int = 0,
    val minStock: Int = 0,
    val lowStock: Boolean = false,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class StockAdjustmentRequest(
    val productId: Long,
    val quantity: Int,
    val notes: String
)
