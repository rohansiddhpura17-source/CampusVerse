package com.campusverse.app

import com.campusverse.app.data.network.ApiConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiConfigurationTest {

    private val originalProductionBaseUrl = ApiConfig.PRODUCTION_BASE_URL
    private val originalUseProduction = ApiConfig.USE_PRODUCTION_API
    private val originalUseLocalDev = ApiConfig.USE_LOCAL_EMULATOR_DEV
    private val originalCustomBaseUrl = ApiConfig.customBaseUrl

    @Before
    fun setUp() {
        ApiConfig.PRODUCTION_BASE_URL = ApiConfig.CANONICAL_DEPLOYED_URL
        ApiConfig.USE_PRODUCTION_API = true
        ApiConfig.USE_LOCAL_EMULATOR_DEV = false
        ApiConfig.customBaseUrl = null
    }

    @After
    fun tearDown() {
        ApiConfig.PRODUCTION_BASE_URL = originalProductionBaseUrl
        ApiConfig.USE_PRODUCTION_API = originalUseProduction
        ApiConfig.USE_LOCAL_EMULATOR_DEV = originalUseLocalDev
        ApiConfig.customBaseUrl = originalCustomBaseUrl
    }

    @Test
    fun `default canonical API URL points to verified Render HTTPS endpoint`() {
        assertEquals("https://campusverse-backend-api.onrender.com/api/v1", ApiConfig.CANONICAL_DEPLOYED_URL)
        assertEquals(ApiConfig.CANONICAL_DEPLOYED_URL, ApiConfig.PRODUCTION_BASE_URL)
    }

    @Test
    fun `default BASE_URL and ADMIN_BASE_URL select canonical deployed HTTPS endpoint`() {
        assertEquals("https://campusverse-backend-api.onrender.com/api/v1", ApiConfig.BASE_URL)
        assertEquals("https://campusverse-backend-api.onrender.com/api/v1/admin", ApiConfig.ADMIN_BASE_URL)
    }

    @Test
    fun `default configuration never contains loopback addresses`() {
        val url = ApiConfig.BASE_URL
        assertFalse("URL must not contain localhost", url.contains("localhost"))
        assertFalse("URL must not contain 127.0.0.1", url.contains("127.0.0.1"))
        assertFalse("URL must not contain 10.0.2.2", url.contains("10.0.2.2"))
        assertTrue("URL must use HTTPS", url.startsWith("https://"))
    }

    @Test
    fun `validateConfiguration succeeds for canonical production configuration`() {
        val validatedUrl = ApiConfig.validateConfiguration()
        assertEquals("https://campusverse-backend-api.onrender.com/api/v1", validatedUrl)
    }

    @Test
    fun `validateConfiguration rejects insecure HTTP protocol for production`() {
        ApiConfig.PRODUCTION_BASE_URL = "http://campusverse-backend-api.onrender.com/api/v1"
        try {
            ApiConfig.validateConfiguration()
            fail("Expected IllegalStateException for non-HTTPS URL")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Security Invariant Violation") == true)
        }
    }

    @Test
    fun `validateConfiguration rejects localhost loopback for production`() {
        ApiConfig.PRODUCTION_BASE_URL = "http://localhost:4000/api/v1"
        try {
            ApiConfig.validateConfiguration()
            fail("Expected IllegalStateException for localhost in production")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Security Invariant Violation") == true || e.message?.contains("Configuration Error") == true)
        }
    }

    @Test
    fun `local emulator development is only active when explicitly opted in`() {
        // Without explicit opt-in, it remains production
        assertEquals("https://campusverse-backend-api.onrender.com/api/v1", ApiConfig.BASE_URL)

        // When explicitly opting in:
        ApiConfig.USE_PRODUCTION_API = false
        ApiConfig.USE_LOCAL_EMULATOR_DEV = true

        if (ApiConfig.isEmulator) {
            assertEquals("http://10.0.2.2:4000/api/v1", ApiConfig.BASE_URL)
        }
    }
}
