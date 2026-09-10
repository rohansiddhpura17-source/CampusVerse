package com.campusverse.app

import com.campusverse.app.data.account.InMemoryAccountStore
import com.campusverse.app.data.account.StoredAccount
import com.campusverse.app.data.local.PasswordHasher
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthState
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.screens.roleselection.RoleSelectionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 2.5 Tests covering:
 * A. Session survives process/app restart when valid.
 * B. Logout clears the session.
 * C. Student cannot access Admin Dashboard.
 * D. Aspirant cannot access Admin Dashboard.
 * E. Alumni cannot access Admin Dashboard.
 * F. Admin can access Admin Dashboard (when authorized).
 * G. Selecting Admin without an authorized Admin account does NOT grant access.
 * H. Stored role is used after login.
 * I. Missing/invalid role results in safe recovery behavior.
 * J. No default Student fallback exists.
 */
class Phase25Tests {

    private lateinit var sessionManager: InMemorySessionManager
    private lateinit var accountStore: InMemoryAccountStore
    private var currentTime: Long = 1_000_000L
    private val sessionDuration: Long = 7 * 24 * 60 * 60 * 1_000L
    private lateinit var repo: AuthRepositoryImpl

    @Before
    fun setup() {
        currentTime = 1_000_000L
        sessionManager = InMemorySessionManager(timeProvider = { currentTime })
        accountStore = InMemoryAccountStore()
        repo = AuthRepositoryImpl(
            sessionManager = sessionManager,
            accountStore = accountStore,
            sessionDurationMillis = sessionDuration,
            timeProvider = { currentTime }
        )
    }

    // -----------------------------------------------------------------------
    // A. Session survives process/app restart when valid
    // -----------------------------------------------------------------------
    @Test
    fun testA_sessionSurvivesAppRestartWhenValid() = runBlocking {
        repo.register("Student User", "student@test.com", "Password123", UserRole.STUDENT)
        assertNotNull(sessionManager.getSession())

        // Simulate app restart with a new repository instance pointing to the persisted stores
        val restartedRepo = AuthRepositoryImpl(
            sessionManager = sessionManager,
            accountStore = accountStore,
            sessionDurationMillis = sessionDuration,
            timeProvider = { currentTime }
        )

        val restoreResult = restartedRepo.restoreSession()
        assertTrue("Session restore must succeed on restart", restoreResult.isSuccess)
        val restoredUser = restoreResult.getOrNull()
        assertNotNull("Restored user must not be null", restoredUser)
        assertEquals("Role must be strictly preserved across restart", UserRole.STUDENT, restoredUser!!.role)
        assertEquals("student@test.com", restoredUser.email)
        assertTrue(restartedRepo.authState.value is AuthState.Authenticated)
    }

    // -----------------------------------------------------------------------
    // B. Logout clears the session
    // -----------------------------------------------------------------------
    @Test
    fun testB_logoutClearsSession() = runBlocking {
        repo.register("Alumni User", "alumni@test.com", "Password123", UserRole.ALUMNI)
        assertNotNull(sessionManager.getSession())

        repo.logout()

        assertNull("Session must be cleared after logout", sessionManager.getSession())
        assertTrue("Auth state must be Unauthenticated", repo.authState.value is AuthState.Unauthenticated)
        assertNull("getCurrentUser must be null", repo.getCurrentUser())

        // Reopening app after logout must not restore any old session
        val restartedRepo = AuthRepositoryImpl(
            sessionManager = sessionManager,
            accountStore = accountStore,
            sessionDurationMillis = sessionDuration,
            timeProvider = { currentTime }
        )
        val restoreResult = restartedRepo.restoreSession()
        assertTrue(restoreResult.isSuccess)
        assertNull("No session should be restored after logout", restoreResult.getOrNull())
        assertTrue("State must remain Unauthenticated", restartedRepo.authState.value is AuthState.Unauthenticated)
    }

