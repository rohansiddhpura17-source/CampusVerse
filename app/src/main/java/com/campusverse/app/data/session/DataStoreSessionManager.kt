package com.campusverse.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.domain.auth.AuthSession
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "campus_verse_session")

/**
 * Persistent DataStore implementation of [SessionManager].
 * Stores session metadata safely across app process lifecycles.
 */
class DataStoreSessionManager(
    private val dataStore: DataStore<Preferences>,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : SessionManager {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("session_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_IS_EMAIL_VERIFIED = booleanPreferencesKey("is_email_verified")
        private val KEY_IS_ADMIN_AUTHORIZED = booleanPreferencesKey("is_admin_authorized")
        private val KEY_EXPIRES_AT = longPreferencesKey("expires_at_epoch_millis")
        private val KEY_USER_PHOTO = stringPreferencesKey("user_photo")
    }

    override suspend fun saveSession(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = session.token
            preferences[KEY_USER_ID] = session.user.userId
            preferences[KEY_USER_EMAIL] = session.user.email
            preferences[KEY_USER_NAME] = session.user.name
            preferences[KEY_USER_ROLE] = session.user.role.name
            preferences[KEY_IS_EMAIL_VERIFIED] = session.user.isEmailVerified
            preferences[KEY_IS_ADMIN_AUTHORIZED] = session.user.isAdminAuthorized
            preferences[KEY_EXPIRES_AT] = session.expiresAtEpochMillis
        }
    }

    override suspend fun getSession(): AuthSession? {
        val session = getSessionFlow().first()
        if (session != null && !isSessionValid(session)) {
            clearSession()
            return null
        }
        return session
    }

    override suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
        com.campusverse.app.data.repository.NetworkAlumniRepository.instance.clearCache()
    }

    override suspend fun updateName(name: String) {
        dataStore.edit { preferences ->
            if (preferences.contains(KEY_USER_NAME)) {
                preferences[KEY_USER_NAME] = name
            }
        }
    }

    override suspend fun updateEmail(email: String) {
        dataStore.edit { preferences ->
            if (preferences.contains(KEY_USER_EMAIL)) {
                preferences[KEY_USER_EMAIL] = email
            }
        }
    }

    override suspend fun updatePhoto(photoUrl: String?) {
        dataStore.edit { preferences ->
            if (photoUrl != null) {
                preferences[KEY_USER_PHOTO] = photoUrl
            } else {
                preferences.remove(KEY_USER_PHOTO)
            }
        }
    }

    override suspend fun updateUserData(name: String?, email: String?) {
        dataStore.edit { preferences ->
            if (name != null && preferences.contains(KEY_USER_NAME)) {
                preferences[KEY_USER_NAME] = name
            }
            if (email != null && preferences.contains(KEY_USER_EMAIL)) {
                preferences[KEY_USER_EMAIL] = email
            }
        }
    }

    override fun getSessionFlow(): Flow<AuthSession?> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val token = preferences[KEY_TOKEN] ?: return@map null
                val userId = preferences[KEY_USER_ID] ?: return@map null
                val email = preferences[KEY_USER_EMAIL] ?: return@map null
                val name = preferences[KEY_USER_NAME] ?: return@map null
                val roleStr = preferences[KEY_USER_ROLE] ?: return@map null
                val role = try {
                    UserRole.valueOf(roleStr)
                } catch (e: IllegalArgumentException) {
                    // Safe recovery: Never default to Student or any arbitrary role if corrupt
                    return@map null
                }
                val isEmailVerified = preferences[KEY_IS_EMAIL_VERIFIED] ?: false
                val isAdminAuthorized = preferences[KEY_IS_ADMIN_AUTHORIZED] ?: false
                val expiresAt = preferences[KEY_EXPIRES_AT] ?: return@map null

                val user = AuthenticatedUser(
                    userId = userId,
                    email = email,
                    name = name,
                    role = role,
                    isEmailVerified = isEmailVerified,
                    isAdminAuthorized = isAdminAuthorized
                )

                val session = AuthSession(
                    token = token,
                    user = user,
                    expiresAtEpochMillis = expiresAt
                )

                if (isSessionValid(session)) {
                    session
                } else {
                    null
                }
            }
    }

    override fun isSessionValid(session: AuthSession?): Boolean {
        if (session == null) return false
        return !session.isExpired(timeProvider())
    }
}
