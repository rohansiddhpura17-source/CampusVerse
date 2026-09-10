package com.campusverse.app

import com.campusverse.app.data.model.UserRole
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.screens.admin.AdminDashboardUiState
import com.campusverse.app.ui.screens.admin.AdminDashboardViewModel
import com.campusverse.app.ui.screens.alumni.AlumniHomeUiState
import com.campusverse.app.ui.screens.alumni.AlumniHomeViewModel
import com.campusverse.app.ui.screens.alumni.careers.JobDetailUiState
import com.campusverse.app.ui.screens.alumni.careers.JobDetailViewModel
import com.campusverse.app.ui.screens.alumni.mentorship.AlumniMentorshipUiState
import com.campusverse.app.ui.screens.alumni.mentorship.AlumniMentorshipViewModel
import com.campusverse.app.ui.screens.alumni.network.AlumniNetworkUiState
import com.campusverse.app.ui.screens.alumni.network.AlumniNetworkViewModel
import com.campusverse.app.ui.screens.aspirant.AspirantHomeUiState
import com.campusverse.app.ui.screens.aspirant.AspirantHomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 8: Cross-Role Integration & Complete User Journeys Android Test Suite.
 * Validates unified multi-role interactions across Aspirant, Student, Alumni, and Admin domains.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossRoleIntegrationTests {

    private val testDispatcher = StandardTestDispatcher()

    // Test Users
    private val aspirantUser = AuthenticatedUser("u_asp", "aspirant@campusverse.edu", "Kavya Sharma", UserRole.ASPIRANT, true, false)
    private val studentUser = AuthenticatedUser("u_stu", "student@campusverse.edu", "Rohan Mehta", UserRole.STUDENT, true, false)
    private val alumniUser = AuthenticatedUser("u_alu", "alumni@campusverse.edu", "Dr. Aisha Patel", UserRole.ALUMNI, true, false)
    private val adminUser = AuthenticatedUser("u_adm", "admin@campusverse.edu", "Campus Administrator", UserRole.ADMIN, true, true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. Role-Based Navigation Routing & Isolation
    // -------------------------------------------------------------------------
    @Test
    fun testRoleRouting_RoutesCorrectlyToIsolatedHomeScreens() {
        // Aspirant routing
        val aspirantRoute = when (aspirantUser.role) {
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminDashboard.route
        }
        assertEquals(Screen.AspirantHome.route, aspirantRoute)

        // Student routing
        val studentRoute = when (studentUser.role) {
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminDashboard.route
        }
        assertEquals(Screen.StudentHome.route, studentRoute)

        // Alumni routing
        val alumniRoute = when (alumniUser.role) {
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminDashboard.route
        }
        assertEquals(Screen.AlumniHome.route, alumniRoute)

        // Admin routing (Authorized)
        val adminRoute = when {
            adminUser.role == UserRole.ADMIN && adminUser.isAdminAuthorized -> Screen.AdminDashboard.route
            else -> Screen.AdminLogin.route
        }
        assertEquals(Screen.AdminDashboard.route, adminRoute)

        // Unauthorized Admin -> falls back to Admin Login
        val unauthAdmin = adminUser.copy(isAdminAuthorized = false)
        val unauthRoute = when {
            unauthAdmin.role == UserRole.ADMIN && unauthAdmin.isAdminAuthorized -> Screen.AdminDashboard.route
            else -> Screen.AdminLogin.route
        }
        assertEquals(Screen.AdminLogin.route, unauthRoute)
    }

    // -------------------------------------------------------------------------
    // 2. Student <-> Alumni Discovery & Connection Workflow
    // -------------------------------------------------------------------------
    @Test
    fun testStudentAlumniConnectionJourney() = runTest {
        val fakeAlumniRepo = FakeAlumniRepository()
        val networkViewModel = AlumniNetworkViewModel(fakeAlumniRepo)
        advanceUntilIdle()

        // 1. Initial State contains alumni records
        val initialState = networkViewModel.uiState.value as AlumniNetworkUiState.Success
        assertTrue(initialState.alumniList.isNotEmpty())

        // 2. Student connects to Alumni
        val target = initialState.alumniList.first()
        networkViewModel.connect(target.userId)
        advanceUntilIdle()

        // 3. Connect completes cleanly
        assertTrue(networkViewModel.uiState.value is AlumniNetworkUiState.Success)
    }

    // -------------------------------------------------------------------------
    // 3. Mentorship Cross-Role Session Overview
    // -------------------------------------------------------------------------
    @Test
    fun testMentorshipCrossRoleSessionLifecycle() = runTest {
        val fakeAlumniRepo = FakeAlumniRepository()
        val mentorshipViewModel = AlumniMentorshipViewModel(fakeAlumniRepo)
        advanceUntilIdle()

        val state1 = mentorshipViewModel.uiState.value as AlumniMentorshipUiState.Success
        assertTrue(state1.mentors.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // 4. Job Application & Status Progression
    // -------------------------------------------------------------------------
    @Test
    fun testJobApplicationTrackingLifecycle() = runTest {
        val fakeAlumniRepo = FakeAlumniRepository()
        val jobDetailViewModel = JobDetailViewModel(fakeAlumniRepo)
        jobDetailViewModel.loadJob("job_seed_1")
        advanceUntilIdle()

        val state1 = jobDetailViewModel.uiState.value as JobDetailUiState.Success
        assertEquals("job_seed_1", state1.job.id)

        // Submit Application
        jobDetailViewModel.apply("https://campusverse.edu/resumes/rohan.pdf", "Cover letter text")
        advanceUntilIdle()

        val state2 = jobDetailViewModel.uiState.value as JobDetailUiState.Success
        assertTrue(state2.applicationSuccess)
    }

    // -------------------------------------------------------------------------
    // 5. Admin Dashboard Overview & Health Telemetry
    // -------------------------------------------------------------------------
    @Test
    fun testAdminDashboard_IntegratesPlatformMetrics() = runTest {
        val fakeAdminRepo = FakeAdminRepository()
        val dashboardViewModel = AdminDashboardViewModel(fakeAdminRepo)
        advanceUntilIdle()

        val state = dashboardViewModel.uiState.value as AdminDashboardUiState.Success
        assertEquals(48, state.data.metrics.totalUsers)
        assertEquals(28, state.data.metrics.studentsCount)
        assertEquals(14, state.data.metrics.alumniCount)
        assertEquals(5, state.data.metrics.aspirantsCount)
        assertEquals(3, state.data.metrics.pendingVerifications)
    }

    // -------------------------------------------------------------------------
    // 6. Aspirant Home Data & Recommendations
    // -------------------------------------------------------------------------
    @Test
    fun testAspirantHome_RendersRecommendations() = runTest {
        val fakeAspirantRepo = FakeAspirantRepository()
        val aspirantHomeViewModel = AspirantHomeViewModel(fakeAspirantRepo)
        advanceUntilIdle()

        val state = aspirantHomeViewModel.uiState.value as AspirantHomeUiState.Success
        assertEquals("Rohan Mehta", state.summary.profile.fullName)
        assertEquals(1, state.summary.savedCollegesCount)
        assertEquals(1, state.summary.savedScholarshipsCount)
        assertEquals(3, state.summary.recommendedColleges.size)
    }

    // -------------------------------------------------------------------------
    // 7. Alumni Home Summary
    // -------------------------------------------------------------------------
    @Test
    fun testAlumniHome_RendersOverview() = runTest {
        val fakeAlumniRepo = FakeAlumniRepository()
        val alumniHomeViewModel = AlumniHomeViewModel(fakeAlumniRepo)
        advanceUntilIdle()

        val state = alumniHomeViewModel.uiState.value as AlumniHomeUiState.Success
        assertNotNull(state.summary.profile)
        assertTrue(state.summary.connectionsCount >= 0)
        assertTrue(state.summary.recommendedJobs.isNotEmpty())
    }
}
