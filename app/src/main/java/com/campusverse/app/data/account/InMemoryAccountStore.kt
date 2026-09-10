package com.campusverse.app.data.account

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory thread-safe implementation of [AccountStore] for isolated testing.
 */
class InMemoryAccountStore : AccountStore {
    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, StoredAccount>()

    override suspend fun getAccount(email: String): StoredAccount? = mutex.withLock {
        accounts[email.trim().lowercase()]
    }

    override suspend fun saveAccount(account: StoredAccount) = mutex.withLock {
        accounts[account.email.trim().lowercase()] = account
    }

    override suspend fun getAllAccounts(): List<StoredAccount> = mutex.withLock {
        accounts.values.toList()
    }

    override suspend fun clear() = mutex.withLock {
        accounts.clear()
    }
}
