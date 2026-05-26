package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProductDto(
    val id: Long = 0,
    val sku: String = "",
    val barcode: String? = null,
    val name: String = "",
    val description: String? = null,
    val categoryId: Long = 0,
    val categoryName: String? = null,
    val brand: String = "LUBY",
    val unit: String = "PCS",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val isActive: Boolean = true,
    val stockQuantity: Int? = null,
    val minStock: Int? = null
)

@JsonClass(generateAdapter = true)
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0
)

@JsonClass(generateAdapter = true)
data class ProductRequest(
    val sku: String,
    val barcode: String? = null,
    val name: String,
    val description: String? = null,
    val categoryId: Long,
    val brand: String = "LUBY",
    val unit: String = "PCS",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val minStock: Int? = null,
    val initialStock: Int? = null
)
