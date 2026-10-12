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
        assertEquals("student_question_bank", Screen.QuestionBank.route)
        assertEquals("aspirant_home", Screen.AspirantHome.route)
        assertEquals("alumni_home", Screen.AlumniHome.route)
        assertEquals("admin_login", Screen.AdminLogin.route)
        assertEquals("admin_dashboard", Screen.AdminDashboard.route)
        assertEquals("admin_events", Screen.AdminEvents.route)
        assertEquals("admin_jobs", Screen.AdminJobs.route)
        assertEquals("admin_profile", Screen.AdminProfile.route)
        assertEquals("aspirant_saved_colleges", Screen.AspirantSavedColleges.route)
        assertEquals("aspirant_colleges", Screen.AspirantCollegeExplorer.route)
        assertEquals("aspirant_college_comparison", Screen.AspirantCollegeComparison.route)
        assertEquals("aspirant_predictor", Screen.AspirantPredictor.route)
        assertEquals("aspirant_scholarships", Screen.AspirantScholarships.route)
        assertEquals("aspirant_saved_scholarships", Screen.AspirantSavedScholarships.route)
        assertEquals("aspirant_profile", Screen.AspirantProfile.route)
        assertEquals("aspirant_settings", Screen.AspirantSettings.route)
        assertEquals("aspirant_notifications", Screen.AspirantNotifications.route)
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
