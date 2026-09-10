package com.campusverse.app

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
import com.campusverse.app.ui.screens.admin.finance.AdminFinanceViewModel
import com.campusverse.app.ui.screens.payment.CheckoutState
import com.campusverse.app.ui.screens.payment.PaymentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In-memory Fake Repository for Payment testing.
 */
class FakePaymentRepository : PaymentRepository {
    var shouldFail: Boolean = false
    var failureMessage: String = "Network error"

    val products = mutableListOf(
        PaymentProduct(
            id = "prod_student_pack",
            sku = "STUDENT_PREMIUM_SEMESTER",
            title = "Student Semester Pack",
            description = "Unlimited notes downloads and mock test access",
            amountPaise = 49900,
            currency = "INR",
            targetRole = "STUDENT",
            productType = "STUDENT_PREMIUM"
        ),
        PaymentProduct(
            id = "prod_aspirant_counseling",
            sku = "ASPIRANT_COLLEGE_PREDICTOR_PRO",
            title = "College Predictor Pro",
            description = "Cutoff predictions for 500+ engineering institutes",
            amountPaise = 29900,
            currency = "INR",
            targetRole = "ASPIRANT",
            productType = "ASPIRANT_PREMIUM"
        ),
        PaymentProduct(
            id = "prod_alumni_network",
            sku = "ALUMNI_MENTORSHIP_PASS",
            title = "Alumni Mentorship Pass",
            description = "Access to premium alumni network directory",
            amountPaise = 99900,
            currency = "INR",
            targetRole = "ALUMNI",
            productType = "ALUMNI_PREMIUM"
        )
    )

    val orders = mutableListOf<PaymentOrder>()
    val transactions = mutableListOf<PaymentTransactionItem>()
    val entitlements = mutableListOf<EntitlementItem>()
    val refunds = mutableListOf<RefundItem>()

    var overview = AdminFinanceOverview(
        currency = "INR",
        grossRevenuePaise = 179700L,
        grossRevenueInr = 1797.0,
        refundedAmountPaise = 29900L,
        refundedAmountInr = 299.0,
        netRevenuePaise = 149800L,
        netRevenueInr = 1498.0,
        totalOrders = 5,
        paidTransactions = 3,
        failedTransactions = 1,
        pendingTransactions = 1,
        successRate = 60.0
    )

    var lastVerifiedOrderId: String? = null
    var lastCreatedKey: String? = null

