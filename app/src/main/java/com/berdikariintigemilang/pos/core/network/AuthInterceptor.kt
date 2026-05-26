package com.berdikariintigemilang.pos.core.network

import com.berdikariintigemilang.pos.core.datastore.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyisipkan header Authorization: Bearer <token> untuk semua request
 * kecuali login, dan memicu auto-logout saat menerima 401.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
    private val authEventBus: AuthEventBus
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isAuthEndpoint = original.url.encodedPath.contains("/api/auth/signin")

        val request = if (isAuthEndpoint) {
            original
        } else {
            val token = runBlocking { sessionStore.tokenOnce() }
            if (token.isNullOrBlank()) original
            else original.newBuilder().header("Authorization", "Bearer $token").build()
        }

        val response = chain.proceed(request)
        if (response.code == 401 && !isAuthEndpoint) {
            runBlocking { sessionStore.clear() }
            authEventBus.notifyLoggedOut()
        }
        return response
    }
}
