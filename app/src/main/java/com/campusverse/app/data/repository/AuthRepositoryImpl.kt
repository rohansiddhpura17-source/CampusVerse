package com.campusverse.app.data.repository

import com.campusverse.app.data.account.AccountStore
import com.campusverse.app.data.account.InMemoryAccountStore
import com.campusverse.app.data.account.StoredAccount
import com.campusverse.app.data.local.PasswordHasher
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.domain.auth.AuthErrorType
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthRepository
import com.campusverse.app.domain.auth.AuthSession
import com.campusverse.app.domain.auth.AuthState
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.domain.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.regex.Pattern

/**
 * Thread-safe implementation of [AuthRepository] managing user persistence,
 * strict validation, role persistence, and session lifecycles.
 */
class AuthRepositoryImpl(
    private val sessionManager: SessionManager = InMemorySessionManager(),
    private val accountStore: AccountStore = InMemoryAccountStore(),
    private val sessionDurationMillis: Long = 7 * 24 * 60 * 60 * 1000L, // 7 days
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val enableDevOtpDisplay: Boolean = false
) : AuthRepository {

    private val mutex = Mutex()
    private val devOtpStore = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private val EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$"
        )
        // Singleton instance for in-app shared repository, initialized by CampusVerseApplication
        @Volatile
        var instance: AuthRepository = AuthRepositoryImpl()
    }

    override suspend fun restoreSession(): Result<AuthenticatedUser?> {
        _authState.value = AuthState.Loading
        return try {
            val session = sessionManager.getSession()
            if (session != null && sessionManager.isSessionValid(session)) {
                _authState.value = AuthState.Authenticated(session.user)
                Result.success(session.user)
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.success(null)
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error(
                message = e.message ?: "Failed to restore session.",
                errorType = AuthErrorType.UNKNOWN
            )
            Result.failure(e)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): Result<AuthenticatedUser> {
        _authState.value = AuthState.Loading

        val trimmedName = name.trim()
        val normalizedEmail = email.trim().lowercase()

        // 1. Validation
        if (trimmedName.isBlank()) {
            val err = AuthException(AuthErrorType.INVALID_CREDENTIALS, "Name cannot be empty.")
            _authState.value = AuthState.Error(err.message, err.errorType)
            return Result.failure(err)
        }

        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            val err = AuthException(AuthErrorType.INVALID_EMAIL, "Please enter a valid email address.")
            _authState.value = AuthState.Error(err.message, err.errorType)
            return Result.failure(err)
        }

        if (password.length < 8) {
            val err = AuthException(
                AuthErrorType.WEAK_PASSWORD,
                "Password must be at least 8 characters long."
            )
            _authState.value = AuthState.Error(err.message, err.errorType)
            return Result.failure(err)
        }

        if (!password.any { it.isDigit() } || !password.any { it.isLetter() }) {
            val err = AuthException(
                AuthErrorType.WEAK_PASSWORD,
                "Password must contain both letters and numbers."
            )
            _authState.value = AuthState.Error(err.message, err.errorType)
            return Result.failure(err)
        }

        return mutex.withLock {
            val existing = accountStore.getAccount(normalizedEmail)
            if (existing != null) {
                val err = AuthException(
                    AuthErrorType.EMAIL_ALREADY_EXISTS,
                    "An account with this email already exists."
                )
                _authState.value = AuthState.Error(err.message, err.errorType)
                return@withLock Result.failure(err)
            }

            val userId = "user_${UUID.randomUUID().toString().take(8)}"
            // Admin role requires backend/admin authorization before granting full admin dashboard access
            val isAdminAuthorized = false

            val salt = UUID.randomUUID().toString()
            val passwordHash = PasswordHasher.hash(password, salt)
            
            // Cryptographically secure 6-digit OTP generation
            val secureRandom = java.security.SecureRandom()
            val rawOtp = (100000 + secureRandom.nextInt(900000)).toString()
            val otpHash = PasswordHasher.hash(rawOtp, salt)
            val now = timeProvider()
            devOtpStore[normalizedEmail] = rawOtp

            val newAccount = StoredAccount(
                userId = userId,
                name = trimmedName,
                email = normalizedEmail,
                passwordHash = passwordHash,
                passwordSalt = salt,
                role = role,
                isEmailVerified = false,
                isAdminAuthorized = isAdminAuthorized,
                otpHash = otpHash,
                otpExpiresAt = now + 5 * 60 * 1000L,
                otpAttemptCount = 0,
                otpCreatedAt = now
            )

            accountStore.saveAccount(newAccount)

            val authenticatedUser = AuthenticatedUser(
                userId = userId,
                email = normalizedEmail,
                name = trimmedName,
                role = role,
                isEmailVerified = false,
                isAdminAuthorized = isAdminAuthorized
            )

            val session = AuthSession(
                token = "token_${UUID.randomUUID()}",
                user = authenticatedUser,
                expiresAtEpochMillis = timeProvider() + sessionDurationMillis
            )

            sessionManager.saveSession(session)
            _authState.value = AuthState.Authenticated(authenticatedUser)
            Result.success(authenticatedUser)
        }
    }

    override suspend fun login(email: String, password: String): Result<AuthenticatedUser> {
        _authState.value = AuthState.Loading

        val normalizedEmail = email.trim().lowercase()

        if (normalizedEmail.isBlank() || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            val err = AuthException(AuthErrorType.INVALID_EMAIL, "Please enter a valid email address.")
            _authState.value = AuthState.Error(err.message, err.errorType)
            return Result.failure(err)
        }

        if (password.isBlank()) {
            val err = AuthException(AuthErrorType.INVALID_CREDENTIALS, "Password cannot be empty.")
            _authState.value = AuthState.Error(err.message, err.errorType)
            return Result.failure(err)
        }

        return mutex.withLock {
            val stored = accountStore.getAccount(normalizedEmail)
            val isPasswordValid = if (stored != null) {
                if (stored.passwordSalt.isNotEmpty()) {
                    PasswordHasher.verify(password, stored.passwordSalt, stored.passwordHash)
                } else {
                    stored.passwordHash == password
                }
            } else {
                false
            }

            if (stored == null || !isPasswordValid) {
                val err = AuthException(
                    AuthErrorType.INVALID_CREDENTIALS,
                    "Invalid email or password. Please try again."
                )
                _authState.value = AuthState.Error(err.message, err.errorType)
                return@withLock Result.failure(err)
            }

            val user = AuthenticatedUser(
                userId = stored.userId,
                email = stored.email,
                name = stored.name,
                role = stored.role,
                isEmailVerified = stored.isEmailVerified,
                isAdminAuthorized = stored.isAdminAuthorized
            )

            val session = AuthSession(
                token = "token_${UUID.randomUUID()}",
                user = user,
                expiresAtEpochMillis = timeProvider() + sessionDurationMillis
            )

            sessionManager.saveSession(session)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        }
    }

    override suspend fun verifyOtp(email: String, otpCode: String): Result<Boolean> {
        val normalizedEmail = email.trim().lowercase()
        val trimmedOtp = otpCode.trim()

        if (trimmedOtp.length != 6 || !trimmedOtp.all { it.isDigit() }) {
            return Result.failure(AuthException(AuthErrorType.VERIFICATION_FAILURE, "Please enter a valid 6-digit verification code."))
        }

        return mutex.withLock {
            val stored = accountStore.getAccount(normalizedEmail)
            if (stored == null) {
                val err = AuthException(AuthErrorType.VERIFICATION_FAILURE, "User account not found.")
                return@withLock Result.failure(err)
            }

            val now = timeProvider()

            // Expiration check
            if (stored.otpHash == null || now > stored.otpExpiresAt) {
                val err = AuthException(AuthErrorType.VERIFICATION_FAILURE, "Verification code has expired. Please request a new code.")
                return@withLock Result.failure(err)
            }

            // Attempt limit check
            if (stored.otpAttemptCount >= 5) {
                val updatedAccount = stored.copy(otpHash = null)
                accountStore.saveAccount(updatedAccount)
                val err = AuthException(AuthErrorType.VERIFICATION_FAILURE, "Maximum verification attempts exceeded. Please request a new code.")
                return@withLock Result.failure(err)
            }

            // Verify hash
            val isValid = PasswordHasher.verify(trimmedOtp, stored.passwordSalt, stored.otpHash)

            if (!isValid) {
                val newCount = stored.otpAttemptCount + 1
                val isLockout = newCount >= 5
                val updatedAccount = stored.copy(
                    otpAttemptCount = newCount,
                    otpHash = if (isLockout) null else stored.otpHash
                )
                accountStore.saveAccount(updatedAccount)

                val err = AuthException(
                    AuthErrorType.VERIFICATION_FAILURE,
                    if (isLockout) "Maximum verification attempts exceeded. Please request a new code."
                    else "Invalid verification code. Please check and try again."
                )
                return@withLock Result.failure(err)
            }

            // Success — invalidate OTP permanently
            val updatedAccount = stored.copy(
                isEmailVerified = true,
                otpHash = null,
                otpExpiresAt = 0L,
                otpAttemptCount = 0
            )
            accountStore.saveAccount(updatedAccount)
            devOtpStore.remove(normalizedEmail)

            // Update active session if belongs to current user
            val currentSession = sessionManager.getSession()
            if (currentSession != null && currentSession.user.email == normalizedEmail) {
                val updatedUser = currentSession.user.copy(isEmailVerified = true)
                sessionManager.saveSession(currentSession.copy(user = updatedUser))
                _authState.value = AuthState.Authenticated(updatedUser)
            }

            Result.success(true)
        }
    }

    override suspend fun resendOtp(email: String): Result<Boolean> {
        val normalizedEmail = email.trim().lowercase()
        return mutex.withLock {
            val stored = accountStore.getAccount(normalizedEmail)
            if (stored == null) {
                val err = AuthException(AuthErrorType.INVALID_EMAIL, "No account associated with this email.")
                return@withLock Result.failure(err)
            }

            val now = timeProvider()
            val elapsedSeconds = (now - stored.otpCreatedAt) / 1000
            if (stored.otpCreatedAt > 0L && elapsedSeconds < 60) {
                val remaining = 60 - elapsedSeconds
                return@withLock Result.failure(AuthException(AuthErrorType.UNKNOWN, "Please wait $remaining seconds before requesting another code."))
            }

            val secureRandom = java.security.SecureRandom()
            val rawOtp = (100000 + secureRandom.nextInt(900000)).toString()
            val newHash = PasswordHasher.hash(rawOtp, stored.passwordSalt)
            devOtpStore[normalizedEmail] = rawOtp

            accountStore.saveAccount(
                stored.copy(
                    otpHash = newHash,
                    otpExpiresAt = now + 5 * 60 * 1000L,
                    otpAttemptCount = 0,
                    otpCreatedAt = now
                )
            )
            Result.success(true)
        }
    }

    override suspend fun getActiveOtpForDisplay(email: String): String? {
        if (!enableDevOtpDisplay) return null
        return devOtpStore[email.trim().lowercase()]
    }

    override suspend fun requestPasswordReset(email: String): Result<Boolean> {
        val normalizedEmail = email.trim().lowercase()
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            val err = AuthException(AuthErrorType.INVALID_EMAIL, "Please enter a valid email address.")
            return Result.failure(err)
        }

        return mutex.withLock {
            val stored = accountStore.getAccount(normalizedEmail)
            if (stored == null) {
                // Enumeration protection
                return@withLock Result.success(true)
            }

            val now = timeProvider()
            val secureRandom = java.security.SecureRandom()
            val rawToken = (100000 + secureRandom.nextInt(900000)).toString()
            val resetHash = PasswordHasher.hash(rawToken, stored.passwordSalt)
            devOtpStore[normalizedEmail] = rawToken

            accountStore.saveAccount(
                stored.copy(
                    resetTokenHash = resetHash,
                    resetTokenExpiresAt = now + 5 * 60 * 1000L,
                    resetTokenAttemptCount = 0
                )
            )
            Result.success(true)
        }
    }

    override suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): Result<Boolean> {
        val normalizedEmail = email.trim().lowercase()
        val trimmedToken = token.trim()

        if (newPassword.length < 8 || !newPassword.any { it.isDigit() } || !newPassword.any { it.isLetter() }) {
            val err = AuthException(
                AuthErrorType.WEAK_PASSWORD,
                "New password must be at least 8 characters and contain both letters and numbers."
            )
            return Result.failure(err)
        }

        return mutex.withLock {
            val stored = accountStore.getAccount(normalizedEmail)
            if (stored == null) {
                val err = AuthException(AuthErrorType.INVALID_EMAIL, "Account not found.")
                return@withLock Result.failure(err)
            }

            val now = timeProvider()
            if (stored.resetTokenHash == null || now > stored.resetTokenExpiresAt) {
                val err = AuthException(
                    AuthErrorType.INVALID_CREDENTIALS,
                    "Invalid or expired password reset code."
                )
                return@withLock Result.failure(err)
            }

            val isValid = PasswordHasher.verify(trimmedToken, stored.passwordSalt, stored.resetTokenHash)
            if (!isValid) {
                val newCount = stored.resetTokenAttemptCount + 1
                accountStore.saveAccount(
                    stored.copy(
                        resetTokenAttemptCount = newCount,
                        resetTokenHash = if (newCount >= 5) null else stored.resetTokenHash
                    )
                )
                val err = AuthException(
                    AuthErrorType.INVALID_CREDENTIALS,
                    if (newCount >= 5) "Maximum reset attempts exceeded. Please request a new code."
                    else "Invalid or expired password reset code."
                )
                return@withLock Result.failure(err)
            }

            val newSalt = UUID.randomUUID().toString()
            val newHash = PasswordHasher.hash(newPassword, newSalt)

            val updatedAccount = stored.copy(
                passwordHash = newHash,
                passwordSalt = newSalt,
                resetTokenHash = null,
                resetTokenExpiresAt = 0L,
                resetTokenAttemptCount = 0
            )
            accountStore.saveAccount(updatedAccount)
            Result.success(true)
        }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
        _authState.value = AuthState.Unauthenticated
    }

    override fun getCurrentUser(): AuthenticatedUser? {
        return (_authState.value as? AuthState.Authenticated)?.user
    }
}
