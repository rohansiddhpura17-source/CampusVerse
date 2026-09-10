package com.campusverse.app.ui.screens.admin.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminFinanceOverview
import com.campusverse.app.data.model.PaymentTransactionItem
import com.campusverse.app.domain.payment.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AdminFinanceUiState(
    val overview: AdminFinanceOverview? = null,
    val isOverviewLoading: Boolean = false,
    val transactions: List<PaymentTransactionItem> = emptyList(),
    val isTransactionsLoading: Boolean = false,
    val error: String? = null,
    val selectedStatus: String? = null,
    val selectedRole: String? = null,
    val searchQuery: String = "",
    val refundDialogTransaction: PaymentTransactionItem? = null,
    val isRefunding: Boolean = false,
    val refundError: String? = null,
    val refundSuccessMessage: String? = null
)

class AdminFinanceViewModel(
    private val repository: PaymentRepository = com.campusverse.app.data.repository.NetworkPaymentRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminFinanceUiState())
    val uiState: StateFlow<AdminFinanceUiState> = _uiState.asStateFlow()

    init {
        loadOverview()
        loadTransactions()
    }

    fun loadOverview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOverviewLoading = true) }
            repository.getAdminFinanceOverview()
                .onSuccess { overview ->
                    _uiState.update { it.copy(isOverviewLoading = false, overview = overview) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isOverviewLoading = false, error = err.message) }
                }
        }
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransactionsLoading = true) }
            val status = _uiState.value.selectedStatus
            val role = _uiState.value.selectedRole
            val search = _uiState.value.searchQuery.takeIf { it.isNotBlank() }

            repository.getAdminFinanceTransactions(
                status = status,
                role = role,
                search = search
            ).onSuccess { txs ->
                _uiState.update { it.copy(isTransactionsLoading = false, transactions = txs) }
            }.onFailure { err ->
                _uiState.update { it.copy(isTransactionsLoading = false, error = err.message) }
            }
        }
    }

    fun onStatusFilterChanged(status: String?) {
        _uiState.update { it.copy(selectedStatus = status) }
        loadTransactions()
    }

    fun onRoleFilterChanged(role: String?) {
        _uiState.update { it.copy(selectedRole = role) }
        loadTransactions()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadTransactions()
    }

    fun openRefundDialog(tx: PaymentTransactionItem) {
        _uiState.update {
            it.copy(
                refundDialogTransaction = tx,
                refundError = null,
                refundSuccessMessage = null
            )
        }
    }

    fun closeRefundDialog() {
        _uiState.update {
            it.copy(
                refundDialogTransaction = null,
                refundError = null,
                isRefunding = false
            )
        }
    }

    fun executeRefund(transactionId: String, amountInr: Double, reason: String, adminNotes: String?) {
        val amountPaise = (amountInr * 100).roundToInt()
        if (amountPaise <= 0) {
            _uiState.update { it.copy(refundError = "Please enter a valid refund amount greater than 0.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefunding = true, refundError = null) }
            repository.processAdminRefund(
                transactionId = transactionId,
                amountPaise = amountPaise,
                reason = reason,
                adminNotes = adminNotes
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isRefunding = false,
                        refundDialogTransaction = null,
                        refundSuccessMessage = "Refund processed successfully."
                    )
                }
                // Refresh overview and transactions
                loadOverview()
                loadTransactions()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRefunding = false,
                        refundError = err.message ?: "Failed to process refund."
                    )
                }
            }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(refundSuccessMessage = null) }
    }
}
