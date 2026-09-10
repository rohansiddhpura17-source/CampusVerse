package com.campusverse.app.domain.auth

import com.campusverse.app.data.model.UserRole

/**
 * Represents an authenticated user profile in CampusVerse.
 *
 * @property userId Unique identifier of the authenticated user.
 * @property email Registered email address.
 * @property name Full name of the user.
 * @property role The assigned user role (Aspirant, Student, Alumni, Admin).
 * @property isEmailVerified Whether the user's email has been verified via OTP/link.
 * @property isAdminAuthorized Whether the user has passed backend Admin authorization.
 */
data class AuthenticatedUser(
    val userId: String,
    val email: String,
    val name: String,
    val role: UserRole,
    val isEmailVerified: Boolean = false,
    val isAdminAuthorized: Boolean = false
)
