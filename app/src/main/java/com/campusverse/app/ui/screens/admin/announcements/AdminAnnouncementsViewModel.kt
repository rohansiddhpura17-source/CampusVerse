package com.campusverse.app.ui.screens.admin.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminAnnouncementItem
import com.campusverse.app.data.repository.NetworkAdminRepository
import com.campusverse.app.domain.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminAnnouncementsUiState {
    object Loading : AdminAnnouncementsUiState
    data class Success(
        val announcements: List<AdminAnnouncementItem>,
        val isComposing: Boolean = false,
        val feedbackMessage: String? = null
    ) : AdminAnnouncementsUiState
    data class Error(val message: String) : AdminAnnouncementsUiState
}

class AdminAnnouncementsViewModel(
    private val repository: AdminRepository = NetworkAdminRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminAnnouncementsUiState>(AdminAnnouncementsUiState.Loading)
    val uiState: StateFlow<AdminAnnouncementsUiState> = _uiState.asStateFlow()

    init {
        loadAnnouncements()
    }

    fun loadAnnouncements() {
        viewModelScope.launch {
            _uiState.value = AdminAnnouncementsUiState.Loading
            repository.getAnnouncements()
                .onSuccess { list ->
                    _uiState.value = AdminAnnouncementsUiState.Success(announcements = list)
                }
                .onFailure { err ->
                    _uiState.value = AdminAnnouncementsUiState.Error(err.message ?: "Failed to load announcements")
                }
        }
    }

    fun toggleCompose(composing: Boolean) {
        val current = _uiState.value
        if (current is AdminAnnouncementsUiState.Success) {
            _uiState.value = current.copy(isComposing = composing, feedbackMessage = null)
        }
    }

    fun publishAnnouncement(title: String, content: String, targetRole: String?, priority: String) {
        viewModelScope.launch {
            repository.createAnnouncement(title, content, targetRole, priority)
                .onSuccess { item ->
                    val current = _uiState.value
                    if (current is AdminAnnouncementsUiState.Success) {
                        _uiState.value = current.copy(
                            announcements = listOf(item) + current.announcements,
                            isComposing = false,
                            feedbackMessage = "Broadcast announcement published to ${item.deliveredCount} users!"
                        )
                    }
                }
                .onFailure { err ->
                    val current = _uiState.value
                    if (current is AdminAnnouncementsUiState.Success) {
                        _uiState.value = current.copy(feedbackMessage = "Failed: ${err.message}")
                    }
                }
        }
    }
}
