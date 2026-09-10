package com.campusverse.app.ui.screens.aspirant.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AlumniNotificationItem
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AspirantNotificationsUiState {
    data object Loading : AspirantNotificationsUiState
    data class Success(val notifications: List<AlumniNotificationItem>) : AspirantNotificationsUiState
    data class Error(val message: String) : AspirantNotificationsUiState
}

class AspirantNotificationsViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AspirantNotificationsUiState>(AspirantNotificationsUiState.Loading)
    val uiState: StateFlow<AspirantNotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = AspirantNotificationsUiState.Loading
            repository.getAspirantHomeSummary()
                .onSuccess { summary ->
                    val notifs = listOf(
                        AlumniNotificationItem("notif_1", "SYSTEM", "Admission Predictor Updated", "NIT Bangalore CSE prediction calculated at 94.5% (Strong Candidate).", false, "2 hours ago"),
                        AlumniNotificationItem("notif_2", "SCHOLARSHIP", "New Scholarship Matching Your Profile", "National Merit STEM Undergraduate Grant applications are now open.", false, "1 day ago"),
                        AlumniNotificationItem("notif_3", "COLLEGE", "Application Deadline Approaching", "Stanford University Regular Decision deadline in 14 days.", true, "3 days ago")
                    )
                    _uiState.value = AspirantNotificationsUiState.Success(notifs)
                }
                .onFailure {
                    _uiState.value = AspirantNotificationsUiState.Error("Failed to load notifications")
                }
        }
    }
}
