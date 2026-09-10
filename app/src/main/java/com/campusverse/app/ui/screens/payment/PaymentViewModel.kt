package com.campusverse.app.ui.screens.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.EntitlementItem
import com.campusverse.app.data.model.PaymentOrder
import com.campusverse.app.data.model.PaymentProduct
import com.campusverse.app.data.model.PaymentResult
import com.campusverse.app.data.model.PaymentTransactionItem
import com.campusverse.app.domain.payment.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface CheckoutState {
    data object Idle : CheckoutState
    data object Loading : CheckoutState
    data class Checkout(val order: PaymentOrder) : CheckoutState
    data object Verifying : CheckoutState
    data class Success(val result: PaymentResult) : CheckoutState
    data class Failed(val reason: String) : CheckoutState
    data object Cancelled : CheckoutState
    data class PendingConfirmation(val message: String) : CheckoutState
}

data class PaymentUiState(
    val isLoading: Boolean = false,
    val products: List<PaymentProduct> = emptyList(),
    val error: String? = null,
    val checkoutState: CheckoutState = CheckoutState.Idle,
    val selectedProduct: PaymentProduct? = null,
    val transactions: List<PaymentTransactionItem> = emptyList(),
    val isTransactionsLoading: Boolean = false,
    val transactionsError: String? = null,
    val entitlements: List<EntitlementItem> = emptyList(),
    val isEntitlementsLoading: Boolean = false,
    val entitlementsError: String? = null
)

class PaymentViewModel(
    private val repository: PaymentRepository = com.campusverse.app.data.repository.NetworkPaymentRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var isCheckoutInProgress = false

    fun loadProducts(targetRole: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getProducts(targetRole)
                .onSuccess { products ->
                    _uiState.update { it.copy(isLoading = false, products = products, error = null) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message ?: "Failed to load products") }
                }
        }
    }

    fun startCheckout(product: PaymentProduct) {
        if (isCheckoutInProgress) return // Prevent duplicate taps
        isCheckoutInProgress = true

        _uiState.update {
            it.copy(
                selectedProduct = product,
                checkoutState = CheckoutState.Loading
            )
        }

        viewModelScope.launch {
            val idempotencyKey = "ord_${UUID.randomUUID()}"
            repository.createOrder(product.id, idempotencyKey)
                .onSuccess { order ->
                    _uiState.update { it.copy(checkoutState = CheckoutState.Checkout(order)) }
                }
                .onFailure { err ->
                    isCheckoutInProgress = false
                    _uiState.update {
                        it.copy(checkoutState = CheckoutState.Failed(err.message ?: "Failed to initialize order."))
                    }
                }
        }
    }

    fun onSimulatePaymentSuccess(order: PaymentOrder, paymentMethod: String = "UPI") {
        _uiState.update { it.copy(checkoutState = CheckoutState.Verifying) }

        viewModelScope.launch {
            // In Razorpay Sandbox mode, simulate payment ID and signature
            val providerPaymentId = "pay_test_${UUID.randomUUID().toString().take(12)}"
            val providerSignature = "sandbox_sig_${order.providerOrderId}"

            repository.verifyPayment(
                orderId = order.orderId,
                providerPaymentId = providerPaymentId,
                providerSignature = providerSignature,
                paymentMethod = paymentMethod
            ).onSuccess { result ->
                isCheckoutInProgress = false
                _uiState.update { it.copy(checkoutState = CheckoutState.Success(result)) }
                // Reconcile and refresh active entitlements & history from server
                loadEntitlements()
                loadTransactions()
            }.onFailure { err ->
                isCheckoutInProgress = false
                _uiState.update {
                    it.copy(checkoutState = CheckoutState.Failed(err.message ?: "Server verification failed."))
                }
            }
        }
    }

    fun onSimulatePaymentFailure(order: PaymentOrder, reason: String = "Bank server declined transaction.") {
        isCheckoutInProgress = false
        _uiState.update { it.copy(checkoutState = CheckoutState.Failed(reason)) }
    }

    fun onCancelPayment() {
        isCheckoutInProgress = false
        _uiState.update { it.copy(checkoutState = CheckoutState.Cancelled) }
    }

    fun onNetworkDisconnectionOrTimeout() {
        isCheckoutInProgress = false
        _uiState.update {
            it.copy(
                checkoutState = CheckoutState.PendingConfirmation(
                    "Payment processing. Check My Transactions shortly."
                )
            )
        }
        // Background reconcile
        loadTransactions()
        loadEntitlements()
    }

    fun dismissCheckout() {
        isCheckoutInProgress = false
        _uiState.update { it.copy(checkoutState = CheckoutState.Idle, selectedProduct = null) }
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransactionsLoading = true, transactionsError = null) }
            repository.getMyTransactions()
                .onSuccess { txs ->
                    _uiState.update { it.copy(isTransactionsLoading = false, transactions = txs) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isTransactionsLoading = false, transactionsError = err.message) }
                }
        }
    }

    fun loadEntitlements() {
        viewModelScope.launch {
            _uiState.update { it.copy(isEntitlementsLoading = true, entitlementsError = null) }
            repository.getMyEntitlements()
                .onSuccess { ents ->
                    _uiState.update { it.copy(isEntitlementsLoading = false, entitlements = ents) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isEntitlementsLoading = false, entitlementsError = err.message) }
                }
        }
    }
}
