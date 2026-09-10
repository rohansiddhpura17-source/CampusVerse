package com.campusverse.app

import com.campusverse.app.data.model.UserRole
import com.campusverse.app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying navigation routes and UserRole metadata integrity.
 */
class NavigationRouteTest {

    @Test
    fun allRequiredPlaceholderRoutes_areDefined() {
        assertEquals("splash", Screen.Splash.route)
        assertEquals("welcome", Screen.Welcome.route)
        assertEquals("login", Screen.Login.route)
        assertEquals("create_account", Screen.CreateAccount.route)
        assertEquals("role_selection", Screen.RoleSelection.route)
        assertEquals("verification", Screen.Verification.route)
        assertEquals("verification_success", Screen.VerificationSuccess.route)
        assertEquals("forgot_password", Screen.ForgotPassword.route)
        assertEquals("reset_password", Screen.ResetPassword.route)
        assertEquals("student_home", Screen.StudentHome.route)
        assertEquals("aspirant_home", Screen.AspirantHome.route)
        assertEquals("alumni_home", Screen.AlumniHome.route)
        assertEquals("admin_login", Screen.AdminLogin.route)
        assertEquals("admin_dashboard", Screen.AdminDashboard.route)
        assertEquals("admin_events", Screen.AdminEvents.route)
        assertEquals("admin_jobs", Screen.AdminJobs.route)
        assertEquals("admin_profile", Screen.AdminProfile.route)
    }

    @Test
    fun userRole_allFourRolesArePresentWithNonEmptyMetadata() {
        val roles = UserRole.entries
        assertEquals(4, roles.size)

        roles.forEach { role ->
            assertNotNull(role.title)
            assertNotNull(role.subtitle)
            assertNotNull(role.description)
            assert(role.title.isNotBlank())
            assert(role.subtitle.isNotBlank())
            assert(role.description.isNotBlank())
        }
    }
}
