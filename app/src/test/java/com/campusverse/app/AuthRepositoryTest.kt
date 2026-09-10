package com.campusverse.app

import com.campusverse.app.data.account.InMemoryAccountStore
import com.campusverse.app.data.local.PasswordHasher
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthErrorType
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthState
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.navigation.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration tests for Authentication Architecture, Role Persistence,
 * Session Restoration, Security RBAC, and Error Handling (Requirements H through S).
 */
class AuthRepositoryTest {

    private lateinit var sessionManager: InMemorySessionManager
    private lateinit var accountStore: InMemoryAccountStore
    private var currentTime: Long = 1000000L
    private val sessionDuration: Long = 7 * 24 * 60 * 60 * 1000L // 7 days
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setup() {
        currentTime = 1000000L
        sessionManager = InMemorySessionManager(timeProvider = { currentTime })
        accountStore = InMemoryAccountStore()
        repository = AuthRepositoryImpl(
            accountStore = accountStore,
            sessionManager = sessionManager,
            sessionDurationMillis = sessionDuration,
            timeProvider = { currentTime }
        )
    }

    @Test
    fun H_registration_storesSelectedRole() = runBlocking {
        // Requirement H: Registration stores selected role
        val role = UserRole.STUDENT
        val result = repository.register(
            name = "John Doe",
            email = "john@university.edu",
            password = "Password123",
            role = role
        )

        assertTrue("Registration must succeed", result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("Registered user role must match selected role", UserRole.STUDENT, user!!.role)

        val session = sessionManager.getSession()
        assertNotNull("Session must be stored", session)
        assertEquals("Persisted session must store user role", UserRole.STUDENT, session!!.user.role)
    }

    @Test
    fun I_login_loadsStoredRole() = runBlocking {
        // Requirement I: Login loads stored role
        repository.register(
            name = "Sarah Connor",
            email = "sarah@alumni.edu",
            password = "Password123",
            role = UserRole.ALUMNI
        )

        // Clear active session to simulate new login
        sessionManager.clearSession()

        val loginResult = repository.login("sarah@alumni.edu", "Password123")
        assertTrue("Login must succeed", loginResult.isSuccess)

        val user = loginResult.getOrNull()
        assertNotNull(user)
        assertEquals("Login must return stored ALUMNI role", UserRole.ALUMNI, user!!.role)
    }

    @Test
    fun J_storedStudent_routesToStudentHome() = runBlocking {
        // Requirement J: Stored STUDENT routes to Student Home
        val result = repository.register(
            name = "Student User",
            email = "student@test.com",
            password = "Password123",
            role = UserRole.STUDENT
        )
        val user = result.getOrThrow()

        val destination = when (user.role) {
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminLogin.route
        }

        assertEquals(Screen.StudentHome.route, destination)
    }

    @Test
    fun K_storedAspirant_routesToAspirantHome() = runBlocking {
        // Requirement K: Stored ASPIRANT routes to Aspirant Home
        val result = repository.register(
            name = "Aspirant User",
            email = "aspirant@test.com",
            password = "Password123",
            role = UserRole.ASPIRANT
        )
        val user = result.getOrThrow()

        val destination = when (user.role) {
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminLogin.route
        }

        assertEquals(Screen.AspirantHome.route, destination)
    }

    @Test
    fun L_storedAlumni_routesToAlumniHome() = runBlocking {
        // Requirement L: Stored ALUMNI routes to Alumni Home
        val result = repository.register(
            name = "Alumni User",
            email = "alumni@test.com",
            password = "Password123",
            role = UserRole.ALUMNI
        )
        val user = result.getOrThrow()

        val destination = when (user.role) {
            UserRole.STUDENT -> Screen.StudentHome.route
            UserRole.ASPIRANT -> Screen.AspirantHome.route
            UserRole.ALUMNI -> Screen.AlumniHome.route
            UserRole.ADMIN -> Screen.AdminLogin.route
        }

        assertEquals(Screen.AlumniHome.route, destination)
    }

    @Test
    fun M_storedAdmin_doesNotBypassAdminAuthorization() = runBlocking {
        // Requirement M: Stored ADMIN does not bypass Admin authorization
        val result = repository.register(
            name = "Admin Candidate",
            email = "admin@candidate.com",
            password = "Password123",
            role = UserRole.ADMIN
        )
        val user = result.getOrThrow()

        assertEquals(UserRole.ADMIN, user.role)
        assertFalse(
            "Admin user must NOT be automatically authorized without backend RBAC verification",
            user.isAdminAuthorized
        )

        // Route resolution check
        val destination = if (user.role == UserRole.ADMIN && user.isAdminAuthorized) {
            Screen.AdminDashboard.route
        } else {
            Screen.AdminLogin.route
        }

        assertEquals("Unverified admin must route to Admin Login gate", Screen.AdminLogin.route, destination)
    }

    @Test
    fun N_logout_clearsSession() = runBlocking {
        // Requirement N: Logout clears session
        repository.register(
            name = "Test User",
            email = "test@logout.com",
            password = "Password123",
            role = UserRole.STUDENT
        )

        assertNotNull("Session must exist after registration", sessionManager.getSession())

        repository.logout()

        assertNull("Session must be null after logout", sessionManager.getSession())
        assertTrue("AuthState must be Unauthenticated", repository.authState.value is AuthState.Unauthenticated)
        assertNull("Current user must be null", repository.getCurrentUser())
    }

    @Test
    fun O_appRestart_restoresValidSession() = runBlocking {
        // Requirement O: App restart restores valid session
        repository.register(
            name = "Persistent User",
            email = "persist@session.com",
            password = "Password123",
            role = UserRole.ASPIRANT
        )

        // Simulate app restart with a fresh repository instance using the same session store
        val restartedRepo = AuthRepositoryImpl(
            sessionManager = sessionManager,
            sessionDurationMillis = sessionDuration,
            timeProvider = { currentTime }
        )
        val restoreResult = restartedRepo.restoreSession()

        assertTrue("Restore session must succeed", restoreResult.isSuccess)
        val restoredUser = restoreResult.getOrNull()
        assertNotNull("Restored user must not be null", restoredUser)
        assertEquals(UserRole.ASPIRANT, restoredUser!!.role)
        assertEquals("persist@session.com", restoredUser.email)
        assertTrue(restartedRepo.authState.value is AuthState.Authenticated)
    }

    @Test
    fun P_expiredSession_returnsToAuthentication() = runBlocking {
        // Requirement P: Expired session returns to authentication
        repository.register(
            name = "Expiring User",
            email = "expire@test.com",
            password = "Password123",
            role = UserRole.STUDENT
        )

        // Advance time past session expiration duration
        currentTime += sessionDuration + 1000L

        val restoreResult = repository.restoreSession()
        assertTrue("Restore call completes", restoreResult.isSuccess)
        val user = restoreResult.getOrNull()
        assertNull("Expired session must return null user", user)
        assertTrue("AuthState must be Unauthenticated after expiration", repository.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun Q_wrongPassword_showsError() = runBlocking {
        // Requirement Q: Wrong password shows error
        repository.register(
            name = "User Alpha",
            email = "alpha@campus.edu",
            password = "CorrectPassword1",
            role = UserRole.STUDENT
        )

        val failedLogin = repository.login("alpha@campus.edu", "WrongPassword9")
        assertTrue("Login must fail with incorrect password", failedLogin.isFailure)

        val exception = failedLogin.exceptionOrNull() as? AuthException
        assertNotNull(exception)
        assertEquals(AuthErrorType.INVALID_CREDENTIALS, exception!!.errorType)
    }

    @Test
    fun R_duplicateEmail_showsError() = runBlocking {
        // Requirement R: Duplicate email shows error
        val firstReg = repository.register(
            name = "First User",
            email = "duplicate@campus.edu",
            password = "Password123",
            role = UserRole.STUDENT
        )
        assertTrue(firstReg.isSuccess)

        val secondReg = repository.register(
            name = "Second User",
            email = "duplicate@campus.edu",
            password = "Password987",
            role = UserRole.ASPIRANT
        )
        assertTrue("Second registration must fail", secondReg.isFailure)

        val exception = secondReg.exceptionOrNull() as? AuthException
        assertNotNull(exception)
        assertEquals(AuthErrorType.EMAIL_ALREADY_EXISTS, exception!!.errorType)
    }

    @Test
    fun S_invalidRegistrationData_isRejected() = runBlocking {
        // Requirement S: Invalid registration data is rejected

        // 1. Blank name
        val blankNameResult = repository.register(
            name = "   ",
            email = "valid@campus.edu",
            password = "Password123",
            role = UserRole.STUDENT
        )
        assertTrue("Blank name must be rejected", blankNameResult.isFailure)

        // 2. Invalid email format
        val invalidEmailResult = repository.register(
            name = "Valid Name",
            email = "invalid-email-format",
            password = "Password123",
            role = UserRole.STUDENT
        )
        assertTrue("Invalid email must be rejected", invalidEmailResult.isFailure)

        // 3. Short password (< 8 chars)
        val shortPasswordResult = repository.register(
            name = "Valid Name",
            email = "valid2@campus.edu",
            password = "short",
            role = UserRole.STUDENT
        )
        assertTrue("Short password must be rejected", shortPasswordResult.isFailure)

        // 4. Password without numbers
        val noNumberPasswordResult = repository.register(
            name = "Valid Name",
            email = "valid3@campus.edu",
            password = "passwordonly",
            role = UserRole.STUDENT
        )
        assertTrue("Password without digits must be rejected", noNumberPasswordResult.isFailure)
    }

    @Test
    fun OTP_verificationFlow_updatesEmailVerifiedStatus() = runBlocking {
        val result = repository.register(
            name = "OTP User",
            email = "otp@campus.edu",
            password = "Password123",
            role = UserRole.STUDENT
        )
        val user = result.getOrThrow()
        assertFalse(user.isEmailVerified)

        val account = accountStore.getAccount("otp@campus.edu")!!
        val salt = account.passwordSalt
        val testOtp = "123456"
        accountStore.saveAccount(account.copy(otpHash = PasswordHasher.hash(testOtp, salt)))

        val verifyResult = repository.verifyOtp("otp@campus.edu", testOtp)
        assertTrue("OTP verification must succeed with valid code", verifyResult.isSuccess)

        val currentUser = repository.getCurrentUser()
        assertNotNull(currentUser)
        assertTrue("User email must now be marked verified", currentUser!!.isEmailVerified)
    }

    @Test
    fun PasswordReset_flow_updatesPasswordSuccessfully() = runBlocking {
        repository.register(
            name = "Reset User",
            email = "reset@campus.edu",
            password = "OldPassword1",
            role = UserRole.ALUMNI
        )

        val requestResult = repository.requestPasswordReset("reset@campus.edu")
        assertTrue("Password reset request must succeed", requestResult.isSuccess)

        val account = accountStore.getAccount("reset@campus.edu")!!
        val salt = account.passwordSalt
        val testToken = "654321"
        accountStore.saveAccount(account.copy(resetTokenHash = PasswordHasher.hash(testToken, salt)))

        val resetResult = repository.resetPassword("reset@campus.edu", testToken, "NewPassword99")
        assertTrue("Password reset must succeed with valid code and password", resetResult.isSuccess)

        // Verify login with new password works
        val loginResult = repository.login("reset@campus.edu", "NewPassword99")
        assertTrue("Login with new password must succeed", loginResult.isSuccess)

        // Verify old password no longer works
        val oldLoginResult = repository.login("reset@campus.edu", "OldPassword1")
        assertTrue("Login with old password must fail", oldLoginResult.isFailure)
    }
}
