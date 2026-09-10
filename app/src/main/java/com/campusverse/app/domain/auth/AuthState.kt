package com.campusverse.app.domain.auth

/**
 * Standard error categories for authentication and registration failures.
 */
enum class AuthErrorType {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_EXISTS,
    INVALID_EMAIL,
    WEAK_PASSWORD,
    PASSWORDS_DO_NOT_MATCH,
    SESSION_EXPIRED,
    VERIFICATION_FAILURE,
    NETWORK_FAILURE,
    ROLE_NOT_CONFIGURED,
    UNAUTHORIZED_ADMIN,
    UNKNOWN
}

/**
 * Verification state lifecycle representation.
 */
enum class VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    FAILED
}

/**
 * State abstraction representing the user's authentication status.
 */
sealed interface AuthState {
    /**
     * User is not authenticated.
     */
    data object Unauthenticated : AuthState

    /**
     * Authentication or session verification is currently in progress.
     */
    data object Loading : AuthState

    /**
     * User is authenticated with a valid user session.
     */
    data class Authenticated(
        val user: AuthenticatedUser
    ) : AuthState

    /**
     * Authentication failed with a user-friendly error message and classified error type.
     */
    data class Error(
        val message: String,
        val errorType: AuthErrorType = AuthErrorType.UNKNOWN
    ) : AuthState
}
