package com.berdikariintigemilang.pos.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "pos_session")

data class SessionUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val roles: Set<String>,
    val isAdmin: Boolean
)

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val FULL_NAME = stringPreferencesKey("full_name")
        val ROLES = stringSetPreferencesKey("roles")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }

    val userFlow: Flow<SessionUser?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.USER_ID] ?: return@map null
        SessionUser(
            id = id,
            username = prefs[Keys.USERNAME] ?: "",
            fullName = prefs[Keys.FULL_NAME] ?: prefs[Keys.USERNAME] ?: "",
            roles = prefs[Keys.ROLES] ?: emptySet(),
            isAdmin = prefs[Keys.IS_ADMIN] ?: false
        )
    }

    suspend fun tokenOnce(): String? = context.dataStore.data.first()[Keys.TOKEN]

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[Keys.TOKEN] = token }
    }

    suspend fun saveUser(user: SessionUser) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.FULL_NAME] = user.fullName
            prefs[Keys.ROLES] = user.roles
            prefs[Keys.IS_ADMIN] = user.isAdmin
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
