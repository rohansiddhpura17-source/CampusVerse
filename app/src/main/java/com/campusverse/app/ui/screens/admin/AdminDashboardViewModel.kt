package com.campusverse.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminDashboardData
import com.campusverse.app.data.model.AdminDashboardMetrics
import com.campusverse.app.data.repository.NetworkAdminRepository
import com.campusverse.app.domain.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminDashboardUiState {
    object Loading : AdminDashboardUiState
    data class Success(val data: AdminDashboardData) : AdminDashboardUiState
    data class Error(val message: String) : AdminDashboardUiState
}

class AdminDashboardViewModel(
    private val repository: AdminRepository = NetworkAdminRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminDashboardUiState>(AdminDashboardUiState.Loading)
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = AdminDashboardUiState.Loading
            repository.getDashboardData()
                .onSuccess { data ->
                    _uiState.value = AdminDashboardUiState.Success(data)
                }
                .onFailure { err ->
                    _uiState.value = AdminDashboardUiState.Error(err.message ?: "Failed to load admin metrics")
                }
        }
    }
}
