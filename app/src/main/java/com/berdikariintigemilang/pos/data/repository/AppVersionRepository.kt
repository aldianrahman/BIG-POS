package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.BuildConfig
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.core.util.Constants
import com.berdikariintigemilang.pos.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/** Hasil pengecekan versi aplikasi terhadap versi minimum di server. */
sealed interface VersionCheck {
    /** Versi aplikasi memenuhi (>= versi server), atau cek gagal (fail-open). */
    data object Ok : VersionCheck

    /** Versi aplikasi di bawah versi server — login harus ditahan. */
    data class Outdated(val current: String, val required: String) : VersionCheck
}

@Singleton
class AppVersionRepository @Inject constructor(
    private val api: ApiService
) {
    /**
     * Bandingkan [BuildConfig.VERSION_CODE] dengan versi minimum di server.
     *
     * FAIL-OPEN: bila server tidak terjangkau / respons tidak valid / versi tak
     * bisa diparse, kembalikan [VersionCheck.Ok] agar kasir tetap bisa login dan
     * bekerja offline. Login hanya ditahan bila benar-benar diketahui bahwa
     * versi aplikasi < versi server.
     */
    suspend fun check(): VersionCheck {
        val serverVersion = when (val res = safePosCall { api.appVersion(Constants.APP_VERSION_ID) }) {
            is ApiResult.Success -> res.data.versionApp?.trim().orEmpty()
            is ApiResult.Error -> return VersionCheck.Ok
        }
        val required = serverVersion.toIntOrNull() ?: return VersionCheck.Ok
        return if (BuildConfig.VERSION_CODE < required) {
            VersionCheck.Outdated(current = BuildConfig.VERSION_NAME, required = serverVersion)
        } else {
            VersionCheck.Ok
        }
    }
}
