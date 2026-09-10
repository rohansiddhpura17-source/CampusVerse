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
 * Network implementation of [PaymentRepository] with offline caching and server authority.
 */
class NetworkPaymentRepository(
    private val baseUrl: String = "http://10.0.2.2:4000/api/v1",
    private val sessionManager: SessionManager? = null
) : PaymentRepository {

    companion object {
        var instance: PaymentRepository = NetworkPaymentRepository()
    }

    // In-memory cache for offline resilience
    private val cachedProducts = mutableListOf<PaymentProduct>()
    private val cachedTransactions = mutableListOf<PaymentTransactionItem>()
    private val cachedEntitlements = mutableListOf<EntitlementItem>()

    private suspend fun getToken(): String? {
        return sessionManager?.getSession()?.token
    }

    override suspend fun getProducts(targetRole: String?): Result<List<PaymentProduct>> = withContext(Dispatchers.IO) {
        try {
            val urlString = if (!targetRole.isNullOrBlank()) {
                "$baseUrl/payments/products?targetRole=$targetRole"
            } else {
                "$baseUrl/payments/products"
            }

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val products = mutableListOf<PaymentProduct>()

                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)
                    products.add(
                        PaymentProduct(
                            id = obj.getString("id"),
                            sku = obj.getString("sku"),
                            title = obj.getString("title"),
                            description = obj.getString("description"),
                            amountPaise = obj.getInt("amountPaise"),
                            currency = obj.optString("currency", "INR"),
                            targetRole = obj.optString("targetRole", "ALL"),
                            productType = obj.optString("productType", "STUDENT_PREMIUM"),
                            isActive = obj.optBoolean("isActive", true),
                            metadata = obj.optString("metadata", null)
                        )
                    )
                }

                synchronized(cachedProducts) {
                    cachedProducts.clear()
                    cachedProducts.addAll(products)
                }

                Result.success(products)
            } else {
                val errReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errJson = errReader.readText()
                errReader.close()
                Result.failure(Exception("Failed to fetch products: $errJson"))
            }
        } catch (e: Exception) {
            synchronized(cachedProducts) {
                if (cachedProducts.isNotEmpty()) {
                    Result.success(cachedProducts.toList())
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun createOrder(productId: String, idempotencyKey: String): Result<PaymentOrder> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/payments/orders")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
                }

                val body = JSONObject().apply {
                    put("productId", productId)
                    put("idempotencyKey", idempotencyKey)
                }

                OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val json = JSONObject(reader.readText())
                    reader.close()

                    val data = json.getJSONObject("data")
                    val prodObj = data.optJSONObject("product")
                    val summary = prodObj?.let {
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
                        keyId = data.optString("keyId", "rzp_test_campusverse_dev"),
                        status = data.optString("status", "PENDING"),
                        product = summary
                    )

                    Result.success(order)
                } else {
                    val errReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                    val errText = errReader.readText()
                    errReader.close()
                    Result.failure(Exception("Order creation failed: $errText"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun verifyPayment(
        orderId: String,
        providerPaymentId: String,
        providerSignature: String,
        paymentMethod: String
    ): Result<PaymentResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/payments/verify")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val body = JSONObject().apply {
                put("orderId", orderId)
                put("providerPaymentId", providerPaymentId)
                put("providerSignature", providerSignature)
                put("paymentMethod", paymentMethod)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val data = json.getJSONObject("data")
                val entObj = data.optJSONObject("entitlement")
                val entitlement = entObj?.let {
                    EntitlementItem(
                        id = it.getString("id"),
                        userId = it.optString("userId", ""),
                        productId = it.optString("productId", ""),
                        status = it.optString("status", "ACTIVE"),
                        validFrom = it.optString("validFrom", ""),
                        validUntil = it.optString("validUntil", null)
                    )
                }

                Result.success(
                    PaymentResult(
                        transactionId = data.getString("transactionId"),
                        status = data.optString("status", "PAID"),
                        entitlement = entitlement
                    )
                )
            } else {
                val errReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errText = errReader.readText()
                errReader.close()
                Result.failure(Exception("Verification failed: $errText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyTransactions(page: Int, limit: Int): Result<List<PaymentTransactionItem>> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/payments/my-transactions?page=$page&limit=$limit")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val json = JSONObject(reader.readText())
                    reader.close()

                    val dataArray = json.optJSONArray("data") ?: JSONArray()
                    val transactions = parseTransactions(dataArray)

                    synchronized(cachedTransactions) {
                        cachedTransactions.clear()
                        cachedTransactions.addAll(transactions)
                    }

                    Result.success(transactions)
                } else {
                    Result.failure(Exception("Failed to fetch user transactions ($responseCode)"))
                }
            } catch (e: Exception) {
                synchronized(cachedTransactions) {
                    if (cachedTransactions.isNotEmpty()) Result.success(cachedTransactions.toList())
                    else Result.failure(e)
                }
            }
        }

    override suspend fun getMyEntitlements(): Result<List<EntitlementItem>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/payments/my-entitlements")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val entitlements = mutableListOf<EntitlementItem>()

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

                    entitlements.add(
                        EntitlementItem(
                            id = obj.getString("id"),
                            userId = obj.optString("userId", ""),
                            productId = obj.optString("productId", ""),
                            status = obj.optString("status", "ACTIVE"),
                            validFrom = obj.optString("validFrom", ""),
                            validUntil = obj.optString("validUntil", null),
                            product = summary
                        )
                    )
                }

                synchronized(cachedEntitlements) {
                    cachedEntitlements.clear()
                    cachedEntitlements.addAll(entitlements)
                }

                Result.success(entitlements)
            } else {
                Result.failure(Exception("Failed to fetch entitlements ($responseCode)"))
            }
        } catch (e: Exception) {
            synchronized(cachedEntitlements) {
                if (cachedEntitlements.isNotEmpty()) Result.success(cachedEntitlements.toList())
                else Result.failure(e)
            }
        }
    }

    override suspend fun getAdminFinanceOverview(): Result<AdminFinanceOverview> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/admin/finance/overview")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val data = json.getJSONObject("data")
                val counts = data.getJSONObject("counts")

                Result.success(
                    AdminFinanceOverview(
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
                )
            } else {
                val errReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val err = errReader.readText()
                errReader.close()
                Result.failure(Exception("Admin overview failed: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAdminFinanceTransactions(
        page: Int,
        limit: Int,
        status: String?,
        role: String?,
        search: String?
    ): Result<List<PaymentTransactionItem>> = withContext(Dispatchers.IO) {
        try {
            val queryParams = mutableListOf("page=$page", "limit=$limit")
            if (!status.isNullOrBlank()) queryParams.add("status=$status")
            if (!role.isNullOrBlank()) queryParams.add("role=$role")
            if (!search.isNullOrBlank()) queryParams.add("search=$search")

            val url = URL("$baseUrl/admin/finance/transactions?${queryParams.joinToString("&")}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val transactions = parseTransactions(dataArray)
                Result.success(transactions)
            } else {
                Result.failure(Exception("Admin transactions failed ($responseCode)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun processAdminRefund(
        transactionId: String,
        amountPaise: Int,
        reason: String,
        adminNotes: String?
    ): Result<RefundItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/admin/finance/transactions/$transactionId/refund")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val body = JSONObject().apply {
                put("amountPaise", amountPaise)
                put("reason", reason)
                if (!adminNotes.isNullOrBlank()) put("adminNotes", adminNotes)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = JSONObject(reader.readText())
                reader.close()

                val data = json.getJSONObject("data")
                Result.success(
                    RefundItem(
                        id = data.getString("refundId"),
                        amountPaise = data.getInt("amountPaise"),
                        status = data.optString("status", "PROCESSED"),
                        reason = reason,
                        createdAt = ""
                    )
                )
            } else {
                val errReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errText = errReader.readText()
                errReader.close()
                Result.failure(Exception("Refund failed: $errText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
