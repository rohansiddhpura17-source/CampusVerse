package com.campusverse.app.ui.screens.student.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.StudentAcademicPreferences
import com.campusverse.app.data.model.StudentNotesPreferences
import com.campusverse.app.data.preferences.ModulePreferencesManager
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.domain.auth.AuthenticatedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StudentNotesPreScreenUiState {
    data class Content(
        val studyingField: String = "Computer Science and Engineering",
        val selectedPrioritySubjects: Set<String> = setOf("Distributed Systems", "Algorithms & DSA"),
        val selectedResourceTypes: Set<String> = setOf("Notes", "Practice Questions", "Flashcards"),
        val dailyStudyTime: String = "2-4 hours/day",
        val isSaving: Boolean = false,
        val errorMessage: String? = null
    ) : StudentNotesPreScreenUiState
    data object Saved : StudentNotesPreScreenUiState
}

class StudentNotesPreScreenViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefsManager = ModulePreferencesManager.getInstance(application)
    private val studentRepo = NetworkStudentRepository.instance

    private val _uiState = MutableStateFlow<StudentNotesPreScreenUiState>(StudentNotesPreScreenUiState.Content())
    val uiState: StateFlow<StudentNotesPreScreenUiState> = _uiState.asStateFlow()

    fun initialize(currentUser: AuthenticatedUser?) {
        viewModelScope.launch {
            val userId = currentUser?.userId ?: "default_student"
            val existing = prefsManager.getModulePreferences(userId, "student_notes")
            if (existing != null) {
                val prefs = StudentNotesPreferences.fromJson(existing)
                _uiState.value = StudentNotesPreScreenUiState.Content(
                    studyingField = prefs.studyingField,
                    selectedPrioritySubjects = prefs.prioritySubjects.toSet(),
                    selectedResourceTypes = prefs.preferredResourceTypes.toSet(),
                    dailyStudyTime = prefs.dailyStudyTime
                )
            } else {
                // Auto-fill field from academic preferences if available
                val acadPrefsJson = prefsManager.getModulePreferences(userId, "student_academics")
                val branch = if (acadPrefsJson != null) {
                    val acadPrefs = StudentAcademicPreferences.fromJson(acadPrefsJson)
                    acadPrefs.branch
                } else {
                    studentRepo.getStudentProfile().getOrNull()?.branch ?: "Computer Science and Engineering"
                }

                _uiState.value = StudentNotesPreScreenUiState.Content(
                    studyingField = branch
                )
            }
        }
    }

    fun updateStudyingField(field: String) {
        updateState { copy(studyingField = field) }
    }

    fun togglePrioritySubject(subject: String) {
        updateState {
            val updated = selectedPrioritySubjects.toMutableSet()
            if (updated.contains(subject)) {
                if (updated.size > 1) updated.remove(subject)
            } else {
                updated.add(subject)
            }
            copy(selectedPrioritySubjects = updated)
        }
    }

    fun toggleResourceType(type: String) {
        updateState {
            val updated = selectedResourceTypes.toMutableSet()
            if (updated.contains(type)) {
                if (updated.size > 1) updated.remove(type)
            } else {
                updated.add(type)
            }
            copy(selectedResourceTypes = updated)
        }
    }

    fun updateDailyStudyTime(time: String) {
        updateState { copy(dailyStudyTime = time) }
    }

    fun savePreferences(currentUser: AuthenticatedUser?) {
        val state = _uiState.value as? StudentNotesPreScreenUiState.Content ?: return
        if (state.studyingField.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please specify what you are studying.")
            return
        }

        val prefs = StudentNotesPreferences(
            studyingField = state.studyingField.trim(),
            prioritySubjects = state.selectedPrioritySubjects.toList(),
            preferredResourceTypes = state.selectedResourceTypes.toList(),
            dailyStudyTime = state.dailyStudyTime
        )

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val userId = currentUser?.userId ?: "default_student"
            prefsManager.saveModulePreferences(userId, "student_notes", prefs.toJson())
            _uiState.value = StudentNotesPreScreenUiState.Saved
        }
    }

    private inline fun updateState(transform: StudentNotesPreScreenUiState.Content.() -> StudentNotesPreScreenUiState.Content) {
        val current = _uiState.value as? StudentNotesPreScreenUiState.Content ?: return
        _uiState.value = current.transform()
    }
}
