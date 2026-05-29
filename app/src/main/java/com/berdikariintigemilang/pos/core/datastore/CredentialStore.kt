package com.berdikariintigemilang.pos.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialDataStore by preferencesDataStore(name = "pos_credentials")

data class SavedCredentials(val username: String, val password: String)

/**
 * Menyimpan kredensial login terakhir yang berhasil agar form login dapat
 * terisi otomatis (membantu kasir yang lupa username/password).
 *
 * Disimpan lokal di perangkat dan sengaja TIDAK dihapus saat logout. Catatan:
 * password tersimpan apa adanya (sama seperti token sesi) — untuk keamanan
 * lebih kuat dapat ditingkatkan ke penyimpanan terenkripsi di kemudian hari.
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USERNAME = stringPreferencesKey("cred_username")
        val PASSWORD = stringPreferencesKey("cred_password")
    }

    val flow: Flow<SavedCredentials?> = context.credentialDataStore.data.map { prefs ->
        val username = prefs[Keys.USERNAME] ?: return@map null
        SavedCredentials(username = username, password = prefs[Keys.PASSWORD] ?: "")
    }

    suspend fun get(): SavedCredentials? = flow.first()

    suspend fun save(username: String, password: String) {
        context.credentialDataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.PASSWORD] = password
        }
    }

    suspend fun clear() {
        context.credentialDataStore.edit { it.clear() }
    }
}
