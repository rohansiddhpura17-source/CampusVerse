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
            repository.getNotifications()
                .onSuccess { notifs ->
                    _uiState.value = AspirantNotificationsUiState.Success(notifs)
                }
                .onFailure {
                    _uiState.value = AspirantNotificationsUiState.Error("Failed to load notifications")
                }
        }
    }

    fun markAsRead(id: String) {
        val current = (_uiState.value as? AspirantNotificationsUiState.Success)?.notifications ?: return
        val updated = current.map { if (it.id == id) it.copy(isRead = true) else it }
        _uiState.value = AspirantNotificationsUiState.Success(updated)
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllAsRead() {
        val current = (_uiState.value as? AspirantNotificationsUiState.Success)?.notifications ?: return
        val updated = current.map { it.copy(isRead = true) }
        _uiState.value = AspirantNotificationsUiState.Success(updated)
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }
}
