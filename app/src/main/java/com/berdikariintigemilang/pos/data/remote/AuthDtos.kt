package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

/** Response dari /api/auth/signin (format big-tracker, bukan PosApiResponse). */
@JsonClass(generateAdapter = true)
data class LoginResponse(
    val accessToken: String,
    val tokenType: String? = "Bearer",
    val id: Long = 0,
    val username: String = "",
    val email: String? = null,
    val roles: List<String> = emptyList()
)
