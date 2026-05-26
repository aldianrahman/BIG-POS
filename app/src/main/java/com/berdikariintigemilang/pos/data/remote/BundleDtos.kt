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
