package com.campusverse.app.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campusverse.app.data.model.UserRole
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

val Context.accountsDataStore: DataStore<Preferences> by preferencesDataStore(name = "campus_verse_accounts")

/**
 * Persistent DataStore implementation of [AccountStore].
 * Persists user accounts safely across app installations and restarts.
 */
class DataStoreAccountStore(
    private val dataStore: DataStore<Preferences>
) : AccountStore {

    private val mutex = Mutex()

    private fun accountKey(email: String): Preferences.Key<String> {
        val sanitized = email.trim().lowercase().replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return stringPreferencesKey("account_$sanitized")
    }

    override suspend fun getAccount(email: String): StoredAccount? {
        val key = accountKey(email)
        val preferences = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val jsonString = preferences[key] ?: return null
        return deserializeAccount(jsonString)
    }

    override suspend fun saveAccount(account: StoredAccount) {
        mutex.withLock {
            val key = accountKey(account.email)
            val serialized = serializeAccount(account)
            dataStore.edit { preferences ->
                preferences[key] = serialized
            }
        }
    }

    override suspend fun getAllAccounts(): List<StoredAccount> {
        val preferences = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val accounts = mutableListOf<StoredAccount>()
        for ((key, value) in preferences.asMap()) {
            if (key.name.startsWith("account_") && value is String) {
                deserializeAccount(value)?.let { accounts.add(it) }
            }
        }
        return accounts
    }

    override suspend fun clear() {
        mutex.withLock {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    companion object {
        fun serializeAccount(account: StoredAccount): String {
            fun escape(str: String): String {
                return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
            }

            val sb = StringBuilder()
            sb.append("{")
            sb.append("\"userId\":\"").append(escape(account.userId)).append("\",")
            sb.append("\"name\":\"").append(escape(account.name)).append("\",")
            sb.append("\"email\":\"").append(escape(account.email)).append("\",")
            sb.append("\"passwordHash\":\"").append(escape(account.passwordHash)).append("\",")
            sb.append("\"passwordSalt\":\"").append(escape(account.passwordSalt)).append("\",")
            sb.append("\"role\":\"").append(escape(account.role.name)).append("\",")
            sb.append("\"isEmailVerified\":").append(account.isEmailVerified).append(",")
            sb.append("\"isAdminAuthorized\":").append(account.isAdminAuthorized).append(",")
            sb.append("\"otpHash\":").append(if (account.otpHash != null) "\"${escape(account.otpHash)}\"" else "null").append(",")
            sb.append("\"otpExpiresAt\":").append(account.otpExpiresAt).append(",")
            sb.append("\"otpAttemptCount\":").append(account.otpAttemptCount).append(",")
            sb.append("\"otpCreatedAt\":").append(account.otpCreatedAt).append(",")
            sb.append("\"resetTokenHash\":").append(if (account.resetTokenHash != null) "\"${escape(account.resetTokenHash)}\"" else "null").append(",")
            sb.append("\"resetTokenExpiresAt\":").append(account.resetTokenExpiresAt).append(",")
            sb.append("\"resetTokenAttemptCount\":").append(account.resetTokenAttemptCount)
            sb.append("}")
            return sb.toString()
        }

        fun deserializeAccount(json: String): StoredAccount? {
            try {
                fun extractString(key: String): String? {
                    val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
                    val match = pattern.find(json) ?: return null
                    return match.groupValues[1]
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                }

                fun extractBoolean(key: String, default: Boolean = false): Boolean {
                    val pattern = Regex("\"$key\"\\s*:\\s*(true|false)")
                    val match = pattern.find(json) ?: return default
                    return match.groupValues[1].toBoolean()
                }

                fun extractLong(key: String, default: Long = 0L): Long {
                    val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
                    val match = pattern.find(json) ?: return default
                    return match.groupValues[1].toLongOrNull() ?: default
                }

                fun extractInt(key: String, default: Int = 0): Int {
                    val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
                    val match = pattern.find(json) ?: return default
                    return match.groupValues[1].toIntOrNull() ?: default
                }

                val userId = extractString("userId") ?: return null
                val name = extractString("name") ?: return null
                val email = extractString("email") ?: return null
                val passwordHash = extractString("passwordHash") ?: return null
                val passwordSalt = extractString("passwordSalt") ?: ""
                val roleStr = extractString("role") ?: return null
                val role = try {
                    UserRole.valueOf(roleStr)
                } catch (e: Exception) {
                    return null
                }
                val isEmailVerified = extractBoolean("isEmailVerified", false)
                val isAdminAuthorized = extractBoolean("isAdminAuthorized", false)
                val otpHash = extractString("otpHash")
                val otpExpiresAt = extractLong("otpExpiresAt", 0L)
                val otpAttemptCount = extractInt("otpAttemptCount", 0)
                val otpCreatedAt = extractLong("otpCreatedAt", 0L)
                val resetTokenHash = extractString("resetTokenHash")
                val resetTokenExpiresAt = extractLong("resetTokenExpiresAt", 0L)
                val resetTokenAttemptCount = extractInt("resetTokenAttemptCount", 0)

                return StoredAccount(
                    userId = userId,
                    name = name,
                    email = email,
                    passwordHash = passwordHash,
                    passwordSalt = passwordSalt,
                    role = role,
                    isEmailVerified = isEmailVerified,
                    isAdminAuthorized = isAdminAuthorized,
                    otpHash = otpHash,
                    otpExpiresAt = otpExpiresAt,
                    otpAttemptCount = otpAttemptCount,
                    otpCreatedAt = otpCreatedAt,
                    resetTokenHash = resetTokenHash,
                    resetTokenExpiresAt = resetTokenExpiresAt,
                    resetTokenAttemptCount = resetTokenAttemptCount
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
}
