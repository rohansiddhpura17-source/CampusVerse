package com.campusverse.app.data.account

import com.campusverse.app.data.model.UserRole

/**
 * Internal persisted account entity.
 * Never stores plaintext passwords; stores hashed password and unique per-user salt.
 */
data class StoredAccount(
    val userId: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val role: UserRole,
    val isEmailVerified: Boolean = false,
    val isAdminAuthorized: Boolean = false,
    val otpHash: String? = null,
    val otpExpiresAt: Long = 0L,
    val otpAttemptCount: Int = 0,
    val otpCreatedAt: Long = 0L,
    val resetTokenHash: String? = null,
    val resetTokenExpiresAt: Long = 0L,
    val resetTokenAttemptCount: Int = 0
)

/**
 * Abstraction for persistent account storage.
 * Isolates local account storage behind a repository-friendly interface
 * so it can easily be swapped with a remote auth backend.
 */
interface AccountStore {
    suspend fun getAccount(email: String): StoredAccount?
    suspend fun saveAccount(account: StoredAccount)
    suspend fun getAllAccounts(): List<StoredAccount>
    suspend fun clear()
}
