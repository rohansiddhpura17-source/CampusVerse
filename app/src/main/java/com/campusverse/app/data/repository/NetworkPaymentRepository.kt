package com.campusverse.app.data.repository

import com.campusverse.app.data.model.AdminFinanceOverview
import com.campusverse.app.data.model.EntitlementItem
import com.campusverse.app.data.model.PaymentOrder
import com.campusverse.app.data.model.PaymentProduct
import com.campusverse.app.data.model.PaymentProductSummary
import com.campusverse.app.data.model.PaymentResult
import com.campusverse.app.data.model.PaymentTransactionItem
import com.campusverse.app.data.model.RefundItem
import com.campusverse.app.data.model.TransactionUserInfo
import com.campusverse.app.domain.payment.PaymentRepository
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

/**
 * Production implementation of [PaymentRepository].
 * Directly interfaces with the centralized deployed backend payment endpoints.
 * Operates exclusively on integer minor units (paise) and validates authoritative database state.
 */
class NetworkPaymentRepository(
    private val baseUrl: String = "",
    private val sessionManager: SessionManager? = null
) : PaymentRepository {

    companion object {
        var instance: PaymentRepository = NetworkPaymentRepository()
    }

    private val effectiveBaseUrl: String
        get() = if (baseUrl.isNotBlank() && !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1") && !baseUrl.contains("10.0.2.2")) baseUrl else com.campusverse.app.data.network.ApiConfig.BASE_URL

    private suspend fun getToken(): String? {
        return sessionManager?.getSession()?.token
    }

    private suspend fun getCurrentUserId(): String? {
        return sessionManager?.getSession()?.user?.userId
    }

    private fun extractErrorMessage(code: Int, res: String, fallback: String): String {
        if (code == -1) {
            return "Network connection failed: $res"
        }
        return try {
            val root = JSONObject(res)
            val errObj = root.optJSONObject("error")
            errObj?.optString("message")
                ?: root.optString("message")
                ?: "$fallback (HTTP $code)"
        } catch (_: Exception) {
            if (res.isNotBlank()) res else "$fallback (HTTP $code)"
        }
    }

    override suspend fun getProducts(targetRole: String?): Result<List<PaymentProduct>> = withContext(Dispatchers.IO) {
        val path = if (!targetRole.isNullOrBlank()) "/payments/products?targetRole=$targetRole" else "/payments/products"
        val (code, res) = makeRequest(path)
        if (code in 200..299) {
            try {
                val root = JSONObject(res)
                val arr = root.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<PaymentProduct>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PaymentProduct(
                            id = obj.getString("id"),
                            sku = obj.getString("sku"),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            amountPaise = obj.getInt("amountPaise"),
                            currency = obj.optString("currency", "INR"),
                            targetRole = obj.optString("targetRole", "ALL"),
                            productType = obj.optString("productType", "SUBSCRIPTION"),
                            isActive = obj.optBoolean("isActive", true),
                            metadata = if (obj.isNull("metadata")) null else obj.optString("metadata")
                        )
                    )
                }
                return@withContext Result.success(list)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception(extractErrorMessage(code, res, "Failed to load payment products")))
    }

    override suspend fun createOrder(productId: String, idempotencyKey: String): Result<PaymentOrder> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("productId", productId)
                put("idempotencyKey", idempotencyKey)
            }
            val (code, res) = makeRequest("/payments/orders", "POST", payload)
            if (code in 200..299) {
                try {
                    val root = JSONObject(res)
                    val data = root.getJSONObject("data")
                    val prodObj = data.optJSONObject("product")
                    val prodSummary = prodObj?.let {
                        PaymentProductSummary(
                            id = it.optString("id"),
                            title = it.optString("title"),
                            sku = it.optString("sku")
                        )
                    }
                    val order = PaymentOrder(
                        orderId = data.getString("orderId"),
                        providerOrderId = data.getString("providerOrderId"),
                        amountPaise = data.getInt("amountPaise"),
                        currency = data.optString("currency", "INR"),
                        keyId = data.optString("keyId", ""),
                        status = data.optString("status", "PENDING"),
                        product = prodSummary
                    )
                    return@withContext Result.success(order)
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            Result.failure(Exception(extractErrorMessage(code, res, "Failed to create payment order")))
        }

    override suspend fun verifyPayment(
        orderId: String,
        providerPaymentId: String,
        providerSignature: String,
        paymentMethod: String
    ): Result<PaymentResult> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("orderId", orderId)
            put("providerPaymentId", providerPaymentId)
            put("providerSignature", providerSignature)
            put("paymentMethod", paymentMethod)
        }
        val (code, res) = makeRequest("/payments/verify", "POST", payload)
        if (code in 200..299) {
            try {
                val root = JSONObject(res)
                val data = root.getJSONObject("data")
                val entObj = data.optJSONObject("entitlement")
                val entItem = entObj?.let {
                    EntitlementItem(
                        id = it.getString("id"),
                        userId = it.optString("userId", getCurrentUserId() ?: ""),
                        productId = it.optString("productId", ""),
                        status = it.optString("status", "ACTIVE"),
                        validFrom = it.optString("validFrom", ""),
                        validUntil = it.optString("validUntil", null)
                    )
                }
                val result = PaymentResult(
                    transactionId = data.getString("transactionId"),
                    status = data.optString("status", "PAID"),
                    entitlement = entItem
                )
                return@withContext Result.success(result)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception(extractErrorMessage(code, res, "Failed to verify payment")))
    }

    override suspend fun getMyTransactions(page: Int, limit: Int): Result<List<PaymentTransactionItem>> =
        withContext(Dispatchers.IO) {
            val (code, res) = makeRequest("/payments/my-transactions?page=$page&limit=$limit")
            if (code in 200..299) {
                try {
                    val root = JSONObject(res)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    return@withContext Result.success(parseTransactions(data))
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            Result.failure(Exception(extractErrorMessage(code, res, "Failed to fetch transactions")))
        }

    override suspend fun getMyEntitlements(): Result<List<EntitlementItem>> = withContext(Dispatchers.IO) {
        val (code, res) = makeRequest("/payments/my-entitlements")
        if (code in 200..299) {
            try {
                val root = JSONObject(res)
                val arr = root.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<EntitlementItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        EntitlementItem(
                            id = obj.getString("id"),
                            userId = obj.optString("userId", getCurrentUserId() ?: ""),
                            productId = obj.optString("productId", ""),
                            status = obj.optString("status", "ACTIVE"),
                            validFrom = obj.optString("validFrom", ""),
                            validUntil = obj.optString("validUntil", null)
                        )
                    )
                }
                return@withContext Result.success(list)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception(extractErrorMessage(code, res, "Failed to fetch entitlements")))
    }

    private suspend fun makeRequest(
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val rootUrl = effectiveBaseUrl
            val urlString = if (path.startsWith("http")) path else "$rootUrl$path"
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

            if (inputStream != null) {
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    return@withContext Pair(responseCode, sb.toString())
                }
            }
            Pair(responseCode, "")
        } catch (e: Exception) {
            Pair(-1, e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun getAdminFinanceOverview(): Result<AdminFinanceOverview> = withContext(Dispatchers.IO) {
        val (code, res) = makeRequest("/admin/finance/overview")
        if (code in 200..299) {
            try {
                val root = JSONObject(res)
                val data = root.getJSONObject("data")
                val counts = data.optJSONObject("counts") ?: JSONObject()
                val overview = AdminFinanceOverview(
                    currency = data.optString("currency", "INR"),
                    grossRevenuePaise = data.optLong("grossRevenuePaise", 0L),
                    grossRevenueInr = data.optDouble("grossRevenueInr", 0.0),
                    refundedAmountPaise = data.optLong("refundedAmountPaise", 0L),
                    refundedAmountInr = data.optDouble("refundedAmountInr", 0.0),
                    netRevenuePaise = data.optLong("netRevenuePaise", 0L),
                    netRevenueInr = data.optDouble("netRevenueInr", 0.0),
                    totalOrders = counts.optInt("totalOrders", 0),
                    paidTransactions = counts.optInt("paidTransactions", 0),
                    failedTransactions = counts.optInt("failedTransactions", 0),
                    pendingTransactions = counts.optInt("pendingTransactions", 0),
                    successRate = counts.optDouble("successRate", 100.0)
                )
                return@withContext Result.success(overview)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception(extractErrorMessage(code, res, "Failed to fetch finance overview")))
    }

    override suspend fun getAdminFinanceTransactions(
        page: Int,
        limit: Int,
        status: String?,
        role: String?,
        search: String?
    ): Result<List<PaymentTransactionItem>> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("?page=$page&limit=$limit")
            if (!status.isNullOrBlank() && status != "ALL") append("&status=$status")
            if (!role.isNullOrBlank() && role != "ALL") append("&role=$role")
            if (!search.isNullOrBlank()) append("&search=" + java.net.URLEncoder.encode(search, "UTF-8"))
        }
        val (code, res) = makeRequest("/admin/finance/transactions$query")
        if (code in 200..299) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONArray("data") ?: JSONArray()
                return@withContext Result.success(parseTransactions(data))
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception(extractErrorMessage(code, res, "Failed to fetch finance transactions")))
    }

    override suspend fun processAdminRefund(
        transactionId: String,
        amountPaise: Int,
        reason: String,
        adminNotes: String?
    ): Result<RefundItem> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("amountPaise", amountPaise)
            put("reason", reason)
            if (!adminNotes.isNullOrBlank()) put("adminNotes", adminNotes)
            put("idempotencyKey", "rfnd_${transactionId.take(8)}_${System.currentTimeMillis()}")
        }
        val (code, res) = makeRequest("/admin/finance/transactions/$transactionId/refund", "POST", payload)
        if (code in 200..299) {
            try {
                val root = JSONObject(res)
                val data = root.getJSONObject("data")
                val item = RefundItem(
                    id = data.optString("refundId", "ref_${System.currentTimeMillis()}"),
                    amountPaise = data.optInt("amountPaise", amountPaise),
                    status = data.optString("status", "PROCESSED"),
                    reason = reason,
                    createdAt = "Just now"
                )
                return@withContext Result.success(item)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception(extractErrorMessage(code, res, "Failed to process refund")))
    }

    private fun parseTransactions(dataArray: JSONArray): List<PaymentTransactionItem> {
        val list = mutableListOf<PaymentTransactionItem>()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.getJSONObject(i)
            val prodObj = obj.optJSONObject("product")
            val summary = prodObj?.let {
                PaymentProductSummary(
                    id = it.optString("id"),
                    title = it.optString("title"),
                    sku = it.optString("sku"),
                    productType = it.optString("productType", null)
                )
            }

            val userObj = obj.optJSONObject("user")
            val userInfo = userObj?.let {
                val prof = it.optJSONObject("profile")
                TransactionUserInfo(
                    id = it.optString("id"),
                    email = it.optString("email"),
                    role = it.optString("role"),
                    fullName = prof?.optString("fullName", null)
                )
            }

            val refundsArray = obj.optJSONArray("refunds") ?: JSONArray()
            val refunds = mutableListOf<RefundItem>()
            for (j in 0 until refundsArray.length()) {
                val rObj = refundsArray.getJSONObject(j)
                refunds.add(
                    RefundItem(
                        id = rObj.optString("id"),
                        amountPaise = rObj.optInt("amountPaise", 0),
                        status = rObj.optString("status", "PROCESSED"),
                        reason = rObj.optString("reason", null),
                        createdAt = rObj.optString("createdAt", "")
                    )
                )
            }

            list.add(
                PaymentTransactionItem(
                    id = obj.getString("id"),
                    userId = obj.optString("userId", ""),
                    productId = obj.optString("productId", ""),
                    paymentOrderId = obj.optString("paymentOrderId", ""),
                    amountPaise = obj.getInt("amountPaise"),
                    currency = obj.optString("currency", "INR"),
                    provider = obj.optString("provider", "RAZORPAY"),
                    providerPaymentId = obj.optString("providerPaymentId", ""),
                    paymentMethod = obj.optString("paymentMethod", "UPI"),
                    status = obj.optString("status", "PAID"),
                    failureReason = obj.optString("failureReason", null),
                    paidAt = obj.optString("paidAt", null),
                    createdAt = obj.optString("createdAt", ""),
                    product = summary,
                    refunds = refunds,
                    user = userInfo
                )
            )
        }
        return list
    }
}
