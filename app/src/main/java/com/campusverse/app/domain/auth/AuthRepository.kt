package com.campusverse.app.domain.auth

import com.campusverse.app.data.model.UserRole
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication repository defining the core auth operations.
 * Concrete implementations back this contract without tying the UI to a specific auth vendor.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /**
     * Checks persistent storage for an active, unexpired session and restores it.
     */
    suspend fun restoreSession(): Result<AuthenticatedUser?>

    /**
     * Registers a new user with their validated credentials and chosen role.
     * The chosen role is strictly persisted with the newly created account.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): Result<AuthenticatedUser>

    /**
     * Authenticates an existing user and loads their stored role.
     */
    suspend fun login(
        email: String,
        password: String
    ): Result<AuthenticatedUser>

    /**
     * Verifies an email with an OTP / verification token.
     */
    suspend fun verifyOtp(
        email: String,
        otpCode: String
    ): Result<Boolean>

    /**
     * Requests a new verification OTP.
     */
    suspend fun resendOtp(
        email: String
    ): Result<Boolean>

    /**
     * Helper to retrieve the active OTP code for on-screen notification in development/simulation mode.
     */
    suspend fun getActiveOtpForDisplay(email: String): String? = null

    /**
     * Submits a password reset request for the specified email.
     */
    suspend fun requestPasswordReset(
        email: String
    ): Result<Boolean>

    /**
     * Resets the user's password using the reset token and new password.
     */
    suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): Result<Boolean>

    /**
     * Logs out the user, clearing all sensitive session tokens and state.
     */
    suspend fun logout()

    /**
     * Returns the currently authenticated user, or null if unauthenticated.
     */
    fun getCurrentUser(): AuthenticatedUser?
}
