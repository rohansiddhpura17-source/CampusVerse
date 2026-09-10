package com.campusverse.app.data.model

import java.util.Locale

/**
 * Domain & DTO models for CampusVerse Monetization and Payment Architecture.
 * All backend monetary amounts are in integer paise (minor units, 100 paise = ₹1.00).
 */

data class PaymentProduct(
    val id: String,
    val sku: String,
    val title: String,
    val description: String,
    val amountPaise: Int,
    val currency: String = "INR",
    val targetRole: String, // ALL, STUDENT, ASPIRANT, ALUMNI
    val productType: String, // STUDENT_PREMIUM, ASPIRANT_PREMIUM, ALUMNI_PREMIUM, AI_CREDITS, MENTORSHIP
    val isActive: Boolean = true,
    val metadata: String? = null
) {
    val formattedAmount: String
        get() = "₹${String.format(Locale.US, "%.2f", amountPaise / 100.0)}"

    val displayPriceInr: Double
        get() = amountPaise / 100.0
}

data class PaymentProductSummary(
    val id: String,
    val title: String,
    val sku: String,
    val productType: String? = null
)

data class PaymentOrder(
    val orderId: String,
    val providerOrderId: String,
    val amountPaise: Int,
    val currency: String = "INR",
    val keyId: String,
    val status: String,
    val product: PaymentProductSummary? = null
) {
    val formattedAmount: String
        get() = "₹${String.format(Locale.US, "%.2f", amountPaise / 100.0)}"
}

data class PaymentResult(
    val transactionId: String,
    val status: String,
    val entitlement: EntitlementItem? = null
)

data class RefundItem(
    val id: String,
    val amountPaise: Int,
    val status: String,
    val reason: String? = null,
    val createdAt: String
) {
    val formattedAmount: String
        get() = "₹${String.format(Locale.US, "%.2f", amountPaise / 100.0)}"
}

data class TransactionUserInfo(
    val id: String,
    val email: String,
    val role: String,
    val fullName: String? = null
)

data class PaymentTransactionItem(
    val id: String,
    val userId: String,
    val productId: String,
    val paymentOrderId: String,
    val amountPaise: Int,
    val currency: String = "INR",
    val provider: String = "RAZORPAY",
    val providerPaymentId: String,
    val paymentMethod: String = "UPI",
    val status: String, // PAID, PARTIALLY_REFUNDED, REFUNDED, FAILED, PENDING
    val failureReason: String? = null,
    val paidAt: String? = null,
    val createdAt: String,
    val product: PaymentProductSummary? = null,
    val refunds: List<RefundItem> = emptyList(),
    val user: TransactionUserInfo? = null
) {
    val formattedAmount: String
        get() = "₹${String.format(Locale.US, "%.2f", amountPaise / 100.0)}"
}

data class EntitlementItem(
    val id: String,
    val userId: String,
    val productId: String,
    val status: String, // ACTIVE, EXPIRED, REVOKED
    val validFrom: String,
    val validUntil: String? = null,
    val product: PaymentProductSummary? = null
)

data class AdminFinanceOverview(
    val currency: String = "INR",
    val grossRevenuePaise: Long,
    val grossRevenueInr: Double,
    val refundedAmountPaise: Long,
    val refundedAmountInr: Double,
    val netRevenuePaise: Long,
    val netRevenueInr: Double,
    val totalOrders: Int,
    val paidTransactions: Int,
    val failedTransactions: Int,
    val pendingTransactions: Int,
    val successRate: Double
) {
    val formattedGross: String
        get() = "₹${String.format(Locale.US, "%.2f", grossRevenueInr)}"

    val formattedRefunded: String
        get() = "₹${String.format(Locale.US, "%.2f", refundedAmountInr)}"

    val formattedNet: String
        get() = "₹${String.format(Locale.US, "%.2f", netRevenueInr)}"
}
