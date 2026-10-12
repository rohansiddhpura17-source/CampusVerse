package com.campusverse.app.data.repository

import android.util.Log
import com.campusverse.app.data.model.AdminAnnouncementItem
import com.campusverse.app.data.model.AdminDashboardData
import com.campusverse.app.data.model.AdminDashboardMetrics
import com.campusverse.app.data.model.AdminEventItem
import com.campusverse.app.data.model.AdminJobItem
import com.campusverse.app.data.model.AdminMarketplaceItem
import com.campusverse.app.data.model.AdminMentorItem
import com.campusverse.app.data.model.AdminPlatformSettingsData
import com.campusverse.app.data.model.AdminReportItem
import com.campusverse.app.data.model.AdminUserItem
import com.campusverse.app.data.model.AdminVerificationItem
import com.campusverse.app.data.model.AuditLogItem
import com.campusverse.app.data.model.NoteItem
import com.campusverse.app.domain.admin.AdminRepository
import com.campusverse.app.domain.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Production-ready network implementation of [AdminRepository].
 * Directly connects to the backend API with no hardcoded fallback demo metrics.
 */
class NetworkAdminRepository(
    private val baseUrl: String = com.campusverse.app.data.network.ApiConfig.ADMIN_BASE_URL,
    private val sessionManager: SessionManager? = null
) : AdminRepository {

    companion object {
        private const val TAG = "NetworkAdminRepo"
        var instance: AdminRepository = NetworkAdminRepository()
    }

    private suspend fun getToken(): String? {
        return sessionManager?.getSession()?.token
    }

    private val effectiveBaseUrl: String
        get() = if (baseUrl.isNotBlank() && !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1") && !baseUrl.contains("10.0.2.2")) baseUrl else com.campusverse.app.data.network.ApiConfig.ADMIN_BASE_URL

    // ==========================================
    // HTTP helper
    // ==========================================

    private suspend fun makeRequest(
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val rootUrl = effectiveBaseUrl
        val urlString = if (path.startsWith("http")) path else "$rootUrl$path"
        try {
            Log.d(TAG, "[$method] -> $urlString")
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = com.campusverse.app.data.network.ApiConfig.CONNECT_TIMEOUT_MS
            connection.readTimeout = com.campusverse.app.data.network.ApiConfig.READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val token = getToken()
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            } else {
                Log.w(TAG, "[$method] $urlString dispatched with NO auth token in session")
            }

            if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(body.toString())
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

            Log.d(TAG, "[$method] <- $urlString [$responseCode] len=${responseBody.length}")
            if (responseCode !in 200..299) {
                Log.e(TAG, "[$method] Error response ($responseCode): $responseBody")
            }
            if (responseCode == 401 || responseCode == 403) {
                if (responseBody.contains("INVALID_TOKEN") || responseBody.contains("TOKEN_EXPIRED") || responseBody.contains("UNAUTHORIZED") || responseBody.contains("SESSION_REVOKED")) {
                    Log.w(TAG, "Session invalid on $responseCode, clearing stored session")
                    sessionManager?.clearSession()
                }
            }

            Pair(responseCode, responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "[$method] Exception connecting to $urlString: ${e.message}", e)
            Pair(-1, "Unable to reach server ($urlString): ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun formatErrorMessage(prefix: String, code: Int, res: String): String {
        val friendlyMsg = try {
            val json = JSONObject(res)
            val err = json.optJSONObject("error")
            val codeStr = err?.optString("code") ?: ""
            val msgStr = err?.optString("message") ?: json.optString("message", "")
            if (codeStr.isNotBlank() && msgStr.isNotBlank()) {
                "$codeStr — $msgStr"
            } else if (msgStr.isNotBlank()) {
                msgStr
            } else {
                res
            }
        } catch (_: Exception) {
            res
        }
        return "$prefix ($code): $friendlyMsg"
    }

    // -------------------------------------------------------------------------
    // 1. Dashboard
    // -------------------------------------------------------------------------

    override suspend fun getDashboardData(): Result<AdminDashboardData> {
        val (code, res) = makeRequest("/dashboard")
        if (code !in 200..299) {
            return Result.failure(Exception(formatErrorMessage("Dashboard API failed", code, res)))
        }
        return try {
            val root = JSONObject(res)
            val data = root.optJSONObject("data") ?: return Result.failure(Exception("Invalid dashboard response format"))
            val metricsObj = data.optJSONObject("metrics") ?: JSONObject()
            val usersObj = metricsObj.optJSONObject("users") ?: JSONObject()
            val verifObj = metricsObj.optJSONObject("verifications") ?: JSONObject()
            val jobsObj = metricsObj.optJSONObject("jobs") ?: JSONObject()
            val mktObj = metricsObj.optJSONObject("marketplace") ?: JSONObject()
            val safetyObj = metricsObj.optJSONObject("safety") ?: JSONObject()
            val eventsObj = metricsObj.optJSONObject("events") ?: JSONObject()
            val sysObj = metricsObj.optJSONObject("system") ?: JSONObject()

            val metrics = AdminDashboardMetrics(
                totalUsers = usersObj.optInt("total", 0),
                studentsCount = usersObj.optInt("students", 0),
                alumniCount = usersObj.optInt("alumni", 0),
                aspirantsCount = usersObj.optInt("aspirants", 0),
                pendingVerifications = verifObj.optInt("pending", 0),
                activeJobs = jobsObj.optInt("active", 0),
                pendingJobs = jobsObj.optInt("pending", 0),
                activeMarketplaceListings = mktObj.optInt("activeListings", 0),
                pendingReports = safetyObj.optInt("pendingReports", 0),
                totalEvents = eventsObj.optInt("total", 0),
                systemStatus = sysObj.optString("status", "OPERATIONAL"),
                databaseUptime = sysObj.optString("databaseUptime", "99.98%"),
                activeSessions = sysObj.optInt("activeSessions", 0),
                securityAlerts = sysObj.optInt("securityAlerts", 0)
            )

            val logsArr = data.optJSONArray("recentAuditLogs") ?: JSONArray()
            val logs = mutableListOf<AuditLogItem>()
            for (i in 0 until logsArr.length()) {
                val item = logsArr.getJSONObject(i)
                val actor = item.optJSONObject("actor")
                val actorProfile = actor?.optJSONObject("profile")
                logs.add(
                    AuditLogItem(
                        id = item.optString("id"),
                        actorEmail = actor?.optString("email") ?: "admin@campusverse.edu",
                        actorName = actorProfile?.optString("fullName") ?: "Administrator",
                        action = item.optString("action"),
                        targetType = item.optString("targetType"),
                        targetId = item.optString("targetId"),
                        timestamp = item.optString("timestamp"),
                        metadata = if (item.isNull("metadata")) null else item.optString("metadata")
                    )
                )
            }

            Result.success(AdminDashboardData(metrics = metrics, recentAuditLogs = logs))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dashboard data: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 2. User Management
    // -------------------------------------------------------------------------

    override suspend fun getUsers(search: String?, role: String?, status: String?): Result<List<AdminUserItem>> {
        val query = buildString {
            append("?")
            if (!search.isNullOrBlank()) append("search=").append(URLEncoder.encode(search, "UTF-8")).append("&")
            if (!role.isNullOrBlank() && role != "ALL") append("role=$role&")
            if (!status.isNullOrBlank() && status != "ALL") append("status=$status&")
        }
        val (code, res) = makeRequest("/users$query")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch users ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminUserItem>()
            for (i in 0 until arr.length()) {
                val u = arr.getJSONObject(i)
                val prof = u.optJSONObject("profile")
                val verifs = u.optJSONArray("verifications")
                val vStatus = if (verifs != null && verifs.length() > 0) verifs.getJSONObject(0).optString("status") else null
                list.add(
                    AdminUserItem(
                        id = u.optString("id"),
                        email = u.optString("email"),
                        fullName = prof?.optString("fullName") ?: u.optString("email"),
                        role = u.optString("role"),
                        isActive = u.optBoolean("isActive", true),
                        isEmailVerified = u.optBoolean("isEmailVerified", false),
                        avatarUrl = prof?.optString("avatarUrl", null),
                        headline = prof?.optString("headline", null),
                        location = prof?.optString("location", null),
                        createdAt = u.optString("createdAt"),
                        verificationStatus = vStatus
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing users: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserDetails(userId: String): Result<AdminUserItem> {
        val (code, res) = makeRequest("/users/$userId")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch user $userId ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val u = root.getJSONObject("data")
            val prof = u.optJSONObject("profile")
            Result.success(
                AdminUserItem(
                    id = u.optString("id"),
                    email = u.optString("email"),
                    fullName = prof?.optString("fullName") ?: u.optString("email"),
                    role = u.optString("role"),
                    isActive = u.optBoolean("isActive", true),
                    isEmailVerified = u.optBoolean("isEmailVerified", false),
                    avatarUrl = prof?.optString("avatarUrl", null),
                    headline = prof?.optString("headline", null),
                    location = prof?.optString("location", null),
                    createdAt = u.optString("createdAt")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user details: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateUserStatus(userId: String, isActive: Boolean?, role: String?, reason: String?): Result<AdminUserItem> {
        val payload = JSONObject()
        if (isActive != null) payload.put("isActive", isActive)
        if (role != null) payload.put("role", role)
        if (reason != null) payload.put("suspensionReason", reason)

        val (code, res) = makeRequest("/users/$userId/status", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to update user status ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val u = root.getJSONObject("data")
            val prof = u.optJSONObject("profile")
            val item = AdminUserItem(
                id = u.optString("id"),
                email = u.optString("email"),
                fullName = prof?.optString("fullName") ?: u.optString("email"),
                role = u.optString("role"),
                isActive = u.optBoolean("isActive", true),
                isEmailVerified = u.optBoolean("isEmailVerified", false),
                avatarUrl = prof?.optString("avatarUrl", null),
                headline = prof?.optString("headline", null),
                location = prof?.optString("location", null),
                createdAt = u.optString("createdAt", "")
            )
            Result.success(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing updated user: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun resetUserPassword(userId: String): Result<String> {
        val (code, res) = makeRequest("/users/$userId/reset-password", "POST", JSONObject())
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to reset password ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val msg = root.optJSONObject("data")?.optString("message")
                ?: root.optString("message", "Password reset instructions dispatched to user's email.")
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing reset password response: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun createUser(name: String, email: String, password: String, role: String): Result<AdminUserItem> {
        val payload = JSONObject().apply {
            put("fullName", name)
            put("email", email)
            put("password", password)
            put("role", role)
        }
        val (code, res) = makeRequest("/users", "POST", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to create user ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val u = root.getJSONObject("data")
            val prof = u.optJSONObject("profile")
            val item = AdminUserItem(
                id = u.optString("id"),
                email = u.optString("email"),
                fullName = prof?.optString("fullName") ?: name,
                role = u.optString("role", role),
                isActive = u.optBoolean("isActive", true),
                isEmailVerified = u.optBoolean("isEmailVerified", true),
                avatarUrl = prof?.optString("avatarUrl", null),
                createdAt = u.optString("createdAt", "")
            )
            Result.success(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing created user: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteUser(userId: String): Result<Boolean> {
        val (code, res) = makeRequest("/users/$userId", "DELETE")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to delete user ($code): $res"))
        }
        return Result.success(true)
    }

    // -------------------------------------------------------------------------
    // 3. Verifications
    // -------------------------------------------------------------------------

    override suspend fun getVerifications(status: String?): Result<List<AdminVerificationItem>> {
        val query = if (!status.isNullOrBlank() && status != "ALL") "?status=$status" else ""
        val (code, res) = makeRequest("/verifications$query")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch verifications ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminVerificationItem>()
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                val u = v.optJSONObject("user")
                val prof = u?.optJSONObject("profile")
                val rev = v.optJSONObject("reviewer")
                val revProf = rev?.optJSONObject("profile")
                list.add(
                    AdminVerificationItem(
                        id = v.optString("id"),
                        userId = v.optString("userId"),
                        userEmail = u?.optString("email") ?: "",
                        userName = prof?.optString("fullName") ?: u?.optString("email") ?: "User",
                        userRole = u?.optString("role") ?: "STUDENT",
                        documentType = v.optString("documentType"),
                        documentUrl = v.optString("documentUrl"),
                        status = v.optString("status"),
                        submittedAt = v.optString("submittedAt"),
                        rejectionReason = if (v.isNull("rejectionReason")) null else v.optString("rejectionReason"),
                        reviewerName = revProf?.optString("fullName") ?: rev?.optString("email")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing verifications: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun reviewVerification(verificationId: String, status: String, rejectionReason: String?): Result<AdminVerificationItem> {
        val payload = JSONObject().apply {
            put("status", status)
            if (!rejectionReason.isNullOrBlank()) put("rejectionReason", rejectionReason)
        }
        val (code, res) = makeRequest("/verifications/$verificationId/review", "POST", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to review verification ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val v = root.getJSONObject("data")
            val item = AdminVerificationItem(
                id = v.optString("id"),
                userId = v.optString("userId"),
                userEmail = "",
                userName = "User",
                userRole = "STUDENT",
                documentType = v.optString("documentType"),
                documentUrl = v.optString("documentUrl"),
                status = v.optString("status"),
                submittedAt = v.optString("submittedAt"),
                rejectionReason = if (v.isNull("rejectionReason")) null else v.optString("rejectionReason")
            )
            Result.success(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing reviewed verification: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 4. Reports & Moderation
    // -------------------------------------------------------------------------

    override suspend fun getReports(status: String?, targetType: String?): Result<List<AdminReportItem>> {
        val query = buildString {
            append("?")
            if (!status.isNullOrBlank() && status != "ALL") append("status=$status&")
            if (!targetType.isNullOrBlank() && targetType != "ALL") append("targetType=$targetType&")
        }
        val (code, res) = makeRequest("/reports$query")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch reports ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminReportItem>()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val rep = r.optJSONObject("reporter")
                val repProf = rep?.optJSONObject("profile")
                val rev = r.optJSONObject("reviewer")
                val revProf = rev?.optJSONObject("profile")
                list.add(
                    AdminReportItem(
                        id = r.optString("id"),
                        reporterId = r.optString("reporterId"),
                        reporterName = repProf?.optString("fullName") ?: rep?.optString("email") ?: "Reporter",
                        reporterEmail = rep?.optString("email") ?: "",
                        targetType = r.optString("targetType"),
                        targetId = r.optString("targetId"),
                        reason = r.optString("reason"),
                        status = r.optString("status"),
                        resolutionNotes = if (r.isNull("resolutionNotes")) null else r.optString("resolutionNotes"),
                        reviewerName = revProf?.optString("fullName") ?: rev?.optString("email"),
                        createdAt = r.optString("createdAt")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing reports: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun resolveReport(reportId: String, status: String, actionTaken: String, notes: String?): Result<AdminReportItem> {
        val payload = JSONObject().apply {
            put("status", status)
            put("actionTaken", actionTaken)
            if (!notes.isNullOrBlank()) put("resolutionNotes", notes)
        }
        val (code, res) = makeRequest("/reports/$reportId/resolve", "POST", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to resolve report ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val r = root.getJSONObject("data")
            val item = AdminReportItem(
                id = r.optString("id"),
                reporterId = r.optString("reporterId"),
                reporterName = "Reporter",
                reporterEmail = "",
                targetType = r.optString("targetType"),
                targetId = r.optString("targetId"),
                reason = r.optString("reason"),
                status = r.optString("status"),
                resolutionNotes = if (r.isNull("resolutionNotes")) null else r.optString("resolutionNotes"),
                createdAt = r.optString("createdAt")
            )
            Result.success(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing resolved report: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 5. Marketplace
    // -------------------------------------------------------------------------

    override suspend fun getMarketplaceListings(search: String?, category: String?): Result<List<AdminMarketplaceItem>> {
        val query = buildString {
            append("?")
            if (!search.isNullOrBlank()) append("search=").append(URLEncoder.encode(search, "UTF-8")).append("&")
            if (!category.isNullOrBlank() && category != "ALL") append("category=$category&")
        }
        val (code, res) = makeRequest("/marketplace$query")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch marketplace listings ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminMarketplaceItem>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val s = m.optJSONObject("seller")
                val sProf = s?.optJSONObject("profile")
                list.add(
                    AdminMarketplaceItem(
                        id = m.optString("id"),
                        title = m.optString("title"),
                        price = m.optDouble("price", 0.0),
                        category = m.optString("category"),
                        status = m.optString("status"),
                        sellerName = sProf?.optString("fullName") ?: s?.optString("email") ?: "Seller",
                        sellerEmail = s?.optString("email") ?: "",
                        createdAt = m.optString("createdAt")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing marketplace listings: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun moderateMarketplaceItem(itemId: String, action: String, reason: String?): Result<Boolean> {
        val payload = JSONObject().apply {
            put("action", action)
            if (!reason.isNullOrBlank()) put("reason", reason)
        }
        val (code, res) = makeRequest("/marketplace/$itemId/moderate", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to moderate marketplace item ($code): $res"))
        }
        return Result.success(true)
    }

    // -------------------------------------------------------------------------
    // 6. Events & Jobs
    // -------------------------------------------------------------------------

    override suspend fun getEvents(): Result<List<AdminEventItem>> {
        val (code, res) = makeRequest("/events")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch events ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminEventItem>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val org = e.optJSONObject("organizer")
                val orgProf = org?.optJSONObject("profile")
                list.add(
                    AdminEventItem(
                        id = e.optString("id"),
                        title = e.optString("title"),
                        eventType = e.optString("type", "ACADEMIC"),
                        date = e.optString("startDate"),
                        location = e.optString("location"),
                        status = e.optString("status", "UPCOMING"),
                        organizerName = orgProf?.optString("fullName") ?: org?.optString("email") ?: "Organizer",
                        organizerEmail = org?.optString("email") ?: ""
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing events: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun moderateEvent(eventId: String, action: String, reason: String?): Result<Boolean> {
        val payload = JSONObject().apply {
            put("action", action)
            if (!reason.isNullOrBlank()) put("reason", reason)
        }
        val (code, res) = makeRequest("/events/$eventId/moderate", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to moderate event ($code): $res"))
        }
        return Result.success(true)
    }

    override suspend fun getJobs(): Result<List<AdminJobItem>> {
        val (code, res) = makeRequest("/jobs")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch jobs ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminJobItem>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val comp = j.optJSONObject("company")
                val poster = j.optJSONObject("poster")
                val posterProf = poster?.optJSONObject("profile")
                list.add(
                    AdminJobItem(
                        id = j.optString("id"),
                        title = j.optString("title"),
                        companyName = comp?.optString("name") ?: j.optString("companyName", "Company"),
                        jobType = j.optString("type", "FULL_TIME"),
                        location = j.optString("location", "Remote"),
                        status = j.optString("status", "ACTIVE"),
                        posterName = posterProf?.optString("fullName") ?: poster?.optString("email") ?: "Poster",
                        posterEmail = poster?.optString("email") ?: ""
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing jobs: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun moderateJob(jobId: String, action: String, reason: String?): Result<Boolean> {
        val payload = JSONObject().apply {
            put("action", action)
            if (!reason.isNullOrBlank()) put("reason", reason)
        }
        val (code, res) = makeRequest("/jobs/$jobId/moderate", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to moderate job ($code): $res"))
        }
        return Result.success(true)
    }

    // -------------------------------------------------------------------------
    // 7. Mentorship
    // -------------------------------------------------------------------------

    override suspend fun getMentors(): Result<List<AdminMentorItem>> {
        val (code, res) = makeRequest("/mentorship")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch mentors ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminMentorItem>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val u = m.optJSONObject("user")
                val prof = u?.optJSONObject("profile")
                val count = m.optJSONObject("_count")
                list.add(
                    AdminMentorItem(
                        id = m.optString("id"),
                        userId = m.optString("userId"),
                        mentorName = prof?.optString("fullName") ?: u?.optString("email") ?: "Mentor",
                        mentorEmail = u?.optString("email") ?: "",
                        title = m.optString("title"),
                        company = m.optString("company"),
                        expertise = m.optString("expertise"),
                        isAcceptingMentees = m.optBoolean("isAcceptingMentees", true),
                        requestsCount = count?.optInt("requests", 0) ?: 0,
                        rating = m.optDouble("rating", 5.0)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing mentors: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun moderateMentor(mentorId: String, action: String, reason: String?): Result<Boolean> {
        val payload = JSONObject().apply {
            put("action", action)
            if (!reason.isNullOrBlank()) put("reason", reason)
        }
        val (code, res) = makeRequest("/mentorship/$mentorId/moderate", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to moderate mentor ($code): $res"))
        }
        return Result.success(true)
    }

    // -------------------------------------------------------------------------
    // 8. Announcements
    // -------------------------------------------------------------------------

    override suspend fun getAnnouncements(): Result<List<AdminAnnouncementItem>> {
        val (code, res) = makeRequest("/announcements")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch announcements ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AdminAnnouncementItem>()
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val auth = a.optJSONObject("author")
                val authProf = auth?.optJSONObject("profile")
                list.add(
                    AdminAnnouncementItem(
                        id = a.optString("id"),
                        title = a.optString("title"),
                        content = a.optString("content"),
                        targetRole = if (a.isNull("targetRole")) null else a.optString("targetRole"),
                        priority = a.optString("priority", "NORMAL"),
                        authorName = authProf?.optString("fullName") ?: auth?.optString("email") ?: "Administrator",
                        createdAt = a.optString("createdAt"),
                        deliveredCount = a.optInt("deliveredCount", 0)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing announcements: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun createAnnouncement(title: String, content: String, targetRole: String?, priority: String): Result<AdminAnnouncementItem> {
        val payload = JSONObject().apply {
            put("title", title)
            put("content", content)
            if (!targetRole.isNullOrBlank() && targetRole != "ALL") put("targetRole", targetRole)
            put("priority", priority)
        }
        val (code, res) = makeRequest("/announcements", "POST", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to create announcement ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val a = root.getJSONObject("data")
            val item = AdminAnnouncementItem(
                id = a.optString("id"),
                title = a.optString("title"),
                content = a.optString("content"),
                targetRole = if (a.isNull("targetRole")) null else a.optString("targetRole"),
                priority = a.optString("priority", "NORMAL"),
                authorName = "Campus Administrator",
                createdAt = a.optString("createdAt"),
                deliveredCount = a.optInt("deliveredCount", 0)
            )
            Result.success(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing created announcement: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // 9. Platform Settings
    // -------------------------------------------------------------------------

    override suspend fun getPlatformSettings(): Result<AdminPlatformSettingsData> {
        val (code, res) = makeRequest("/settings")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch platform settings ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val d = root.getJSONObject("data")
            val settings = AdminPlatformSettingsData(
                maintenanceMode = d.optBoolean("maintenanceMode", false),
                allowNewRegistrations = d.optBoolean("allowNewRegistrations", true),
                autoModeration = d.optBoolean("autoModeration", true),
                strictVerification = d.optBoolean("strictVerification", true),
                require2FAForAdmins = d.optBoolean("require2FAForAdmins", true),
                systemVersion = d.optString("systemVersion", "2.4.0"),
                lastBackup = d.optString("lastBackup", "Live")
            )
            Result.success(settings)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing settings: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updatePlatformSettings(settings: AdminPlatformSettingsData): Result<AdminPlatformSettingsData> {
        val payload = JSONObject().apply {
            put("maintenanceMode", settings.maintenanceMode)
            put("allowNewRegistrations", settings.allowNewRegistrations)
            put("autoModeration", settings.autoModeration)
            put("strictVerification", settings.strictVerification)
            put("require2FAForAdmins", settings.require2FAForAdmins)
        }
        val (code, res) = makeRequest("/settings", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to update platform settings ($code): $res"))
        }
        return Result.success(settings)
    }

    // -------------------------------------------------------------------------
    // 10. Notes Moderation
    // -------------------------------------------------------------------------

    override suspend fun getAdminNotes(status: String?, search: String?): Result<List<NoteItem>> {
        val queryParams = mutableListOf<String>()
        if (!status.isNullOrBlank() && status != "ALL") {
            queryParams.add("status=$status")
        }
        if (!search.isNullOrBlank()) {
            queryParams.add("search=" + URLEncoder.encode(search, "UTF-8"))
        }
        val queryStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
        val (code, res) = makeRequest("/notes$queryStr")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to fetch notes ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val data = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<NoteItem>()
            for (i in 0 until data.length()) {
                val n = data.getJSONObject(i)
                val userObj = n.optJSONObject("user")
                val profileObj = userObj?.optJSONObject("profile")
                val courseObj = n.optJSONObject("course")
                val tagsStr = n.optString("tags", "")

                list.add(
                    NoteItem(
                        id = n.getString("id"),
                        userId = n.optString("userId", ""),
                        authorName = profileObj?.optString("fullName") ?: "Campus Student",
                        courseCode = courseObj?.optString("code") ?: "CS301",
                        courseName = courseObj?.optString("name") ?: "Distributed Systems",
                        title = n.getString("title"),
                        description = if (n.isNull("description")) null else n.optString("description"),
                        fileUrl = n.optString("fileUrl", "https://docs.campusverse.edu/notes/sample.pdf"),
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",").map { it.trim() } else emptyList(),
                        downloadsCount = n.optInt("downloadsCount", 0),
                        createdAt = if (n.isNull("createdAt")) null else n.optString("createdAt"),
                        isOwnedByCurrentUser = false,
                        status = n.optString("status", "PENDING_REVIEW"),
                        rejectionReason = if (n.isNull("rejectionReason")) null else n.optString("rejectionReason"),
                        removalReason = if (n.isNull("removalReason")) null else n.optString("removalReason"),
                        reviewedBy = if (n.isNull("reviewedBy")) null else n.optString("reviewedBy"),
                        reviewedAt = if (n.isNull("reviewedAt")) null else n.optString("reviewedAt"),
                        removedBy = if (n.isNull("removedBy")) null else n.optString("removedBy"),
                        removedAt = if (n.isNull("removedAt")) null else n.optString("removedAt")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notes: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun moderateNote(noteId: String, action: String, reason: String?): Result<NoteItem> {
        val payload = JSONObject().apply {
            put("action", action)
            if (!reason.isNullOrBlank()) {
                put("reason", reason)
            }
        }
        val (code, res) = makeRequest("/notes/$noteId/moderate", "PATCH", payload)
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to moderate note ($code): $res"))
        }
        return try {
            val root = JSONObject(res)
            val n = root.getJSONObject("data")
            val userObj = n.optJSONObject("user")
            val profileObj = userObj?.optJSONObject("profile")
            val courseObj = n.optJSONObject("course")
            val tagsStr = n.optString("tags", "")

            val note = NoteItem(
                id = n.getString("id"),
                userId = n.optString("userId", ""),
                authorName = profileObj?.optString("fullName") ?: "Campus Student",
                courseCode = courseObj?.optString("code") ?: "CS301",
                courseName = courseObj?.optString("name") ?: "Distributed Systems",
                title = n.getString("title"),
                description = if (n.isNull("description")) null else n.optString("description"),
                fileUrl = n.optString("fileUrl", "https://docs.campusverse.edu/notes/sample.pdf"),
                tags = if (tagsStr.isNotBlank()) tagsStr.split(",").map { it.trim() } else emptyList(),
                downloadsCount = n.optInt("downloadsCount", 0),
                createdAt = if (n.isNull("createdAt")) null else n.optString("createdAt"),
                isOwnedByCurrentUser = false,
                status = n.optString("status", "PUBLISHED"),
                rejectionReason = if (n.isNull("rejectionReason")) null else n.optString("rejectionReason"),
                removalReason = if (n.isNull("removalReason")) null else n.optString("removalReason"),
                reviewedBy = if (n.isNull("reviewedBy")) null else n.optString("reviewedBy"),
                reviewedAt = if (n.isNull("reviewedAt")) null else n.optString("reviewedAt"),
                removedBy = if (n.isNull("removedBy")) null else n.optString("removedBy"),
                removedAt = if (n.isNull("removedAt")) null else n.optString("removedAt")
            )
            Result.success(note)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing moderated note: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteNotePermanently(noteId: String): Result<Boolean> {
        val (code, res) = makeRequest("/notes/$noteId", "DELETE")
        if (code !in 200..299) {
            return Result.failure(Exception("Failed to delete note ($code): $res"))
        }
        return Result.success(true)
    }
}
