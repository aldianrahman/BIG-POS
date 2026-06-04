package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BundleCalcItem(
    val productId: Long,
    val quantity: Int
)

@JsonClass(generateAdapter = true)
data class BundleCalcRequest(
    val items: List<BundleCalcItem>
)

@JsonClass(generateAdapter = true)
data class AppliedBundleDto(
    val bundleId: Long? = null,
    val name: String = "",
    val count: Int = 0,
    val discountAmount: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class BundleCalcResult(
    val bundleDiscount: Double = 0.0,
    val appliedBundles: List<AppliedBundleDto> = emptyList()
)

/** Item penyusun bundle (untuk di-cache & dihitung offline). */
@JsonClass(generateAdapter = true)
data class BundleListItemDto(
    val productId: Long = 0,
    val quantity: Int = 0
)

/** Definisi bundle dari GET /api/pos/bundles (di-cache untuk perhitungan offline). */
@JsonClass(generateAdapter = true)
data class BundleListDto(
    val id: Long = 0,
    val name: String = "",
    val bundlePrice: Double = 0.0,
    val isActive: Boolean = true,
    val startDate: String? = null,
    val endDate: String? = null,
    val items: List<BundleListItemDto> = emptyList()
)
