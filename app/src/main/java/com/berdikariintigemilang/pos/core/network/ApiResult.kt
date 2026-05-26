package com.berdikariintigemilang.pos.core.network

import com.berdikariintigemilang.pos.data.remote.PosApiResponse
import retrofit2.HttpException
import java.io.IOException

/** Hasil pemanggilan API yang aman dipakai di ViewModel. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: String? = null, val httpStatus: Int? = null) : ApiResult<Nothing>
}

/** Bungkus pemanggilan endpoint POS yang mengembalikan PosApiResponse. */
suspend fun <T> safePosCall(block: suspend () -> PosApiResponse<T>): ApiResult<T> = try {
    val res = block()
    if (res.success && res.data != null) {
        ApiResult.Success(res.data)
    } else {
        ApiResult.Error(res.error?.message ?: res.message ?: "Terjadi kesalahan", res.error?.code)
    }
} catch (e: HttpException) {
    ApiResult.Error(mapHttpMessage(e.code()), httpStatus = e.code())
} catch (e: IOException) {
    ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
} catch (e: Exception) {
    ApiResult.Error(e.message ?: "Kesalahan tidak diketahui")
}

/** Untuk endpoint POS yang body data-nya boleh null (mis. shift current). */
suspend fun <T> safePosCallNullable(block: suspend () -> PosApiResponse<T>): ApiResult<T?> = try {
    val res = block()
    if (res.success) ApiResult.Success(res.data)
    else ApiResult.Error(res.error?.message ?: res.message ?: "Terjadi kesalahan", res.error?.code)
} catch (e: HttpException) {
    ApiResult.Error(mapHttpMessage(e.code()), httpStatus = e.code())
} catch (e: IOException) {
    ApiResult.Error("Koneksi bermasalah. Periksa jaringan Anda.")
} catch (e: Exception) {
    ApiResult.Error(e.message ?: "Kesalahan tidak diketahui")
}

private fun mapHttpMessage(code: Int): String = when (code) {
    401 -> "Sesi berakhir, silakan login ulang"
    403 -> "Anda tidak memiliki akses untuk aksi ini"
    404 -> "Data tidak ditemukan"
    in 500..599 -> "Server sedang bermasalah"
    else -> "Permintaan gagal ($code)"
}
