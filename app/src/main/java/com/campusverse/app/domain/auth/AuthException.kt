package com.campusverse.app.domain.auth

/**
 * Domain exception conveying classified authentication errors.
 */
class AuthException(
    val errorType: AuthErrorType,
    override val message: String
) : Exception(message)
