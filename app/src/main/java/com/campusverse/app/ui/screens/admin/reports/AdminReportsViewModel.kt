package com.campusverse.app.ui.screens.admin.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminReportItem
import com.campusverse.app.data.repository.NetworkAdminRepository
import com.campusverse.app.domain.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminReportsUiState {
    object Loading : AdminReportsUiState
    data class Success(
        val reports: List<AdminReportItem>,
        val selectedStatus: String = "ALL",
        val selectedTargetType: String = "ALL",
        val reviewingReport: AdminReportItem? = null,
        val feedbackMessage: String? = null
    ) : AdminReportsUiState
    data class Error(val message: String) : AdminReportsUiState
}

class AdminReportsViewModel(
    private val repository: AdminRepository = NetworkAdminRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminReportsUiState>(AdminReportsUiState.Loading)
    val uiState: StateFlow<AdminReportsUiState> = _uiState.asStateFlow()

    private var currentStatus: String = "ALL"
    private var currentTargetType: String = "ALL"

    init {
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            _uiState.value = AdminReportsUiState.Loading
            repository.getReports(status = currentStatus, targetType = currentTargetType)
                .onSuccess { list ->
                    _uiState.value = AdminReportsUiState.Success(
                        reports = list,
                        selectedStatus = currentStatus,
                        selectedTargetType = currentTargetType
                    )
                }
                .onFailure { err ->
                    _uiState.value = AdminReportsUiState.Error(err.message ?: "Failed to load safety reports")
                }
        }
    }

    fun onStatusFilterSelected(status: String) {
        currentStatus = status
        loadReports()
    }

    fun onTargetTypeFilterSelected(targetType: String) {
        currentTargetType = targetType
        loadReports()
    }

    fun onStartReview(report: AdminReportItem?) {
        val current = _uiState.value
        if (current is AdminReportsUiState.Success) {
            _uiState.value = current.copy(reviewingReport = report, feedbackMessage = null)
        }
    }

    fun resolveReport(reportId: String, status: String, actionTaken: String, notes: String?) {
        viewModelScope.launch {
            repository.resolveReport(reportId, status, actionTaken, notes)
                .onSuccess { updated ->
                    val current = _uiState.value
                    if (current is AdminReportsUiState.Success) {
                        val updatedList = current.reports.map {
                            if (it.id == reportId) it.copy(status = status, resolutionNotes = notes) else it
                        }
                        _uiState.value = current.copy(
                            reports = updatedList,
                            reviewingReport = null,
                            feedbackMessage = "Report resolved with action: $actionTaken."
                        )
                    }
                }
                .onFailure { err ->
                    val current = _uiState.value
                    if (current is AdminReportsUiState.Success) {
                        _uiState.value = current.copy(feedbackMessage = "Failed to resolve: ${err.message}")
                    }
                }
        }
    }
}