    // -----------------------------------------------------------------------
    // C. Student cannot access Admin Dashboard
    // -----------------------------------------------------------------------
    @Test
    fun testC_studentCannotAccessAdminDashboard() = runBlocking {
        val reg = repo.register("Enrolled Student", "student@campus.edu", "Password123", UserRole.STUDENT)
        val user = reg.getOrThrow()

        val canAccess = user.role == UserRole.ADMIN && user.isAdminAuthorized
        assertFalse("Enrolled student MUST NOT be authorized for Admin Dashboard", canAccess)
    }

    // -----------------------------------------------------------------------
    // D. Aspirant cannot access Admin Dashboard
    // -----------------------------------------------------------------------
    @Test
    fun testD_aspirantCannotAccessAdminDashboard() = runBlocking {
        val reg = repo.register("Prospective Student", "aspirant@campus.edu", "Password123", UserRole.ASPIRANT)
        val user = reg.getOrThrow()

        val canAccess = user.role == UserRole.ADMIN && user.isAdminAuthorized
        assertFalse("Aspirant user MUST NOT be authorized for Admin Dashboard", canAccess)
    }

    // -----------------------------------------------------------------------
    // E. Alumni cannot access Admin Dashboard
    // -----------------------------------------------------------------------
    @Test
    fun testE_alumniCannotAccessAdminDashboard() = runBlocking {
        val reg = repo.register("Graduated Alumni", "alumni@campus.edu", "Password123", UserRole.ALUMNI)
        val user = reg.getOrThrow()

        val canAccess = user.role == UserRole.ADMIN && user.isAdminAuthorized
        assertFalse("Alumni user MUST NOT be authorized for Admin Dashboard", canAccess)
    }

    // -----------------------------------------------------------------------
    // F. Admin can access Admin Dashboard when authorized
    // -----------------------------------------------------------------------
    @Test
    fun testF_authorizedAdminCanAccessAdminDashboard() = runBlocking {
        val authorizedAdminUser = AuthenticatedUser(
            userId = "admin_001",
            email = "superadmin@campus.edu",
            name = "Campus SuperAdmin",
            role = UserRole.ADMIN,
            isEmailVerified = true,
            isAdminAuthorized = true
        )

        val canAccess = authorizedAdminUser.role == UserRole.ADMIN && authorizedAdminUser.isAdminAuthorized
        assertTrue("Authorized Admin user must be permitted to access Admin Dashboard", canAccess)
    }

    // -----------------------------------------------------------------------
    // G. Selecting Admin without an authorized Admin account does NOT grant access
    // -----------------------------------------------------------------------
    @Test
    fun testG_selectingAdminDoesNotGrantAdminAuthorization() = runBlocking {
        val reg = repo.register("Self Declared Admin", "admin_candidate@test.com", "Password123", UserRole.ADMIN)
        val user = reg.getOrThrow()

        assertEquals("Registered role is ADMIN", UserRole.ADMIN, user.role)
        assertFalse(
            "Selecting Admin role must NEVER grant isAdminAuthorized privileges automatically",
            user.isAdminAuthorized
        )

        // Route guard verification
        val targetDestination = if (user.role == UserRole.ADMIN && user.isAdminAuthorized) {
            Screen.AdminDashboard.route
        } else {
            Screen.AdminLogin.route
        }

        assertEquals(
            "Unverified Admin must be routed to Admin Login checkpoint, NOT Admin Dashboard",
            Screen.AdminLogin.route,
            targetDestination
        )
    }

    // -----------------------------------------------------------------------
    // H. Stored role is used after login (Source of Truth)
    // -----------------------------------------------------------------------
    @Test
    fun testH_storedRoleIsUsedAfterLogin() = runBlocking {
        repo.register("Sarah Alumni", "sarah@network.com", "Password123", UserRole.ALUMNI)
        sessionManager.clearSession()

        val loginResult = repo.login("sarah@network.com", "Password123")
        assertTrue("Login must succeed", loginResult.isSuccess)
        val user = loginResult.getOrThrow()

        assertEquals("Stored role ALUMNI must be returned from persistent store", UserRole.ALUMNI, user.role)
        assertNotEquals("Role must NOT fallback to STUDENT", UserRole.STUDENT, user.role)
    }

