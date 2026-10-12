package com.campusverse.app.domain.auth

/**
 * Represents a persistent authentication session.
 *
 * @property token Secure session token.
 * @property user Authenticated user payload including stored role.
 * @property expiresAtEpochMillis Timestamp when the session expires.
 */
data class AuthSession(
    val token: String,
    val user: AuthenticatedUser,
    val expiresAtEpochMillis: Long
) {
    fun isExpired(currentEpochMillis: Long = System.currentTimeMillis()): Boolean {
        return currentEpochMillis >= expiresAtEpochMillis
    }

    fun isJwtFormat(): Boolean {
        if (token.isBlank() || token.startsWith("token_")) return false
        val parts = token.split(".")
        return parts.size == 3 && parts.all { it.isNotBlank() }
    }
}
