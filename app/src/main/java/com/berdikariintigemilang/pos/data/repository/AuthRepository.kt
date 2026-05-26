package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.datastore.SessionStore
import com.berdikariintigemilang.pos.core.datastore.SessionUser
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.core.util.isAllowedToLogin
import com.berdikariintigemilang.pos.core.util.isPosAdmin
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.LoginRequest
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val sessionStore: SessionStore
) {
    val userFlow: Flow<SessionUser?> = sessionStore.userFlow
    val tokenFlow: Flow<String?> = sessionStore.tokenFlow

    suspend fun login(username: String, password: String): ApiResult<SessionUser> {
        // 1) Autentikasi ke endpoint standar big-tracker.
        val loginRes = try {
            api.signin(LoginRequest(username.trim(), password))
        } catch (e: HttpException) {
            return ApiResult.Error(
                if (e.code() == 401 || e.code() == 400) "Username atau password salah"
                else "Login gagal (${e.code()})",
                httpStatus = e.code()
            )
        } catch (e: IOException) {
            return ApiResult.Error("Tidak dapat terhubung ke server. Periksa jaringan.")
        } catch (e: Exception) {
            return ApiResult.Error(e.message ?: "Login gagal")
        }

        // 2) Batasi hanya role POS yang diizinkan.
        if (!loginRes.roles.isAllowedToLogin()) {
            return ApiResult.Error("Akun ini tidak memiliki akses ke aplikasi POS")
        }

        // 3) Simpan token lebih dulu agar /me terotentikasi.
        sessionStore.saveToken(loginRes.accessToken)

        // 4) Ambil profil lengkap (fullName, isAdmin).
        return when (val me = safePosCall { api.me() }) {
            is ApiResult.Success -> {
                val user = SessionUser(
                    id = me.data.id,
                    username = me.data.username,
                    fullName = me.data.fullName ?: me.data.username,
                    roles = me.data.roles.toSet(),
                    isAdmin = me.data.isAdmin || me.data.roles.isPosAdmin()
                )
                sessionStore.saveUser(user)
                ApiResult.Success(user)
            }
            is ApiResult.Error -> {
                // fallback: pakai data dari login bila /me gagal
                val user = SessionUser(
                    id = loginRes.id,
                    username = loginRes.username,
                    fullName = loginRes.username,
                    roles = loginRes.roles.toSet(),
                    isAdmin = loginRes.roles.isPosAdmin()
                )
                sessionStore.saveUser(user)
                ApiResult.Success(user)
            }
        }
    }

    /** Validasi token masih berlaku (dipakai Splash). */
    suspend fun validateSession(): Boolean = when (safePosCall { api.me() }) {
        is ApiResult.Success -> true
        is ApiResult.Error -> false
    }

    suspend fun logout() {
        sessionStore.clear()
    }
}
