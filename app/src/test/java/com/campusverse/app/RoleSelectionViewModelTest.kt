package com.campusverse.app

import com.campusverse.app.data.model.UserRole
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.screens.roleselection.RoleSelectionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated tests verifying Role Selection business logic, strict null defaults,
 * selection toggles, and role-based destination routing.
 */
class RoleSelectionViewModelTest {

    private lateinit var viewModel: RoleSelectionViewModel

    @Before
    fun setup() {
        viewModel = RoleSelectionViewModel()
    }

    @Test
    fun initialRole_isNull() {
        // CRITICAL REQUIREMENT: Initial role selection MUST be null.
        val state = viewModel.uiState.value
        assertNull("Initial selectedRole must be null", state.selectedRole)
    }

    @Test
    fun student_isNotPreselected() {
        // CRITICAL REQUIREMENT: Student MUST NOT be selected automatically.
        val state = viewModel.uiState.value
        assertFalse(
            "Student must not be preselected",
            state.selectedRole == UserRole.STUDENT
        )
        assertNull(
            "No role should be selected by default",
            state.selectedRole
        )
    }

    @Test
    fun continue_isDisabledWhenInitialRoleIsNull() {
        // CRITICAL REQUIREMENT: Continue is disabled when no role is selected
        val state = viewModel.uiState.value
        assertFalse(
            "Continue must be disabled when selectedRole is null",
            state.canContinue
        )
        assertNull(
            "No destination should be resolved when selectedRole is null",
            viewModel.getDestinationForSelectedRole()
        )
    }

    @Test
    fun selectingStudent_works() {
        // Behavior: tap Student -> Student selected
        viewModel.selectRole(UserRole.STUDENT)
        val state = viewModel.uiState.value

        assertEquals(
            "Selected role must be STUDENT",
            UserRole.STUDENT,
            state.selectedRole
        )
        assertTrue(
            "Continue must be enabled when STUDENT is selected",
            state.canContinue
        )
    }

    @Test
    fun selectingAspirant_works() {
        // Behavior: tap Aspirant -> Aspirant selected
        viewModel.selectRole(UserRole.ASPIRANT)
        val state = viewModel.uiState.value

        assertEquals(
            "Selected role must be ASPIRANT",
            UserRole.ASPIRANT,
            state.selectedRole
        )
        assertTrue(
            "Continue must be enabled when ASPIRANT is selected",
            state.canContinue
        )
    }

    @Test
    fun selectingAlumni_works() {
        // Behavior: tap Alumni -> Alumni selected
        viewModel.selectRole(UserRole.ALUMNI)
        val state = viewModel.uiState.value

        assertEquals(
            "Selected role must be ALUMNI",
            UserRole.ALUMNI,
            state.selectedRole
        )
        assertTrue(
            "Continue must be enabled when ALUMNI is selected",
            state.canContinue
        )
    }

    @Test
    fun selectingAdmin_works() {
        // Behavior: tap Admin -> Admin selected
        viewModel.selectRole(UserRole.ADMIN)
        val state = viewModel.uiState.value

        assertEquals(
            "Selected role must be ADMIN",
            UserRole.ADMIN,
            state.selectedRole
        )
        assertTrue(
            "Continue must be enabled when ADMIN is selected",
            state.canContinue
        )
    }

    @Test
    fun selectingAlumni_deselectsStudent() {
        // Behavior: Only one role may be selected
        viewModel.selectRole(UserRole.STUDENT)
        assertEquals(UserRole.STUDENT, viewModel.uiState.value.selectedRole)

        viewModel.selectRole(UserRole.ALUMNI)
        val state = viewModel.uiState.value

        assertEquals(
            "Selected role must now be ALUMNI",
            UserRole.ALUMNI,
            state.selectedRole
        )
        assertFalse(
            "STUDENT must no longer be selected",
            state.selectedRole == UserRole.STUDENT
        )
    }

    @Test
    fun clearSelection_resetsToNullAndDisablesContinue() {
        viewModel.selectRole(UserRole.ASPIRANT)
        assertTrue(viewModel.uiState.value.canContinue)

        viewModel.clearSelection()
        val state = viewModel.uiState.value

        assertNull("Selected role should be reset to null", state.selectedRole)
        assertFalse("Continue should be disabled after clearing", state.canContinue)
    }

    @Test
    fun routing_aspirantRoutesToAspirantHome() {
        // Routing: Aspirant -> Aspirant Home
        viewModel.selectRole(UserRole.ASPIRANT)
        val route = viewModel.getDestinationForSelectedRole()

        assertEquals(Screen.AspirantHome.route, route)
        assertEquals(Screen.AspirantHome.route, viewModel.getDestinationRoute(UserRole.ASPIRANT))
    }

    @Test
    fun routing_studentRoutesToStudentHome() {
        // Routing: Student -> Student Home
        viewModel.selectRole(UserRole.STUDENT)
        val route = viewModel.getDestinationForSelectedRole()

        assertEquals(Screen.StudentHome.route, route)
        assertEquals(Screen.StudentHome.route, viewModel.getDestinationRoute(UserRole.STUDENT))
    }

    @Test
    fun routing_alumniRoutesToAlumniHome() {
        // Routing: Alumni -> Alumni Home
        viewModel.selectRole(UserRole.ALUMNI)
        val route = viewModel.getDestinationForSelectedRole()

        assertEquals(Screen.AlumniHome.route, route)
        assertEquals(Screen.AlumniHome.route, viewModel.getDestinationRoute(UserRole.ALUMNI))
    }

    @Test
    fun routing_adminRoutesToAdminLogin() {
        // Routing: Admin -> Admin Login
        viewModel.selectRole(UserRole.ADMIN)
        val route = viewModel.getDestinationForSelectedRole()

        assertEquals(Screen.AdminLogin.route, route)
        assertEquals(Screen.AdminLogin.route, viewModel.getDestinationRoute(UserRole.ADMIN))
    }
}
