package com.campusverse.app.ui.screens.roleselection

import androidx.lifecycle.ViewModel
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel managing role selection state and role-based routing.
 *
 * CRITICAL REQUIREMENTS:
 * - Initial role selection MUST be null (selectedRole = null)
 * - Student MUST NOT be selected automatically.
 * - Selecting any role enables the Continue action.
 * - Role routing maps to specific destination routes.
 */
class RoleSelectionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RoleSelectionUiState(selectedRole = null, canContinue = false))
    val uiState: StateFlow<RoleSelectionUiState> = _uiState.asStateFlow()

    /**
     * Updates the selected role.
     * Only one role may be selected at any given time.
     */
    fun selectRole(role: UserRole) {
        _uiState.update {
            it.copy(
                selectedRole = role,
                canContinue = true
            )
        }
    }

    /**
     * Clears the current role selection, resetting back to null.
     */
    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedRole = null,
                canContinue = false
            )
        }
    }

    /**
     * Returns the target route for a given [UserRole].
     *
     * Aspirant -> Aspirant Home
     * Student   -> Student Home
     * Alumni    -> Alumni Home
     * Admin     -> Admin Login
     */
    fun getDestinationRoute(role: UserRole): String {
        return when (role) {
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminLogin.route
        }
    }

    /**
     * Resolves the navigation destination for the currently selected role.
     * Returns null if no role is currently selected.
     */
    fun getDestinationForSelectedRole(): String? {
        val currentRole = _uiState.value.selectedRole ?: return null
        return getDestinationRoute(currentRole)
    }
}
