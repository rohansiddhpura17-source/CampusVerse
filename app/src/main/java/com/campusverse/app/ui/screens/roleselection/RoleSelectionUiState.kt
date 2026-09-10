package com.campusverse.app.ui.screens.roleselection

import com.campusverse.app.data.model.UserRole

/**
 * UI State for the Role Selection screen.
 * Initial role selection MUST be null.
 */
data class RoleSelectionUiState(
    val selectedRole: UserRole? = null,
    val canContinue: Boolean = false
)
