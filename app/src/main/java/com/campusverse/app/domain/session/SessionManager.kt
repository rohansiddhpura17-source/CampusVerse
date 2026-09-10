package com.campusverse.app.domain.session

import com.campusverse.app.domain.auth.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Contract for managing persisted authentication sessions.
 */
interface SessionManager {
    suspend fun saveSession(session: AuthSession)
    suspend fun getSession(): AuthSession?
    suspend fun clearSession()
    suspend fun updateName(name: String)
    suspend fun updateEmail(email: String)
    suspend fun updatePhoto(photoUrl: String?)
    suspend fun updateUserData(name: String? = null, email: String? = null)
    fun getSessionFlow(): Flow<AuthSession?>
    fun isSessionValid(session: AuthSession?): Boolean
}

/**
 * Thread-safe implementation of [SessionManager].
 *
 * @property timeProvider Supplier of current time in milliseconds, customizable for tests.
 */
class InMemorySessionManager(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : SessionManager {

    private val mutex = Mutex()
    private val _sessionFlow = MutableStateFlow<AuthSession?>(null)

    override suspend fun saveSession(session: AuthSession) {
        mutex.withLock {
            _sessionFlow.value = session
        }
    }

    override suspend fun getSession(): AuthSession? {
        return mutex.withLock {
            val session = _sessionFlow.value
            if (session != null && isSessionValid(session)) {
                session
            } else if (session != null && !isSessionValid(session)) {
                // Auto purge expired session
                _sessionFlow.value = null
                null
            } else {
                null
            }
        }
    }

    override suspend fun clearSession() {
        mutex.withLock {
            _sessionFlow.value = null
        }
    }

    override suspend fun updateName(name: String) {
        mutex.withLock {
            val current = _sessionFlow.value ?: return@withLock
            val updatedUser = current.user.copy(name = name)
            _sessionFlow.value = current.copy(user = updatedUser)
        }
    }

    override suspend fun updateEmail(email: String) {
        mutex.withLock {
            val current = _sessionFlow.value ?: return@withLock
            val updatedUser = current.user.copy(email = email)
            _sessionFlow.value = current.copy(user = updatedUser)
        }
    }

    override suspend fun updatePhoto(photoUrl: String?) {
        // No-op or photo property if added to AuthenticatedUser
    }

    override suspend fun updateUserData(name: String?, email: String?) {
        mutex.withLock {
            val current = _sessionFlow.value ?: return@withLock
            val updatedUser = current.user.copy(
                name = name ?: current.user.name,
                email = email ?: current.user.email
            )
            _sessionFlow.value = current.copy(user = updatedUser)
        }
    }

    override fun getSessionFlow(): Flow<AuthSession?> = _sessionFlow.asStateFlow()

    override fun isSessionValid(session: AuthSession?): Boolean {
        if (session == null) return false
        return !session.isExpired(timeProvider())
    }
}
