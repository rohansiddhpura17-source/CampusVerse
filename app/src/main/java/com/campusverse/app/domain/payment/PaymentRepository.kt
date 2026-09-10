package com.campusverse.app.domain.payment

import com.campusverse.app.data.model.AdminFinanceOverview
import com.campusverse.app.data.model.EntitlementItem
import com.campusverse.app.data.model.PaymentOrder
import com.campusverse.app.data.model.PaymentProduct
import com.campusverse.app.data.model.PaymentResult
import com.campusverse.app.data.model.PaymentTransactionItem
import com.campusverse.app.data.model.RefundItem

/**
 * Domain repository contract for CampusVerse payment and monetization operations.
 */
interface PaymentRepository {
    suspend fun getProducts(targetRole: String? = null): Result<List<PaymentProduct>>
    suspend fun createOrder(productId: String, idempotencyKey: String): Result<PaymentOrder>
    suspend fun verifyPayment(
        orderId: String,
        providerPaymentId: String,
        providerSignature: String,
        paymentMethod: String = "UPI"
    ): Result<PaymentResult>

    suspend fun getMyTransactions(page: Int = 1, limit: Int = 50): Result<List<PaymentTransactionItem>>
    suspend fun getMyEntitlements(): Result<List<EntitlementItem>>

    // Admin Finance Operations
    suspend fun getAdminFinanceOverview(): Result<AdminFinanceOverview>
    suspend fun getAdminFinanceTransactions(
        page: Int = 1,
        limit: Int = 50,
        status: String? = null,
        role: String? = null,
        search: String? = null
    ): Result<List<PaymentTransactionItem>>

    suspend fun processAdminRefund(
        transactionId: String,
        amountPaise: Int,
        reason: String,
        adminNotes: String? = null
    ): Result<RefundItem>
}
