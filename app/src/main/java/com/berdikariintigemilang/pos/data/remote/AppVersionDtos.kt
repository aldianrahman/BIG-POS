package com.berdikariintigemilang.pos.data.remote

import com.squareup.moshi.JsonClass

/** Data versi aplikasi dari server (GET api/v1/app-versions/{id}). */
@JsonClass(generateAdapter = true)
data class AppVersionDto(
    val id: Long? = null,
    val appName: String? = null,
    /** Versi minimum yang didukung server, mis. "1". */
    val versionApp: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
