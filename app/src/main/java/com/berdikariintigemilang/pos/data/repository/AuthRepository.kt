package com.berdikariintigemilang.pos.data.repository

import com.berdikariintigemilang.pos.core.datastore.CredentialStore
import com.berdikariintigemilang.pos.core.datastore.SavedCredentials
import com.berdikariintigemilang.pos.core.datastore.SessionStore
import com.berdikariintigemilang.pos.core.datastore.SessionUser
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.core.network.safePosCall
import com.berdikariintigemilang.pos.core.util.Constants
import com.berdikariintigemilang.pos.core.util.isAllowedToLogin
import com.berdikariintigemilang.pos.core.util.isPosAdmin
import com.berdikariintigemilang.pos.data.cart.CartManager
import com.berdikariintigemilang.pos.data.remote.ApiService
import com.berdikariintigemilang.pos.data.remote.LoginRequest
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Karyawan berwenang hasil verifikasi kredensial untuk ubah harga di kasir. */
data class PriceEditor(val id: Long, val username: String)

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val sessionStore: SessionStore,
    private val credentialStore: CredentialStore,
    private val cartManager: CartManager
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

        // Simpan kredensial valid agar form login bisa terisi otomatis nanti.
        // Dibungkus agar kegagalan tulis tak pernah menggagalkan proses login.
        runCatching { credentialStore.save(username.trim(), password) }

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

    /**
     * Verifikasi kredensial karyawan yang berwenang menurunkan harga di kasir,
     * TANPA mengubah sesi kasir yang sedang login (token & user tidak ditimpa).
     *
     * Berhasil hanya bila: kredensial benar DAN id karyawan termasuk daftar
     * [Constants.PRICE_EDIT_AUTHORIZED_IDS] (mis. 38/54/60). Verifikasi password
     * dilakukan di server sehingga membutuhkan koneksi internet.
     */
    suspend fun verifyPriceEditor(username: String, password: String): ApiResult<PriceEditor> {
        val res = try {
            api.signin(LoginRequest(username.trim(), password))
        } catch (e: HttpException) {
            return ApiResult.Error(
                if (e.code() == 401 || e.code() == 400) "Username atau password salah"
                else "Verifikasi gagal (${e.code()})",
                httpStatus = e.code()
            )
        } catch (e: IOException) {
            return ApiResult.Error("Perlu koneksi internet untuk verifikasi ubah harga")
        } catch (e: Exception) {
            return ApiResult.Error(e.message ?: "Verifikasi gagal")
        }
        if (res.id !in Constants.PRICE_EDIT_AUTHORIZED_IDS) {
            return ApiResult.Error("Akun ini tidak berwenang mengubah harga")
        }
        return ApiResult.Success(PriceEditor(id = res.id, username = res.username.ifBlank { username.trim() }))
    }

    /** Validasi token masih berlaku (dipakai Splash). */
    suspend fun validateSession(): Boolean = when (safePosCall { api.me() }) {
        is ApiResult.Success -> true
        is ApiResult.Error -> false
    }

    /** Kredensial login terakhir yang berhasil (untuk mengisi form login). */
    suspend fun savedCredentials(): SavedCredentials? = credentialStore.get()

    suspend fun logout() {
        sessionStore.clear()
        cartManager.clear()
        // Kredensial sengaja TIDAK dihapus agar form login tetap terisi.
    }
}
