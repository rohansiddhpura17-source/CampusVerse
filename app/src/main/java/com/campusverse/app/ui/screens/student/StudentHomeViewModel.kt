package com.campusverse.app.ui.screens.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AcademicSummary
import com.campusverse.app.data.model.CampusEvent
import com.campusverse.app.data.model.NoteItem
import com.campusverse.app.data.model.StudentProfileData
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StudentHomeUiState {
    data object Loading : StudentHomeUiState
    data class Success(
        val profile: StudentProfileData,
        val academics: AcademicSummary,
        val upcomingEvents: List<CampusEvent>,
        val recentNotes: List<NoteItem>,
        val notificationsCount: Int = 3
    ) : StudentHomeUiState
    data class Error(val message: String) : StudentHomeUiState
}

class StudentHomeViewModel(
    private val repository: StudentRepository = NetworkStudentRepository.instance,
    private val userProfileRepository: com.campusverse.app.data.repository.UserProfileRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<StudentHomeUiState>(StudentHomeUiState.Loading)
    val uiState: StateFlow<StudentHomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = StudentHomeUiState.Loading
            try {
                val profileResult = repository.getStudentProfile()
                val academicsResult = repository.getAcademicSummary()
                val eventsResult = repository.getEvents()
                val notesResult = repository.getNotes()

                val profile = profileResult.getOrNull()
                val academics = academicsResult.getOrNull()

                if (profile != null && academics != null) {
                    try {
                        userProfileRepository?.loadProfile(preferredRole = com.campusverse.app.data.model.UserRole.STUDENT)
                    } catch (_: Exception) {}
                    val events = eventsResult.getOrDefault(emptyList())
                    val notes = notesResult.getOrDefault(emptyList())

                    _uiState.value = StudentHomeUiState.Success(
                        profile = profile,
                        academics = academics,
                        upcomingEvents = events.take(3),
                        recentNotes = notes.take(3)
                    )
                } else {
                    _uiState.value = StudentHomeUiState.Error("Failed to load student dashboard data.")
                }
            } catch (e: Exception) {
                _uiState.value = StudentHomeUiState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }
}
