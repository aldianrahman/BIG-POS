package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

/** Amplop response standar endpoint /api/pos. */
@JsonClass(generateAdapter = true)
data class PosApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
    val error: PosApiError? = null
)

@JsonClass(generateAdapter = true)
data class PosApiError(
    val code: String? = null,
    val message: String? = null
)