    override suspend fun getProducts(targetRole: String?): Result<List<PaymentProduct>> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        val filtered = if (targetRole != null && targetRole != "ALL") {
            products.filter { it.targetRole == targetRole || it.targetRole == "ALL" }
        } else {
            products
        }
        return Result.success(filtered)
    }

    override suspend fun createOrder(productId: String, idempotencyKey: String): Result<PaymentOrder> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        val prod = products.find { it.id == productId }
            ?: return Result.failure(Exception("Product not found"))
        lastCreatedKey = idempotencyKey
        val order = PaymentOrder(
            orderId = "order_db_${orders.size + 1}",
            providerOrderId = "order_rzp_${orders.size + 1}",
            amountPaise = prod.amountPaise,
            currency = "INR",
            keyId = "rzp_test_mock_key",
            status = "CREATED",
            product = PaymentProductSummary(
                id = prod.id,
                title = prod.title,
                sku = prod.sku,
                productType = prod.productType
            )
        )
        orders.add(order)
        return Result.success(order)
    }

    override suspend fun verifyPayment(
        orderId: String,
        providerPaymentId: String,
        providerSignature: String,
        paymentMethod: String
    ): Result<PaymentResult> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        lastVerifiedOrderId = orderId
        val order = orders.find { it.orderId == orderId }
            ?: return Result.failure(Exception("Order not found"))

        val entitlement = EntitlementItem(
            id = "ent_${System.currentTimeMillis()}",
            userId = "usr_test",
            productId = order.product?.id ?: "prod_default",
            status = "ACTIVE",
            validFrom = "2026-09-09T00:00:00Z",
            validUntil = "2026-12-09T00:00:00Z",
            product = order.product
        )
        entitlements.add(entitlement)

        val tx = PaymentTransactionItem(
            id = "tx_${System.currentTimeMillis()}",
            userId = "usr_test",
            productId = order.product?.id ?: "prod_default",
            paymentOrderId = order.orderId,
            amountPaise = order.amountPaise,
            currency = "INR",
            provider = "RAZORPAY",
            providerPaymentId = providerPaymentId,
            paymentMethod = paymentMethod,
            status = "PAID",
            createdAt = "2026-09-09T00:00:00Z",
            product = order.product
        )
        transactions.add(tx)

        return Result.success(
            PaymentResult(
                transactionId = tx.id,
                status = "PAID",
                entitlement = entitlement
            )
        )
    }

    override suspend fun getMyTransactions(page: Int, limit: Int): Result<List<PaymentTransactionItem>> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        return Result.success(transactions)
    }

    override suspend fun getMyEntitlements(): Result<List<EntitlementItem>> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        return Result.success(entitlements)
    }

    override suspend fun getAdminFinanceOverview(): Result<AdminFinanceOverview> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        return Result.success(overview)
    }

    override suspend fun getAdminFinanceTransactions(
        page: Int,
        limit: Int,
        status: String?,
        role: String?,
        search: String?
    ): Result<List<PaymentTransactionItem>> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        var list = transactions.toList()
        if (status != null) list = list.filter { it.status == status }
        if (role != null) list = list.filter { it.user?.role == role }
        if (search != null) {
            val q = search.lowercase()
            list = list.filter {
                it.id.lowercase().contains(q) ||
                    (it.product?.title?.lowercase()?.contains(q) == true) ||
                    (it.user?.email?.lowercase()?.contains(q) == true)
            }
        }
        return Result.success(list)
    }

    override suspend fun processAdminRefund(
        transactionId: String,
        amountPaise: Int,
        reason: String,
        adminNotes: String?
    ): Result<RefundItem> {
        if (shouldFail) return Result.failure(Exception(failureMessage))
        val tx = transactions.find { it.id == transactionId }
            ?: return Result.failure(Exception("Transaction not found"))

        val refund = RefundItem(
            id = "rf_${System.currentTimeMillis()}",
            amountPaise = amountPaise,
            status = "PROCESSED",
            reason = reason,
            createdAt = "2026-09-09T01:00:00Z"
        )
        refunds.add(refund)
        return Result.success(refund)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentModuleTests {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakePaymentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePaymentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testProductListFormattingAndMinorUnits() {
        val product = fakeRepository.products[0]
        assertEquals(49900, product.amountPaise)
        assertEquals("₹499.00", product.formattedAmount)
        assertEquals(499.0, product.displayPriceInr, 0.001)

        val aspirantProduct = fakeRepository.products[1]
        assertEquals("₹299.00", aspirantProduct.formattedAmount)

        val alumniProduct = fakeRepository.products[2]
        assertEquals("₹999.00", alumniProduct.formattedAmount)
    }

    @Test
    fun testPaymentViewModelLoadsProductsSuccessfully() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        viewModel.loadProducts("STUDENT")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.products.size)
        assertEquals("STUDENT_PREMIUM_SEMESTER", state.products[0].sku)
    }

    @Test
    fun testPaymentViewModelLoadProductsFailure() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureMessage = "Server unreachable"

        val viewModel = PaymentViewModel(fakeRepository)
        viewModel.loadProducts()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Server unreachable", state.error)
        assertTrue(state.products.isEmpty())
    }

    @Test
    fun testStartCheckoutTransitionsToCheckoutState() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        val product = fakeRepository.products[0]

        viewModel.startCheckout(product)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(product, state.selectedProduct)
        assertTrue(state.checkoutState is CheckoutState.Checkout)
        val order = (state.checkoutState as CheckoutState.Checkout).order
        assertEquals(49900, order.amountPaise)
        assertEquals("₹499.00", order.formattedAmount)
        assertNotNull(fakeRepository.lastCreatedKey)
    }

    @Test
    fun testSimulatePaymentSuccessTransitionsToSuccessAndRefreshesData() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        val product = fakeRepository.products[0]

        viewModel.startCheckout(product)
        testDispatcher.scheduler.advanceUntilIdle()

        val order = (viewModel.uiState.value.checkoutState as CheckoutState.Checkout).order
        viewModel.onSimulatePaymentSuccess(order, "UPI")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.checkoutState is CheckoutState.Success)
        val result = (state.checkoutState as CheckoutState.Success).result
        assertEquals("PAID", result.status)
        assertNotNull(result.entitlement)

        // Verifies that loadEntitlements and loadTransactions were automatically called
        assertEquals(1, state.entitlements.size)
        assertEquals(1, state.transactions.size)
        assertEquals(order.orderId, fakeRepository.lastVerifiedOrderId)
    }

    @Test
    fun testSimulatePaymentFailureTransitionsToFailed() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        val product = fakeRepository.products[0]

        viewModel.startCheckout(product)
        testDispatcher.scheduler.advanceUntilIdle()

        val order = (viewModel.uiState.value.checkoutState as CheckoutState.Checkout).order
        viewModel.onSimulatePaymentFailure(order, "Card declined by issuing bank")

        val state = viewModel.uiState.value
        assertTrue(state.checkoutState is CheckoutState.Failed)
        assertEquals("Card declined by issuing bank", (state.checkoutState as CheckoutState.Failed).reason)
    }

    @Test
    fun testCancelCheckoutTransitionsToCancelled() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        val product = fakeRepository.products[0]

        viewModel.startCheckout(product)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCancelPayment()

        val state = viewModel.uiState.value
        assertTrue(state.checkoutState is CheckoutState.Cancelled)
    }

    @Test
    fun testNetworkDisconnectionTransitionsToPendingConfirmation() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        val product = fakeRepository.products[0]

        viewModel.startCheckout(product)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNetworkDisconnectionOrTimeout()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.checkoutState is CheckoutState.PendingConfirmation)
    }

    @Test
    fun testDismissCheckoutResetsToIdle() = runTest {
        val viewModel = PaymentViewModel(fakeRepository)
        val product = fakeRepository.products[0]

        viewModel.startCheckout(product)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissCheckout()

        val state = viewModel.uiState.value
        assertTrue(state.checkoutState is CheckoutState.Idle)
        assertNull(state.selectedProduct)
    }

    @Test
    fun testAdminFinanceOverviewKpisAndFormatting() {
        val overview = fakeRepository.overview
        assertEquals(179700L, overview.grossRevenuePaise)
        assertEquals(1797.0, overview.grossRevenueInr, 0.001)
        assertEquals("₹1797.00", overview.formattedGross)
        assertEquals("₹299.00", overview.formattedRefunded)
        assertEquals("₹1498.00", overview.formattedNet)
        assertEquals(5, overview.totalOrders)
        assertEquals(3, overview.paidTransactions)
        assertEquals(60.0, overview.successRate, 0.01)
    }

    @Test
    fun testAdminFinanceViewModelLoadsOverviewAndTransactions() = runTest {
        // Seed mock transaction
        fakeRepository.transactions.add(
            PaymentTransactionItem(
                id = "tx_admin_1",
                userId = "usr_1",
                productId = "prod_1",
                paymentOrderId = "ord_1",
                amountPaise = 49900,
                providerPaymentId = "pay_rzp_1",
                status = "PAID",
                createdAt = "2026-09-09T00:00:00Z",
                user = TransactionUserInfo(id = "usr_1", email = "student@test.com", role = "STUDENT")
            )
        )

        val viewModel = AdminFinanceViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.overview)
        assertEquals(1797.0, state.overview?.grossRevenueInr ?: 0.0, 0.001)
        assertEquals(1, state.transactions.size)
        assertEquals("tx_admin_1", state.transactions[0].id)
    }

    @Test
    fun testAdminFinanceViewModelFilterStatusAndSearch() = runTest {
        fakeRepository.transactions.add(
            PaymentTransactionItem(
                id = "tx_paid_student",
                userId = "usr_1",
                productId = "prod_1",
                paymentOrderId = "ord_1",
                amountPaise = 49900,
                providerPaymentId = "pay_rzp_1",
                status = "PAID",
                createdAt = "2026-09-09T00:00:00Z",
                user = TransactionUserInfo(id = "usr_1", email = "student@campus.edu", role = "STUDENT")
            )
        )
        fakeRepository.transactions.add(
            PaymentTransactionItem(
                id = "tx_failed_aspirant",
                userId = "usr_2",
                productId = "prod_2",
                paymentOrderId = "ord_2",
                amountPaise = 29900,
                providerPaymentId = "pay_rzp_2",
                status = "FAILED",
                createdAt = "2026-09-09T00:00:00Z",
                user = TransactionUserInfo(id = "usr_2", email = "aspirant@campus.edu", role = "ASPIRANT")
            )
        )

        val viewModel = AdminFinanceViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by PAID
        viewModel.onStatusFilterChanged("PAID")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.transactions.size)
        assertEquals("tx_paid_student", viewModel.uiState.value.transactions[0].id)

        // Search query
        viewModel.onStatusFilterChanged(null)
        viewModel.onSearchQueryChanged("aspirant@campus.edu")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.transactions.size)
        assertEquals("tx_failed_aspirant", viewModel.uiState.value.transactions[0].id)
    }

    @Test
    fun testAdminFinanceViewModelIssueRefundSuccess() = runTest {
        val tx = PaymentTransactionItem(
            id = "tx_to_refund",
            userId = "usr_1",
            productId = "prod_1",
            paymentOrderId = "ord_1",
            amountPaise = 49900,
            providerPaymentId = "pay_rzp_1",
            status = "PAID",
            createdAt = "2026-09-09T00:00:00Z"
        )
        fakeRepository.transactions.add(tx)

        val viewModel = AdminFinanceViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openRefundDialog(tx)
        assertEquals(tx, viewModel.uiState.value.refundDialogTransaction)

        viewModel.executeRefund(
            transactionId = tx.id,
            amountInr = 499.0,
            reason = "Customer requested refund",
            adminNotes = "Approved"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefunding)
        assertNotNull(state.refundSuccessMessage)
        assertNull(state.refundDialogTransaction)
        assertEquals(1, fakeRepository.refunds.size)
        assertEquals(49900, fakeRepository.refunds[0].amountPaise)
    }

    @Test
    fun testAdminFinanceViewModelIssueRefundFailure() = runTest {
        fakeRepository.shouldFail = true
        fakeRepository.failureMessage = "Refund window expired"

        val tx = PaymentTransactionItem(
            id = "tx_expired",
            userId = "usr_1",
            productId = "prod_1",
            paymentOrderId = "ord_1",
            amountPaise = 49900,
            providerPaymentId = "pay_rzp_1",
            status = "PAID",
            createdAt = "2026-09-09T00:00:00Z"
        )

        val viewModel = AdminFinanceViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openRefundDialog(tx)
        viewModel.executeRefund(
            transactionId = tx.id,
            amountInr = 499.0,
            reason = "Late request",
            adminNotes = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefunding)
        assertEquals("Refund window expired", state.refundError)
        assertNotNull(state.refundDialogTransaction) // Dialog stays open on error
    }
}
