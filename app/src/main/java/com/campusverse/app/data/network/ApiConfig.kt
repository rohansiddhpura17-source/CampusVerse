package com.campusverse.app.data.network

import android.os.Build
import com.campusverse.app.BuildConfig
import java.net.URI

/**
 * Production Centralized API Configuration for CampusVerse.
 * Ensures the physical installed app and release variants connect to the
 * canonical deployed HTTPS backend rather than loopback localhost.
 */
object ApiConfig {

    /**
     * Canonical Deployed HTTPS Backend Base URL on Render.
     * Verified live deployment reporting healthy database status.
     */
    const val CANONICAL_DEPLOYED_URL = "https://campusverse-backend-api.onrender.com/api/v1"

    /**
     * Custom Domain Production Base URL (active once DNS CNAME is provisioned).
     */
    const val PRODUCTION_CUSTOM_DOMAIN_URL = "https://api.campusverse.edu/api/v1"

    /**
     * Staging Base URL.
     */
    const val STAGING_BASE_URL = "https://staging-api.campusverse.edu/api/v1"

    /**
     * Active production base URL. Defaults to the verified deployed Render service.
     */
    var PRODUCTION_BASE_URL: String = CANONICAL_DEPLOYED_URL

    /**
     * Optional custom override URL set at runtime (e.g., for automated integration tests).
     */
    var customBaseUrl: String? = null

    /**
     * Whether the device running this application is an Android Emulator.
     */
    val isEmulator: Boolean by lazy {
        try {
            val fingerprint = Build.FINGERPRINT ?: ""
            val model = Build.MODEL ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            val hardware = Build.HARDWARE ?: ""
            fingerprint.startsWith("generic") ||
                    fingerprint.startsWith("unknown") ||
                    model.contains("google_sdk") ||
                    model.contains("Emulator") ||
                    model.contains("Android SDK built for x86") ||
                    manufacturer.contains("Genymotion") ||
                    hardware.contains("goldfish") ||
                    hardware.contains("ranchu")
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Localhost Development Base URL (for Android Emulator only).
     */
    const val EMULATOR_DEV_BASE_URL: String = "http://10.0.2.2:4000/api/v1"

    /**
     * Flag indicating whether to use the production API.
     * Defaults to true so the installed app (both release and physical-device debug builds)
     * connects to the verified deployed backend by default.
     */
    var USE_PRODUCTION_API: Boolean = true

    /**
     * Explicit opt-in flag for local emulator development server (http://10.0.2.2:4000/api/v1).
     * Must be explicitly set to true by a developer running a local backend instance on their machine.
     * It is never an automatic fallback.
     */
    var USE_LOCAL_EMULATOR_DEV: Boolean = false

    /**
     * The active API Base URL used by all Android repositories.
     * The normal installed application (including debug builds used for physical-device testing)
     * uses the deployed production HTTPS backend by default.
     */
    val BASE_URL: String
        get() {
            customBaseUrl?.let { return it }

            // Local emulator dev is ONLY active when explicitly opted in on an emulator in Debug mode
            if (BuildConfig.DEBUG && isEmulator && USE_LOCAL_EMULATOR_DEV && !USE_PRODUCTION_API) {
                return EMULATOR_DEV_BASE_URL
            }

            // Normal installed app defaults to the deployed production HTTPS backend
            return PRODUCTION_BASE_URL
        }

    /**
     * Active Base URL for Admin endpoints.
     */
    val ADMIN_BASE_URL: String
        get() = "$BASE_URL/admin"

    /**
     * Recommended network timeouts in milliseconds.
     */
    const val CONNECT_TIMEOUT_MS = 15000
    const val READ_TIMEOUT_MS = 15000

    /**
     * Validates API configuration at application startup.
     * Throws [IllegalStateException] if configuration violates security or deployment policies.
     */
    fun validateConfiguration(): String {
        val activeUrl = BASE_URL
        require(activeUrl.isNotBlank()) { "API Base URL must not be blank." }

        val uri = try {
            URI.create(activeUrl)
        } catch (e: Exception) {
            throw IllegalStateException("CampusVerse API Base URL is malformed: $activeUrl", e)
        }

        val scheme = uri.scheme?.lowercase() ?: ""
        val host = uri.host?.lowercase() ?: ""

        if (!BuildConfig.DEBUG || USE_PRODUCTION_API || !USE_LOCAL_EMULATOR_DEV || !isEmulator) {
            if (scheme != "https") {
                throw IllegalStateException(
                    "Security Invariant Violation: Production API must use HTTPS protocol. " +
                            "Attempted scheme: '$scheme' on URL: $activeUrl"
                )
            }
            if (host in listOf("localhost", "127.0.0.1", "10.0.2.2") || host.endsWith(".local")) {
                throw IllegalStateException(
                    "Configuration Error: Physical device / production app cannot use local loopback address '$host'. " +
                            "Canonical deployed HTTPS backend must be used."
                )
            }
        }

        return activeUrl
    }
}
