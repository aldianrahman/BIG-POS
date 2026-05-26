package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransactionItemRequest(
    val productId: Long,
    val quantity: Int,
    val discountAmount: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class TransactionRequest(
    val items: List<TransactionItemRequest>,
    val discountAmount: Double = 0.0,
    val cashReceived: Double,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class VoidRequest(val reason: String)

/** Bentuk Spring Page (field "number" untuk halaman). */
@JsonClass(generateAdapter = true)
data class TrxPageDto<T>(
    val content: List<T> = emptyList(),
    val number: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0
)

@JsonClass(generateAdapter = true)
data class TransactionItemDto(
    val id: Long = 0,
    val productId: Long = 0,
    val productSku: String = "",
    val productName: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,
    val discountAmount: Double = 0.0,
    val subtotal: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class TransactionDto(
    val id: Long = 0,
    val trxNo: String = "",
    val shiftId: Long = 0,
    val userId: Long = 0,
    val cashierName: String? = null,
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val cashReceived: Double = 0.0,
    val changeAmount: Double = 0.0,
    val status: String = "COMPLETED",
    val notes: String? = null,
    val createdAt: String? = null,
    val items: List<TransactionItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReceiptDto(
    val trxNo: String = "",
    val content: String = ""
)