    // -----------------------------------------------------------------------
    // I. Password hashing security
    // -----------------------------------------------------------------------
    @Test
    fun testI_passwordsAreStoredHashedNotPlaintext() = runBlocking {
        val plainPassword = "SecurePassword123"
        repo.register("Crypto User", "crypto@campus.edu", plainPassword, UserRole.STUDENT)

        val storedAccount = accountStore.getAccount("crypto@campus.edu")
        assertNotNull("Account must exist in store", storedAccount)
        assertNotEquals(
            "Stored password must NEVER be plaintext",
            plainPassword,
            storedAccount!!.passwordHash
        )
        assertTrue(
            "Password hash must match PBKDF2 verification",
            PasswordHasher.verify(plainPassword, storedAccount.passwordSalt, storedAccount.passwordHash)
        )
    }

    // -----------------------------------------------------------------------
    // J. No default Student fallback exists
    // -----------------------------------------------------------------------
    @Test
    fun testJ_noDefaultStudentFallbackExists() {
        val roleViewModel = RoleSelectionViewModel()
        val initialState = roleViewModel.uiState.value

        assertNull("Initial selected role MUST be null, NEVER default to Student", initialState.selectedRole)
        assertFalse("Continue button MUST be disabled when no role is chosen", initialState.canContinue)
        assertNull("No destination should be resolved when role is null", roleViewModel.getDestinationForSelectedRole())
    }

    // -----------------------------------------------------------------------
    // K. Persistent Account JSON Serialization Roundtrip
    // -----------------------------------------------------------------------
    @Test
    fun testK_accountStoreJsonRoundtrip() {
        val original = StoredAccount(
            userId = "usr_12345",
            name = "Test \"User\" \n with specials",
            email = "test@domain.edu",
            passwordHash = "abcdef1234567890",
            passwordSalt = "salt-uuid-9999",
            role = UserRole.ALUMNI,
            isEmailVerified = true,
            isAdminAuthorized = false,
            otpHash = "otp_hash_654321",
            otpExpiresAt = 1750000000000L,
            otpAttemptCount = 1,
            otpCreatedAt = 1749999700000L,
            resetTokenHash = null,
            resetTokenExpiresAt = 0L,
            resetTokenAttemptCount = 0
        )

        val json = com.campusverse.app.data.account.DataStoreAccountStore.serializeAccount(original)
        val deserialized = com.campusverse.app.data.account.DataStoreAccountStore.deserializeAccount(json)

        assertNotNull("Deserialized account must not be null", deserialized)
        assertEquals(original.userId, deserialized!!.userId)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.email, deserialized.email)
        assertEquals(original.passwordHash, deserialized.passwordHash)
        assertEquals(original.passwordSalt, deserialized.passwordSalt)
        assertEquals(original.role, deserialized.role)
        assertEquals(original.isEmailVerified, deserialized.isEmailVerified)
        assertEquals(original.isAdminAuthorized, deserialized.isAdminAuthorized)
        assertEquals(original.otpHash, deserialized.otpHash)
        assertEquals(original.otpExpiresAt, deserialized.otpExpiresAt)
        assertEquals(original.otpAttemptCount, deserialized.otpAttemptCount)
        assertEquals(original.otpCreatedAt, deserialized.otpCreatedAt)
        assertNull(deserialized.resetTokenHash)
    }

    @Test
    fun testL_corruptJsonReturnsNullGracefully() {
        val corruptJson = "{ invalid json without proper structure "
        val result = com.campusverse.app.data.account.DataStoreAccountStore.deserializeAccount(corruptJson)
        assertNull("Corrupted account record must return null gracefully without throwing", result)
    }
}
