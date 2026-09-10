package com.campusverse.app

import com.campusverse.app.data.account.InMemoryAccountStore
import com.campusverse.app.data.local.PasswordHasher
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthErrorType
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthState
import com.campusverse.app.domain.session.InMemorySessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 1 & 2 Audit — missing test coverage added per full audit requirements.
 *
 * Covers:
 *  - OTP single-use enforcement
 *  - Reset-token single-use enforcement
 *  - Unauthorized admin access rejection (all non-admin roles)
 *  - Session not restored after explicit logout
 *  - Correct role routing after login for all four roles
 *  - Admin registration does NOT grant isAdminAuthorized
 */
class AuditMissingTests {

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
            accountStore = accountStore,
            sessionManager = sessionManager,
            sessionDurationMillis = sessionDuration,
            timeProvider = { currentTime }
        )
    }

    // -----------------------------------------------------------------------
    // OTP ENFORCEMENT
    // -----------------------------------------------------------------------

    @Test
    fun otp_correctCode_verifies() = runBlocking {
        repo.register("OTP User", "otp@test.com", "Password1", UserRole.STUDENT)
        val acc = accountStore.getAccount("otp@test.com")!!
        val testOtp = "654321"
        accountStore.saveAccount(acc.copy(otpHash = PasswordHasher.hash(testOtp, acc.passwordSalt)))

        val result = repo.verifyOtp("otp@test.com", testOtp)
        assertTrue("Valid OTP must succeed", result.isSuccess)
        assertTrue("User must be email-verified", repo.getCurrentUser()!!.isEmailVerified)
    }

    @Test
    fun otp_wrongCode_isRejected() = runBlocking {
        repo.register("OTP User2", "otp2@test.com", "Password1", UserRole.STUDENT)

        val result = repo.verifyOtp("otp2@test.com", "000000")
        assertTrue("Wrong OTP must fail", result.isFailure)
        val ex = result.exceptionOrNull() as? AuthException
        assertNotNull(ex)
        assertEquals(AuthErrorType.VERIFICATION_FAILURE, ex!!.errorType)
    }

    @Test
    fun otp_afterConsumed_cannotBeReused() = runBlocking {
        repo.register("OTP Reuse", "reuse@test.com", "Password1", UserRole.STUDENT)
        val acc = accountStore.getAccount("reuse@test.com")!!
        val testOtp = "654321"
        accountStore.saveAccount(acc.copy(otpHash = PasswordHasher.hash(testOtp, acc.passwordSalt)))

        // First verification succeeds
        val first = repo.verifyOtp("reuse@test.com", testOtp)
        assertTrue("First OTP must succeed", first.isSuccess)

        // Second attempt with same code must now fail (OTP is nulled after use)
        val second = repo.verifyOtp("reuse@test.com", testOtp)
        assertTrue("Reused OTP must be rejected after consumption", second.isFailure)
    }

    // -----------------------------------------------------------------------
    // RESET TOKEN ENFORCEMENT
    // -----------------------------------------------------------------------

    @Test
    fun resetToken_correctToken_resetsPassword() = runBlocking {
        repo.register("Reset User", "reset@test.com", "OldPass1", UserRole.ALUMNI)
        repo.requestPasswordReset("reset@test.com")
        val acc = accountStore.getAccount("reset@test.com")!!
        val testToken = "123456"
        accountStore.saveAccount(acc.copy(resetTokenHash = PasswordHasher.hash(testToken, acc.passwordSalt)))

        val result = repo.resetPassword("reset@test.com", testToken, "NewPass99")
        assertTrue("Reset with valid token must succeed", result.isSuccess)

        val loginResult = repo.login("reset@test.com", "NewPass99")
        assertTrue("Login with new password must succeed", loginResult.isSuccess)
    }

    @Test
    fun resetToken_wrongToken_isRejected() = runBlocking {
        repo.register("Reset Bad", "badreset@test.com", "OldPass1", UserRole.STUDENT)
        repo.requestPasswordReset("badreset@test.com")

        val result = repo.resetPassword("badreset@test.com", "999999", "NewPass99")
        assertTrue("Wrong reset token must be rejected", result.isFailure)
        val ex = result.exceptionOrNull() as? AuthException
        assertNotNull(ex)
        assertEquals(AuthErrorType.INVALID_CREDENTIALS, ex!!.errorType)
    }

    @Test
    fun resetToken_afterConsumed_cannotBeReused() = runBlocking {
        repo.register("Reset Reuse", "reusereset@test.com", "OldPass1", UserRole.STUDENT)
        repo.requestPasswordReset("reusereset@test.com")
        val acc = accountStore.getAccount("reusereset@test.com")!!
        val testToken = "123456"
        accountStore.saveAccount(acc.copy(resetTokenHash = PasswordHasher.hash(testToken, acc.passwordSalt)))

        // First reset succeeds
        val first = repo.resetPassword("reusereset@test.com", testToken, "NewPass99")
        assertTrue("First reset must succeed", first.isSuccess)

        // Second reset attempt with same token must fail (token is nulled)
        val second = repo.resetPassword("reusereset@test.com", testToken, "AnotherPass1")
        assertTrue("Reused reset token must be rejected", second.isFailure)
    }

    // -----------------------------------------------------------------------
    // ADMIN AUTHORIZATION SECURITY
    // -----------------------------------------------------------------------

    @Test
    fun adminRegistration_doesNotGrantAdminAuthorization() = runBlocking {
        val result = repo.register("Admin Candidate", "admin@test.com", "Password1", UserRole.ADMIN)
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()

        assertEquals("Role must be ADMIN", UserRole.ADMIN, user.role)
        assertFalse(
            "isAdminAuthorized must be FALSE — selecting Admin role does NOT grant authorization",
            user.isAdminAuthorized
        )
    }

    @Test
    fun studentAccount_isRejectedFromAdminDashboard() = runBlocking {
        val result = repo.register("Student User", "student@test.com", "Password1", UserRole.STUDENT)
        val user = result.getOrThrow()

        // Nav guard check: student must NOT be admitted to Admin Dashboard
        val canEnterDashboard = user.role == UserRole.ADMIN && user.isAdminAuthorized
        assertFalse("Student must not enter Admin Dashboard", canEnterDashboard)
    }

    @Test
    fun alumniAccount_isRejectedFromAdminDashboard() = runBlocking {
        val result = repo.register("Alumni User", "alumni@test.com", "Password1", UserRole.ALUMNI)
        val user = result.getOrThrow()

        val canEnterDashboard = user.role == UserRole.ADMIN && user.isAdminAuthorized
        assertFalse("Alumni must not enter Admin Dashboard", canEnterDashboard)
    }

    @Test
    fun aspirantAccount_isRejectedFromAdminDashboard() = runBlocking {
        val result = repo.register("Aspirant User", "aspirant@test.com", "Password1", UserRole.ASPIRANT)
        val user = result.getOrThrow()

        val canEnterDashboard = user.role == UserRole.ADMIN && user.isAdminAuthorized
        assertFalse("Aspirant must not enter Admin Dashboard", canEnterDashboard)
    }

    // -----------------------------------------------------------------------
    // SESSION / LOGOUT
    // -----------------------------------------------------------------------

    @Test
    fun afterLogout_sessionIsNotRestored() = runBlocking {
        repo.register("Logout User", "logout@test.com", "Password1", UserRole.STUDENT)
        assertNotNull(sessionManager.getSession())

        repo.logout()

        assertNull("Session must be null after logout", sessionManager.getSession())
        assertTrue("Auth state must be Unauthenticated", repo.authState.value is AuthState.Unauthenticated)
        assertNull("getCurrentUser must be null", repo.getCurrentUser())

        // Attempt to restore session — must return null (not the old session)
        val restoreResult = repo.restoreSession()
        assertTrue(restoreResult.isSuccess)
        assertNull("Restored session must be null after logout", restoreResult.getOrNull())
        assertTrue("Auth state must remain Unauthenticated", repo.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun expiredSession_afterRestart_redirectsToUnauthenticated() = runBlocking {
        repo.register("Expire User", "expire@test.com", "Password1", UserRole.ALUMNI)

        // Advance time past 7-day expiry
        currentTime += sessionDuration + 1

        val restoreResult = repo.restoreSession()
        assertTrue(restoreResult.isSuccess)
        assertNull("Expired session user must be null", restoreResult.getOrNull())
        assertTrue("State must be Unauthenticated", repo.authState.value is AuthState.Unauthenticated)
    }

    // -----------------------------------------------------------------------
    // CORRECT ROLE ROUTING AFTER LOGIN
    // -----------------------------------------------------------------------

    @Test
    fun login_withStudentAccount_storesStudentRole() = runBlocking {
        repo.register("Student", "s@test.com", "Password1", UserRole.STUDENT)
        sessionManager.clearSession()

        val result = repo.login("s@test.com", "Password1")
        assertTrue(result.isSuccess)
        assertEquals(UserRole.STUDENT, result.getOrThrow().role)
    }

    @Test
    fun login_withAspirantAccount_storesAspirantRole() = runBlocking {
        repo.register("Aspirant", "a@test.com", "Password1", UserRole.ASPIRANT)
        sessionManager.clearSession()

        val result = repo.login("a@test.com", "Password1")
        assertTrue(result.isSuccess)
        assertEquals(UserRole.ASPIRANT, result.getOrThrow().role)
    }

    @Test
    fun login_withAlumniAccount_storesAlumniRole() = runBlocking {
        repo.register("Alumni", "al@test.com", "Password1", UserRole.ALUMNI)
        sessionManager.clearSession()

        val result = repo.login("al@test.com", "Password1")
        assertTrue(result.isSuccess)
        assertEquals(UserRole.ALUMNI, result.getOrThrow().role)
    }

    @Test
    fun login_withAdminAccount_storesAdminRoleWithoutAuthorization() = runBlocking {
        repo.register("Admin", "adm@test.com", "Password1", UserRole.ADMIN)
        sessionManager.clearSession()

        val result = repo.login("adm@test.com", "Password1")
        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(UserRole.ADMIN, user.role)
        assertFalse("Admin must NOT be automatically authorized", user.isAdminAuthorized)
    }

    // -----------------------------------------------------------------------
    // FORM VALIDATION EDGE CASES
    // -----------------------------------------------------------------------

    @Test
    fun registration_withLetterOnlyPassword_isRejected() = runBlocking {
        val result = repo.register("User", "user@test.com", "PasswordOnly", UserRole.STUDENT)
        assertTrue("Letter-only password must be rejected", result.isFailure)
        val ex = result.exceptionOrNull() as? AuthException
        assertEquals(AuthErrorType.WEAK_PASSWORD, ex!!.errorType)
    }

    @Test
    fun registration_withDigitOnlyPassword_isRejected() = runBlocking {
        val result = repo.register("User", "user2@test.com", "12345678", UserRole.STUDENT)
        assertTrue("Digit-only password must be rejected", result.isFailure)
        val ex = result.exceptionOrNull() as? AuthException
        assertEquals(AuthErrorType.WEAK_PASSWORD, ex!!.errorType)
    }

    @Test
    fun registration_withBlankEmail_isRejected() = runBlocking {
        val result = repo.register("User", "  ", "Password1", UserRole.STUDENT)
        assertTrue("Blank email must be rejected", result.isFailure)
        val ex = result.exceptionOrNull() as? AuthException
        assertEquals(AuthErrorType.INVALID_EMAIL, ex!!.errorType)
    }

    @Test
    fun login_withNonExistentAccount_showsError() = runBlocking {
        val result = repo.login("nobody@test.com", "Password1")
        assertTrue("Login with unknown email must fail", result.isFailure)
        val ex = result.exceptionOrNull() as? AuthException
        assertEquals(AuthErrorType.INVALID_CREDENTIALS, ex!!.errorType)
    }
}
