package com.campusverse.app.data.local

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-based password hashing utility.
 *
 * Uses PBKDF2WithHmacSHA256 with a per-user UUID salt.
 * This is the Java standard-library equivalent of bcrypt for environments
 * that cannot depend on an external crypto library.
 *
 * Production recommendation: increase ITERATIONS to ≥ 100,000 once
 * a backend auth service replaces this local implementation.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256

    /**
     * Hashes [password] using PBKDF2 with the provided [salt].
     * The [salt] should be unique per user (e.g., the user's UUID).
     *
     * @return Hex-encoded hash string.
     */
    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val hashBytes = factory.generateSecret(spec).encoded
            hashBytes.joinToString("") { "%02x".format(it) }
        } finally {
            // Clear the password from memory after use
            spec.clearPassword()
        }
    }

    /**
     * Verifies [plaintext] against a previously stored [storedHash] and [salt].
     * Uses a constant-time comparison to resist timing attacks.
     */
    fun verify(plaintext: String, salt: String, storedHash: String): Boolean {
        val candidateHash = hash(plaintext, salt)
        // Constant-time comparison to prevent timing-based attacks
        if (candidateHash.length != storedHash.length) return false
        var diff = 0
        for (i in candidateHash.indices) {
            diff = diff or (candidateHash[i].code xor storedHash[i].code)
        }
        return diff == 0
    }
}
