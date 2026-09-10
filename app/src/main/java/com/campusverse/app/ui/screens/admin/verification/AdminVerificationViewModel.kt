package com.campusverse.app.ui.screens.admin.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminVerificationItem
import com.campusverse.app.data.repository.NetworkAdminRepository
import com.campusverse.app.domain.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminVerificationUiState {
    object Loading : AdminVerificationUiState
    data class Success(
        val verifications: List<AdminVerificationItem>,
        val selectedStatus: String = "ALL",
        val reviewingItem: AdminVerificationItem? = null,
        val feedbackMessage: String? = null
    ) : AdminVerificationUiState
    data class Error(val message: String) : AdminVerificationUiState
}

class AdminVerificationViewModel(
    private val repository: AdminRepository = NetworkAdminRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminVerificationUiState>(AdminVerificationUiState.Loading)
    val uiState: StateFlow<AdminVerificationUiState> = _uiState.asStateFlow()

    private var currentFilter: String = "ALL"

    init {
        loadVerifications()
    }

    fun loadVerifications() {
        viewModelScope.launch {
            _uiState.value = AdminVerificationUiState.Loading
            repository.getVerifications(status = currentFilter)
                .onSuccess { list ->
                    _uiState.value = AdminVerificationUiState.Success(
                        verifications = list,
                        selectedStatus = currentFilter
                    )
                }
                .onFailure { err ->
                    _uiState.value = AdminVerificationUiState.Error(err.message ?: "Failed to load verifications")
                }
        }
    }

    fun onFilterSelected(status: String) {
        currentFilter = status
        loadVerifications()
    }

    fun onStartReview(item: AdminVerificationItem?) {
        val current = _uiState.value
        if (current is AdminVerificationUiState.Success) {
            _uiState.value = current.copy(reviewingItem = item, feedbackMessage = null)
        }
    }

    fun submitReview(verificationId: String, decision: String, reason: String? = null) {
        viewModelScope.launch {
            repository.reviewVerification(verificationId, decision, reason)
                .onSuccess { updated ->
                    val current = _uiState.value
                    if (current is AdminVerificationUiState.Success) {
                        val updatedList = current.verifications.map {
                            if (it.id == verificationId) it.copy(status = decision, rejectionReason = reason) else it
                        }
                        _uiState.value = current.copy(
                            verifications = updatedList,
                            reviewingItem = null,
                            feedbackMessage = "Verification record $decision successfully."
                        )
                    }
                }
                .onFailure { err ->
                    val current = _uiState.value
                    if (current is AdminVerificationUiState.Success) {
                        _uiState.value = current.copy(feedbackMessage = "Error: ${err.message}")
                    }
                }
        }
    }
}
