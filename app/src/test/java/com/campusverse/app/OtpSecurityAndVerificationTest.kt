package com.campusverse.app

import com.campusverse.app.data.account.InMemoryAccountStore
import com.campusverse.app.data.local.PasswordHasher
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.session.InMemorySessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OtpSecurityAndVerificationTest {

    private lateinit var store: InMemoryAccountStore
    private lateinit var sessionManager: InMemorySessionManager
    private var currentTime: Long = 1000000000L
    private lateinit var authRepository: AuthRepositoryImpl

    @Before
    fun setUp() {
        store = InMemoryAccountStore()
        sessionManager = InMemorySessionManager()
        currentTime = 1000000000L
        authRepository = AuthRepositoryImpl(
            accountStore = store,
            sessionManager = sessionManager,
            timeProvider = { currentTime }
        )
    }

    @Test
    fun test1_registrationGeneratesHashedOtpAndNeverStoresPlaintext() = runBlocking {
        val email = "student@test.edu"
        val result = authRepository.register("Student User", email, "Password123", UserRole.STUDENT)
        assertTrue(result.isSuccess)

        val account = store.getAccount(email)
        assertNotNull(account)
        assertNotNull(account!!.otpHash)
        assertEquals(64, account.otpHash!!.length) // SHA-256 length
        assertEquals(0, account.otpAttemptCount)
        assertEquals(currentTime + 5 * 60 * 1000L, account.otpExpiresAt)
        assertNull(authRepository.getActiveOtpForDisplay(email))
    }

    @Test
    fun test2_invalidOtpIncrementsAttemptCountAndLocksOutAfter5Attempts() = runBlocking {
        val email = "lockout@test.edu"
        authRepository.register("Lockout User", email, "Password123", UserRole.STUDENT)

        for (i in 1..4) {
            val failRes = authRepository.verifyOtp(email, "000000")
            assertFalse(failRes.isSuccess)
            val acc = store.getAccount(email)
            assertEquals(i, acc!!.otpAttemptCount)
        }

        // 5th attempt triggers lockout
        val fifthRes = authRepository.verifyOtp(email, "000000")
        assertFalse(fifthRes.isSuccess)
        val lockedAcc = store.getAccount(email)
        assertNull(lockedAcc!!.otpHash) // OTP Hash invalidated permanently
    }

    @Test
    fun test3_expiredOtpIsRejected() = runBlocking {
        val email = "expired@test.edu"
        authRepository.register("Expired User", email, "Password123", UserRole.STUDENT)

        // Advance time past 5 minutes
        currentTime += 6 * 60 * 1000L

        val res = authRepository.verifyOtp(email, "123456")
        assertFalse(res.isSuccess)
        assertTrue(res.exceptionOrNull()?.message?.contains("expired", ignoreCase = true) == true)
    }

    @Test
    fun test4_resendOtpEnforces60SecondCooldown() = runBlocking {
        val email = "cooldown@test.edu"
        authRepository.register("Cooldown User", email, "Password123", UserRole.STUDENT)

        // Immediate resend must fail
        val immediateResend = authRepository.resendOtp(email)
        assertFalse(immediateResend.isSuccess)
        assertTrue(immediateResend.exceptionOrNull()?.message?.contains("wait", ignoreCase = true) == true)

        // Advance 61 seconds
        currentTime += 61 * 1000L
        val successResend = authRepository.resendOtp(email)
        assertTrue(successResend.isSuccess)
    }

    @Test
    fun test5_successfulOtpVerificationInvalidatesCodeAndMarksEmailVerified() = runBlocking {
        val email = "verify_success@test.edu"
        val user = authRepository.register("Verify Success", email, "Password123", UserRole.ASPIRANT).getOrThrow()
        assertFalse(user.isEmailVerified)

        val account = store.getAccount(email)!!
        val salt = account.passwordSalt
        // Simulate knowing the OTP generated for test verification
        val rawOtp = "889900"
        val knownHash = PasswordHasher.hash(rawOtp, salt)
        store.saveAccount(account.copy(otpHash = knownHash))

        val verifyResult = authRepository.verifyOtp(email, rawOtp)
        assertTrue(verifyResult.isSuccess)

        val updatedAccount = store.getAccount(email)!!
        assertTrue(updatedAccount.isEmailVerified)
        assertNull(updatedAccount.otpHash) // Single-use OTP cleared

        // Replay attempt must fail
        val replayResult = authRepository.verifyOtp(email, rawOtp)
        assertFalse(replayResult.isSuccess)
    }
}
