package com.campusverse.app.ui.screens.alumni.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AlumniNotificationItem
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlumniNotificationsUiState {
    data object Loading : AlumniNotificationsUiState
    data class Success(val notifications: List<AlumniNotificationItem>) : AlumniNotificationsUiState
    data class Error(val message: String) : AlumniNotificationsUiState
}

class AlumniNotificationsViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlumniNotificationsUiState>(AlumniNotificationsUiState.Loading)
    val uiState: StateFlow<AlumniNotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = AlumniNotificationsUiState.Loading
            repository.getNotifications()
                .onSuccess { list ->
                    _uiState.value = AlumniNotificationsUiState.Success(list)
                }
                .onFailure { err ->
                    _uiState.value = AlumniNotificationsUiState.Error(err.message ?: "Failed to load notifications.")
                }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
            loadNotifications()
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
            loadNotifications()
        }
    }
}
