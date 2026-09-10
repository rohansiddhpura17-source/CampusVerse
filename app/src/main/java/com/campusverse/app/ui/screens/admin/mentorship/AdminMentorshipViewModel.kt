package com.campusverse.app.ui.screens.admin.mentorship

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminMentorItem
import com.campusverse.app.data.repository.NetworkAdminRepository
import com.campusverse.app.domain.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminMentorshipUiState {
    object Loading : AdminMentorshipUiState
    data class Success(
        val mentors: List<AdminMentorItem>,
        val feedbackMessage: String? = null
    ) : AdminMentorshipUiState
    data class Error(val message: String) : AdminMentorshipUiState
}

class AdminMentorshipViewModel(
    private val repository: AdminRepository = NetworkAdminRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminMentorshipUiState>(AdminMentorshipUiState.Loading)
    val uiState: StateFlow<AdminMentorshipUiState> = _uiState.asStateFlow()

    init {
        loadMentors()
    }

    fun loadMentors() {
        viewModelScope.launch {
            _uiState.value = AdminMentorshipUiState.Loading
            repository.getMentors()
                .onSuccess { list ->
                    _uiState.value = AdminMentorshipUiState.Success(mentors = list)
                }
                .onFailure { err ->
                    _uiState.value = AdminMentorshipUiState.Error(err.message ?: "Failed to load mentorship directory")
                }
        }
    }

    fun moderateMentor(mentorId: String, action: String) {
        viewModelScope.launch {
            repository.moderateMentor(mentorId, action)
                .onSuccess {
                    val current = _uiState.value
                    if (current is AdminMentorshipUiState.Success) {
                        val updated = current.mentors.map {
                            if (it.id == mentorId) it.copy(isAcceptingMentees = action == "APPROVE") else it
                        }
                        _uiState.value = current.copy(mentors = updated, feedbackMessage = "Mentor status updated.")
                    }
                }
        }
    }
}
