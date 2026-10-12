package com.campusverse.app

import com.campusverse.app.data.account.InMemoryAccountStore
import com.campusverse.app.data.model.AdminDashboardData
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.NetworkAuthRepository
import com.campusverse.app.domain.auth.AuthSession
import com.campusverse.app.domain.auth.AuthState
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.ui.screens.admin.AdminDashboardUiState
import com.campusverse.app.ui.screens.admin.AdminDashboardViewModel
import com.campusverse.app.ui.screens.admin.AdminLoginUiState
import com.campusverse.app.ui.screens.admin.AdminLoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testUser = AuthenticatedUser(
        userId = "usr_admin_1",
        email = "admin@campusverse.edu",
        name = "System Super Administrator",
        role = UserRole.ADMIN,
        isEmailVerified = true,
        isAdminAuthorized = true
    )
    private val validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c3JfYWRtaW5fMSIsImVtYWlsIjoiYWRtaW5AY2FtcHVzdmVyc2UuZWR1Iiwicm9sZSI6IkFETUlOIn0.signature_abc"
    private val mockToken = "token_fa0e9982-1c2a-4bc6-a991-382901c38fa1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMockTokensAreDetectedAsNonJwtFormat() {
        val mockSession = AuthSession(mockToken, testUser, System.currentTimeMillis() + 60000)
        assertFalse("Mock token starting with token_ must not be JWT format", mockSession.isJwtFormat())

        val emptySession = AuthSession("", testUser, System.currentTimeMillis() + 60000)
        assertFalse("Blank token must not be JWT format", emptySession.isJwtFormat())

        val partialToken = AuthSession("eyJhbGciOi.eyJzdWIiOi", testUser, System.currentTimeMillis() + 60000)
        assertFalse("Incomplete 2-part token must not be JWT format", partialToken.isJwtFormat())

        val realSession = AuthSession(validJwt, testUser, System.currentTimeMillis() + 60000)
        assertTrue("3-part standard JWT must be recognized as valid format", realSession.isJwtFormat())
    }

    @Test
    fun testSessionExpirationLogic() {
        val now = 1000000L
        val activeSession = AuthSession(validJwt, testUser, expiresAtEpochMillis = now + 50000L)
        assertFalse("Future session must not be expired", activeSession.isExpired(now))

        val expiredSession = AuthSession(validJwt, testUser, expiresAtEpochMillis = now - 1000L)
        assertTrue("Past session must be expired", expiredSession.isExpired(now))
    }

    @Test
    fun testRestoreSession_mockTokenPurgedImmediately() = runTest {
        val sessionManager = InMemorySessionManager()
        val accountStore = InMemoryAccountStore()
        val repository = NetworkAuthRepository(
            baseUrl = "https://campusverse-backend-api.onrender.com/api/v1",
            sessionManager = sessionManager,
            accountStore = accountStore
        )

        // Seed with a mock token from earlier sessions
        sessionManager.saveSession(AuthSession(mockToken, testUser, System.currentTimeMillis() + 100000L))
        assertNotNull("Session seeded", sessionManager.getSession())

        val result = repository.restoreSession()
        assertTrue("Restore session call finishes", result.isSuccess)
        assertNull("Restored user must be null when stored token is a mock token", result.getOrNull())
        assertNull("Session in sessionManager must be purged on mock token discovery", sessionManager.getSession())
        assertEquals("Auth state must transition to Unauthenticated", AuthState.Unauthenticated, repository.authState.value)
    }

    @Test
    fun testRestoreSession_expiredSessionReturnsNull() = runTest {
        val currentTime = 100000L
        val sessionManager = InMemorySessionManager(timeProvider = { currentTime })
        val accountStore = InMemoryAccountStore()
        val repository = NetworkAuthRepository(
            baseUrl = "https://campusverse-backend-api.onrender.com/api/v1",
            sessionManager = sessionManager,
            accountStore = accountStore
        )

        // Seed with expired session
        sessionManager.saveSession(AuthSession(validJwt, testUser, expiresAtEpochMillis = 50000L))

        val result = repository.restoreSession()
        assertTrue(result.isSuccess)
        assertNull("Expired session must restore to null", result.getOrNull())
        assertNull("Session must be cleared", sessionManager.getSession())
    }

    @Test
    fun testAdminDashboardViewModel_authErrorSetsIsAuthErrorTrue() = runTest {
        val failingRepo = object : FakeAdminRepository() {
            override suspend fun getDashboardData(): Result<AdminDashboardData> = Result.failure(
                Exception("Dashboard API failed (401): INVALID_TOKEN — Authentication token is invalid or expired.")
            )
        }

        val viewModel = AdminDashboardViewModel(failingRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("UI state must be Error", state is AdminDashboardUiState.Error)
        val errorState = state as AdminDashboardUiState.Error
        assertTrue("isAuthError must be true on 401 INVALID_TOKEN", errorState.isAuthError)
        assertTrue("Message must contain 401 error details", errorState.message.contains("401"))
    }

    @Test
    fun testAdminDashboardViewModel_genericNetworkErrorDoesNotFlagAuthError() = runTest {
        val networkErrRepo = object : FakeAdminRepository() {
            override suspend fun getDashboardData(): Result<AdminDashboardData> = Result.failure(
                Exception("Unable to reach server: Connection timed out")
            )
        }

        val viewModel = AdminDashboardViewModel(networkErrRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("UI state must be Error", state is AdminDashboardUiState.Error)
        val errorState = state as AdminDashboardUiState.Error
        assertFalse("Generic network error must not be flagged as auth error", errorState.isAuthError)
    }

    @Test
    fun testAdminLogin_nonAdminAccountIsDeniedAndLoggedOut() = runTest {
        var logoutCalled = false
        val studentUser = AuthenticatedUser(
            userId = "usr_std_1",
            email = "student@campusverse.edu",
            name = "Student User",
            role = UserRole.STUDENT,
            isEmailVerified = true,
            isAdminAuthorized = false
        )

        val fakeAuthRepo = object : com.campusverse.app.domain.auth.AuthRepository {
            override val authState = kotlinx.coroutines.flow.MutableStateFlow<AuthState>(AuthState.Unauthenticated)
            override suspend fun login(email: String, password: String): Result<AuthenticatedUser> {
                return Result.success(studentUser)
            }
            override suspend fun register(name: String, email: String, password: String, role: UserRole) = Result.success(studentUser)
            override suspend fun restoreSession() = Result.success(null)
            override suspend fun logout() {
                logoutCalled = true
            }
            override fun getCurrentUser() = null
            override suspend fun verifyOtp(email: String, otpCode: String) = Result.success(true)
            override suspend fun resendOtp(email: String) = Result.success(true)
            override suspend fun requestPasswordReset(email: String) = Result.success(true)
            override suspend fun resetPassword(email: String, token: String, newPassword: String) = Result.success(true)
        }

        val viewModel = AdminLoginViewModel(fakeAuthRepo)
        viewModel.onEmailChanged("student@campusverse.edu")
        viewModel.onPasswordChanged("Password123")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Form state with error must be active", state is AdminLoginUiState.Form)
        val formState = state as AdminLoginUiState.Form
        assertTrue("Access Denied error message must be shown", formState.errorMessage?.contains("Access Denied") == true)
        assertTrue("Session must be cleared via logout on non-admin login attempt", logoutCalled)
    }
}
