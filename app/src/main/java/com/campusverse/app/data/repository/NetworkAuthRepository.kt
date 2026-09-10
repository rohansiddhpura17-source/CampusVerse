package com.campusverse.app.data.repository

import com.campusverse.app.data.model.UserRole
import com.campusverse.app.domain.auth.AuthErrorType
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthRepository
import com.campusverse.app.domain.auth.AuthSession
import com.campusverse.app.domain.auth.AuthState
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.domain.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Production implementation of [AuthRepository] communicating with the CampusVerse REST API.
 * Proves Android -> HTTPS API -> Backend Services -> Database integration.
 */
class NetworkAuthRepository(
    private val baseUrl: String = "http://10.0.2.2:4000/api/v1",
    private val sessionManager: SessionManager = InMemorySessionManager(),
    private val sessionDurationMillis: Long = 7 * 24 * 60 * 60 * 1000L
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun restoreSession(): Result<AuthenticatedUser?> {
        _authState.value = AuthState.Loading
        return try {
            val session = sessionManager.getSession()
            if (session != null && sessionManager.isSessionValid(session)) {
                // Validate session token with backend GET /auth/me
                val remoteUser = fetchCurrentUserFromNetwork(session.token)
                if (remoteUser != null) {
                    _authState.value = AuthState.Authenticated(remoteUser)
                    Result.success(remoteUser)
                } else {
                    // Fallback to local session if network temporarily unreachable
                    _authState.value = AuthState.Authenticated(session.user)
                    Result.success(session.user)
                }
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.success(null)
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
            Result.success(null)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): Result<AuthenticatedUser> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            val payload = JSONObject().apply {
                put("name", name.trim())
                put("email", email.trim().lowercase())
                put("password", password)
                put("role", role.name)
            }

            val response = executeHttpRequest("POST", "$baseUrl/auth/register", payload.toString(), null)
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Registration failed."
                val err = AuthException(AuthErrorType.EMAIL_ALREADY_EXISTS, errorMsg)
                _authState.value = AuthState.Error(err.message, err.errorType)
                return@withContext Result.failure(err)
            }

            val data = json.getJSONObject("data")
            val token = data.getString("token")
            val userObj = data.getJSONObject("user")

            val user = AuthenticatedUser(
                userId = userObj.getString("userId"),
                email = userObj.getString("email"),
                name = userObj.getString("name"),
                role = UserRole.valueOf(userObj.getString("role")),
                isEmailVerified = userObj.optBoolean("isEmailVerified", false),
                isAdminAuthorized = userObj.optBoolean("isAdminAuthorized", false)
            )

            val session = AuthSession(
                token = token,
                user = user,
                expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
            )

            sessionManager.saveSession(session)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val err = AuthException(AuthErrorType.NETWORK_FAILURE, e.message ?: "Network error during registration.")
            _authState.value = AuthState.Error(err.message, err.errorType)
            Result.failure(err)
        }
    }

    override suspend fun login(email: String, password: String): Result<AuthenticatedUser> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }

            val response = executeHttpRequest("POST", "$baseUrl/auth/login", payload.toString(), null)
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Invalid credentials."
                val err = AuthException(AuthErrorType.INVALID_CREDENTIALS, errorMsg)
                _authState.value = AuthState.Error(err.message, err.errorType)
                return@withContext Result.failure(err)
            }

            val data = json.getJSONObject("data")
            val token = data.getString("token")
            val userObj = data.getJSONObject("user")

            val user = AuthenticatedUser(
                userId = userObj.getString("userId"),
                email = userObj.getString("email"),
                name = userObj.getString("name"),
                role = UserRole.valueOf(userObj.getString("role")),
                isEmailVerified = userObj.optBoolean("isEmailVerified", false),
                isAdminAuthorized = userObj.optBoolean("isAdminAuthorized", false)
            )

            val session = AuthSession(
                token = token,
                user = user,
                expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
            )

            sessionManager.saveSession(session)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val err = AuthException(AuthErrorType.INVALID_CREDENTIALS, e.message ?: "Authentication failed.")
            _authState.value = AuthState.Error(err.message, err.errorType)
            Result.failure(err)
        }
    }

    override suspend fun verifyOtp(email: String, otpCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("otp", otpCode.trim())
                put("purpose", "EMAIL_VERIFICATION")
            }

            val response = executeHttpRequest("POST", "$baseUrl/auth/verify-otp", payload.toString(), null)
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Verification failed."
                return@withContext Result.failure(AuthException(AuthErrorType.VERIFICATION_FAILURE, errorMsg))
            }

            // Update cached session user if current user is logged in
            val session = sessionManager.getSession()
            if (session != null && session.user.email.equals(email.trim(), ignoreCase = true)) {
                val updatedUser = session.user.copy(isEmailVerified = true)
                sessionManager.saveSession(session.copy(user = updatedUser))
                _authState.value = AuthState.Authenticated(updatedUser)
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(AuthException(AuthErrorType.NETWORK_FAILURE, e.message ?: "Network error during OTP verification."))
        }
    }

    override suspend fun resendOtp(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("purpose", "EMAIL_VERIFICATION")
            }

            val response = executeHttpRequest("POST", "$baseUrl/auth/send-otp", payload.toString(), null)
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Failed to resend code."
                return@withContext Result.failure(AuthException(AuthErrorType.UNKNOWN, errorMsg))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(AuthException(AuthErrorType.NETWORK_FAILURE, e.message ?: "Network error during OTP resend."))
        }
    }

    override suspend fun requestPasswordReset(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
            }

            val response = executeHttpRequest("POST", "$baseUrl/auth/forgot-password", payload.toString(), null)
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Password reset request failed."
                return@withContext Result.failure(AuthException(AuthErrorType.UNKNOWN, errorMsg))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(AuthException(AuthErrorType.NETWORK_FAILURE, e.message ?: "Network error during password reset request."))
        }
    }

    override suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("otp", token.trim())
                put("newPassword", newPassword)
            }

            val response = executeHttpRequest("POST", "$baseUrl/auth/reset-password", payload.toString(), null)
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val errorMsg = json.optJSONObject("error")?.optString("message") ?: "Password reset failed."
                return@withContext Result.failure(AuthException(AuthErrorType.INVALID_CREDENTIALS, errorMsg))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(AuthException(AuthErrorType.NETWORK_FAILURE, e.message ?: "Network error during password reset."))
        }
    }

    override suspend fun logout() {
        val currentSession = sessionManager.getSession()
        if (currentSession != null) {
            try {
                withContext(Dispatchers.IO) {
                    executeHttpRequest("POST", "$baseUrl/auth/logout", "{}", currentSession.token)
                }
            } catch (_: Exception) {}
        }
        sessionManager.clearSession()
        _authState.value = AuthState.Unauthenticated
    }

    override fun getCurrentUser(): AuthenticatedUser? {
        return (_authState.value as? AuthState.Authenticated)?.user
    }

    private suspend fun fetchCurrentUserFromNetwork(token: String): AuthenticatedUser? = withContext(Dispatchers.IO) {
        try {
            val response = executeHttpRequest("GET", "$baseUrl/auth/me", null, token)
            val json = JSONObject(response)
            if (json.optBoolean("success", false)) {
                val data = json.getJSONObject("data")
                AuthenticatedUser(
                    userId = data.getString("userId"),
                    email = data.getString("email"),
                    name = data.getString("name"),
                    role = UserRole.valueOf(data.getString("role")),
                    isEmailVerified = data.optBoolean("isEmailVerified", false),
                    isAdminAuthorized = data.optBoolean("isAdminAuthorized", false)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun executeHttpRequest(
        method: String,
        urlString: String,
        body: String?,
        token: String?
    ): String {
        println("[CampusVerseNet] Auth HTTP $method -> $urlString (authHeader: ${!token.isNullOrBlank()})")
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Accept", "application/json")

        if (!token.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }

        if (body != null && (method == "POST" || method == "PATCH" || method == "PUT")) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(body)
                writer.flush()
            }
        }

        val statusCode = conn.responseCode
        println("[CampusVerseNet] Auth HTTP response code: $statusCode for $urlString")
        val inputStream = if (statusCode in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        return BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
    }
}
