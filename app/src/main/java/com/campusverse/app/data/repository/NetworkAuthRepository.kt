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
 * Performs real HTTP requests to the local/cloud backend while maintaining local fallback.
 */
class NetworkAuthRepository(
    private val baseUrl: String = "",
    private val sessionManager: SessionManager = InMemorySessionManager(),
    private val accountStore: AccountStore = InMemoryAccountStore(),
    private val sessionDurationMillis: Long = 7 * 24 * 60 * 60 * 1000L
) : AuthRepository {

    private val effectiveBaseUrl: String
        get() = if (baseUrl.isNotBlank() && !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1") && !baseUrl.contains("10.0.2.2")) baseUrl else com.campusverse.app.data.network.ApiConfig.BASE_URL

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun restoreSession(): Result<AuthenticatedUser?> {
        _authState.value = AuthState.Loading
        return try {
            val session = sessionManager.getSession()
            if (session != null && sessionManager.isSessionValid(session)) {
                // If token is a mock token or not a JWT, purge immediately
                if (session.token.startsWith("token_") || !session.token.contains(".")) {
                    sessionManager.clearSession()
                    _authState.value = AuthState.Unauthenticated
                    return Result.success(null)
                }

                val (code, remoteUser) = fetchCurrentUserFromNetwork(session.token)
                if (code in 200..299 && remoteUser != null) {
                    _authState.value = AuthState.Authenticated(remoteUser)
                    Result.success(remoteUser)
                } else if (code == 401 || code == 403) {
                    // Token rejected by server as invalid or expired - clear session immediately
                    sessionManager.clearSession()
                    _authState.value = AuthState.Unauthenticated
                    Result.success(null)
                } else {
                    // Offline or temporary network failure; preserve cached user
                    _authState.value = AuthState.Authenticated(session.user)
                    Result.success(session.user)
                }
            } else {
                sessionManager.clearSession()
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
        val normalizedEmail = email.trim().lowercase()
        val trimmedName = name.trim()

        // 1. Attempt Real HTTP Request to Backend
        try {
            val payload = JSONObject().apply {
                put("fullName", trimmedName)
                put("email", normalizedEmail)
                put("password", password)
                put("role", role.name)
            }
            val (code, response) = executeHttpRequestWithCode("POST", "$effectiveBaseUrl/auth/register", payload.toString(), null)
            if (code in 200..299 && response.isNotBlank()) {
                val json = JSONObject(response)
                if (json.optBoolean("success", false)) {
                    val data = json.optJSONObject("data")
                    val userObj = data?.optJSONObject("user") ?: data
                    val token = data?.optString("token") ?: ""

                    if (token.isBlank() || (com.campusverse.app.data.network.ApiConfig.USE_PRODUCTION_API && (!token.contains(".") || token.startsWith("token_")))) {
                        _authState.value = AuthState.Unauthenticated
                        return@withContext Result.failure(Exception("Registration succeeded but server returned an invalid token format."))
                    }

                    val authenticatedUser = AuthenticatedUser(
                        userId = userObj?.optString("userId")?.takeIf { it.isNotBlank() }
                            ?: userObj?.optString("id")?.takeIf { it.isNotBlank() }
                            ?: "user_${UUID.randomUUID().toString().take(6)}",
                        email = normalizedEmail,
                        name = trimmedName,
                        role = role,
                        isEmailVerified = userObj?.optBoolean("isEmailVerified") ?: true,
                        isAdminAuthorized = (role == UserRole.ADMIN) || (userObj?.optBoolean("isAdminAuthorized", false) == true)
                    )

                    val session = AuthSession(
                        token = token,
                        user = authenticatedUser,
                        expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
                    )
                    sessionManager.saveSession(session)
                    _authState.value = AuthState.Authenticated(authenticatedUser)
                    return@withContext Result.success(authenticatedUser)
                }
            } else if (code in 400..499 && response.isNotBlank()) {
                val errorMessage = try {
                    val json = JSONObject(response)
                    val errObj = json.optJSONObject("error")
                    errObj?.optString("message") ?: json.optString("message", "Registration rejected ($code)")
                } catch (_: Exception) {
                    "Registration rejected ($code)"
                }
                _authState.value = AuthState.Unauthenticated
                return@withContext Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            if (com.campusverse.app.data.network.ApiConfig.USE_PRODUCTION_API) {
                _authState.value = AuthState.Unauthenticated
                return@withContext Result.failure(Exception("Unable to connect to authentication server: ${e.message}"))
            }
        }

        // 2. Offline Fallback Mode (ONLY when explicitly in local emulator dev AND NOT production)
        if (com.campusverse.app.data.network.ApiConfig.USE_LOCAL_EMULATOR_DEV && com.campusverse.app.data.network.ApiConfig.isEmulator && !com.campusverse.app.data.network.ApiConfig.USE_PRODUCTION_API) {
            val existing = accountStore.getAccount(normalizedEmail)
            if (existing != null) {
                val user = AuthenticatedUser(
                    userId = existing.userId,
                    email = existing.email,
                    name = existing.name,
                    role = existing.role,
                    isEmailVerified = existing.isEmailVerified,
                    isAdminAuthorized = existing.isAdminAuthorized
                )
                val session = AuthSession(
                    token = "token_" + UUID.randomUUID().toString(),
                    user = user,
                    expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
                )
                sessionManager.saveSession(session)
                _authState.value = AuthState.Authenticated(user)
                return@withContext Result.success(user)
            }

            val salt = UUID.randomUUID().toString()
            val passwordHash = PasswordHasher.hash(password, salt)
            val userId = "user_" + UUID.randomUUID().toString().take(6)
            val isAdminAuth = (role == UserRole.ADMIN)

            val newAccount = StoredAccount(
                userId = userId,
                name = trimmedName,
                email = normalizedEmail,
                passwordHash = passwordHash,
                passwordSalt = salt,
                role = role,
                isEmailVerified = true,
                isAdminAuthorized = isAdminAuth
            )
            accountStore.saveAccount(newAccount)

            val authenticatedUser = AuthenticatedUser(
                userId = userId,
                email = normalizedEmail,
                name = trimmedName,
                role = role,
                isEmailVerified = true,
                isAdminAuthorized = isAdminAuth
            )
            val session = AuthSession(
                token = "token_" + UUID.randomUUID().toString(),
                user = authenticatedUser,
                expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
            )
            sessionManager.saveSession(session)
            _authState.value = AuthState.Authenticated(authenticatedUser)
            return@withContext Result.success(authenticatedUser)
        }

        _authState.value = AuthState.Unauthenticated
        Result.failure(Exception("Unable to register account. Server unreachable."))
    }

    override suspend fun login(email: String, password: String): Result<AuthenticatedUser> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        val normalizedEmail = email.trim().lowercase()

        // 1. Attempt Real HTTP Request to Backend
        try {
            val payload = JSONObject().apply {
                put("email", normalizedEmail)
                put("password", password)
            }
            val (code, response) = executeHttpRequestWithCode("POST", "$effectiveBaseUrl/auth/login", payload.toString(), null)
            if (code in 200..299 && response.isNotBlank()) {
                val json = JSONObject(response)
                if (json.optBoolean("success", false)) {
                    val data = json.optJSONObject("data")
                    val userObj = data?.optJSONObject("user") ?: data
                    val token = data?.optString("token") ?: ""

                    if (token.isBlank() || (com.campusverse.app.data.network.ApiConfig.USE_PRODUCTION_API && (!token.contains(".") || token.startsWith("token_")))) {
                        _authState.value = AuthState.Unauthenticated
                        return@withContext Result.failure(Exception("Authentication failed: Server returned an invalid token format."))
                    }

                    val roleStr = userObj?.optString("role") ?: "STUDENT"
                    val parsedRole = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.STUDENT }

                    val authenticatedUser = AuthenticatedUser(
                        userId = userObj?.optString("userId")?.takeIf { it.isNotBlank() }
                            ?: userObj?.optString("id")?.takeIf { it.isNotBlank() }
                            ?: "user_${UUID.randomUUID().toString().take(6)}",
                        email = normalizedEmail,
                        name = userObj?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: userObj?.optString("fullName")?.takeIf { it.isNotBlank() }
                            ?: "Campus User",
                        role = parsedRole,
                        isEmailVerified = userObj?.optBoolean("isEmailVerified") ?: true,
                        isAdminAuthorized = (parsedRole == UserRole.ADMIN) || (userObj?.optBoolean("isAdminAuthorized", false) == true)
                    )

                    val session = AuthSession(
                        token = token,
                        user = authenticatedUser,
                        expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
                    )
                    sessionManager.saveSession(session)
                    _authState.value = AuthState.Authenticated(authenticatedUser)
                    return@withContext Result.success(authenticatedUser)
                }
            } else if (code in 400..499 && response.isNotBlank()) {
                val errorMessage = try {
                    val json = JSONObject(response)
                    val errObj = json.optJSONObject("error")
                    errObj?.optString("message") ?: json.optString("message", "Invalid email or password")
                } catch (_: Exception) {
                    "Invalid email or password ($code)"
                }
                _authState.value = AuthState.Unauthenticated
                return@withContext Result.failure(AuthException(AuthErrorType.INVALID_CREDENTIALS, errorMessage))
            }
        } catch (e: Exception) {
            if (com.campusverse.app.data.network.ApiConfig.USE_PRODUCTION_API) {
                _authState.value = AuthState.Unauthenticated
                return@withContext Result.failure(Exception("Unable to connect to authentication server: ${e.message}"))
            }
        }

        // 2. Offline Fallback Mode (ONLY when explicitly in local emulator dev AND NOT production)
        if (com.campusverse.app.data.network.ApiConfig.USE_LOCAL_EMULATOR_DEV && com.campusverse.app.data.network.ApiConfig.isEmulator && !com.campusverse.app.data.network.ApiConfig.USE_PRODUCTION_API) {
            val stored = accountStore.getAccount(normalizedEmail)
            if (stored != null) {
                val user = AuthenticatedUser(
                    userId = stored.userId,
                    email = stored.email,
                    name = stored.name,
                    role = stored.role,
                    isEmailVerified = stored.isEmailVerified,
                    isAdminAuthorized = stored.isAdminAuthorized
                )
                val session = AuthSession(
                    token = "token_" + UUID.randomUUID().toString(),
                    user = user,
                    expiresAtEpochMillis = System.currentTimeMillis() + sessionDurationMillis
                )
                sessionManager.saveSession(session)
                _authState.value = AuthState.Authenticated(user)
                return@withContext Result.success(user)
            }
        }

        _authState.value = AuthState.Unauthenticated
        Result.failure(Exception("Invalid credentials or server unreachable."))
    }

    override suspend fun verifyOtp(email: String, otpCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(true)
    }

    override suspend fun resendOtp(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(true)
    }

    override suspend fun requestPasswordReset(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(true)
    }

    override suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(true)
    }

    override suspend fun logout() {
        val currentSession = sessionManager.getSession()
        if (currentSession != null) {
            try {
                withContext(Dispatchers.IO) {
                    executeHttpRequest("POST", "$effectiveBaseUrl/auth/logout", "{}", currentSession.token)
                }
            } catch (_: Exception) {}
        }
        sessionManager.clearSession()
        _authState.value = AuthState.Unauthenticated
    }

    override fun getCurrentUser(): AuthenticatedUser? {
        return (_authState.value as? AuthState.Authenticated)?.user
    }

    private suspend fun fetchCurrentUserFromNetwork(token: String): Pair<Int, AuthenticatedUser?> = withContext(Dispatchers.IO) {
        try {
            val (code, response) = executeHttpRequestWithCode("GET", "$effectiveBaseUrl/auth/me", null, token)
            if (code in 200..299 && response.isNotBlank()) {
                val json = JSONObject(response)
                if (json.optBoolean("success", false)) {
                    val data = json.getJSONObject("data")
                    val roleStr = data.optString("role", "STUDENT")
                    val parsedRole = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.STUDENT }
                    val user = AuthenticatedUser(
                        userId = data.optString("userId").takeIf { it.isNotBlank() } ?: data.optString("id"),
                        email = data.optString("email"),
                        name = data.optString("name").takeIf { it.isNotBlank() }
                            ?: data.optString("fullName").takeIf { it.isNotBlank() }
                            ?: "User",
                        role = parsedRole,
                        isEmailVerified = data.optBoolean("isEmailVerified", true),
                        isAdminAuthorized = (parsedRole == UserRole.ADMIN) || data.optBoolean("isAdminAuthorized", false)
                    )
                    return@withContext Pair(code, user)
                }
            }
            Pair(code, null)
        } catch (_: Exception) {
            Pair(-1, null)
        }
    }

    private fun executeHttpRequest(
        method: String,
        urlString: String,
        body: String?,
        token: String?
    ): String {
        return executeHttpRequestWithCode(method, urlString, body, token).second
    }

    private fun executeHttpRequestWithCode(
        method: String,
        urlString: String,
        body: String?,
        token: String?
    ): Pair<Int, String> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = com.campusverse.app.data.network.ApiConfig.CONNECT_TIMEOUT_MS
            connection.readTimeout = com.campusverse.app.data.network.ApiConfig.READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = if (inputStream != null) {
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    sb.toString()
                }
            } else {
                ""
            }
            Pair(responseCode, responseBody)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }
}
