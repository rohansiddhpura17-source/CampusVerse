package com.campusverse.app.ui.screens.student.academics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.StudentAcademicPreferences
import com.campusverse.app.data.preferences.ModulePreferencesManager
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.domain.auth.AuthenticatedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StudentAcademicsPreScreenUiState {
    data class Content(
        val degree: String = "B.Tech",
        val semester: Int = 6,
        val branch: String = "Computer Science and Engineering",
        val academicGoal: String = "Placement Prep & Top Tech Jobs",
        val selectedFocusAreas: Set<String> = setOf("Algorithms & Data Structures", "System Design & Distributed Systems"),
        val difficultyLevel: String = "Intermediate",
        val recentGpa: String = "8.75",
        val isSaving: Boolean = false,
        val errorMessage: String? = null
    ) : StudentAcademicsPreScreenUiState
    data object Saved : StudentAcademicsPreScreenUiState
}

class StudentAcademicsPreScreenViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefsManager = ModulePreferencesManager.getInstance(application)
    private val studentRepo = NetworkStudentRepository.instance

    private val _uiState = MutableStateFlow<StudentAcademicsPreScreenUiState>(StudentAcademicsPreScreenUiState.Content())
    val uiState: StateFlow<StudentAcademicsPreScreenUiState> = _uiState.asStateFlow()

    fun initialize(currentUser: AuthenticatedUser?) {
        viewModelScope.launch {
            val userId = currentUser?.userId ?: "default_student"
            val existing = prefsManager.getModulePreferences(userId, "student_academics")
            if (existing != null) {
                val prefs = StudentAcademicPreferences.fromJson(existing)
                _uiState.value = StudentAcademicsPreScreenUiState.Content(
                    degree = prefs.degree,
                    semester = prefs.semester,
                    branch = prefs.branch,
                    academicGoal = prefs.academicGoal,
                    selectedFocusAreas = prefs.focusAreas.toSet(),
                    difficultyLevel = prefs.difficultyLevel,
                    recentGpa = prefs.recentGpa?.toString() ?: ""
                )
            } else {
                // Auto-fill from existing student profile
                val profileResult = studentRepo.getStudentProfile()
                val profile = profileResult.getOrNull()
                if (profile != null) {
                    _uiState.value = StudentAcademicsPreScreenUiState.Content(
                        degree = profile.degree.ifBlank { "B.Tech" },
                        semester = profile.semester,
                        branch = profile.branch.ifBlank { "Computer Science and Engineering" },
                        recentGpa = profile.cgpa.toString()
                    )
                }
            }
        }
    }

    fun updateDegree(value: String) {
        updateState { copy(degree = value) }
    }

    fun updateSemester(value: Int) {
        updateState { copy(semester = value) }
    }

    fun updateBranch(value: String) {
        updateState { copy(branch = value) }
    }

    fun updateGoal(value: String) {
        updateState { copy(academicGoal = value) }
    }

    fun toggleFocusArea(area: String) {
        updateState {
            val updated = selectedFocusAreas.toMutableSet()
            if (updated.contains(area)) {
                if (updated.size > 1) updated.remove(area)
            } else {
                updated.add(area)
            }
            copy(selectedFocusAreas = updated)
        }
    }

    fun updateDifficulty(value: String) {
        updateState { copy(difficultyLevel = value) }
    }

    fun updateGpa(value: String) {
        updateState { copy(recentGpa = value, errorMessage = null) }
    }

    fun savePreferences(currentUser: AuthenticatedUser?) {
        val state = _uiState.value as? StudentAcademicsPreScreenUiState.Content ?: return

        val gpaVal = state.recentGpa.toDoubleOrNull()
        if (state.recentGpa.isNotBlank() && (gpaVal == null || gpaVal < 0.0 || gpaVal > 10.0)) {
            _uiState.value = state.copy(errorMessage = "CGPA must be a valid number between 0.0 and 10.0")
            return
        }

        val prefs = StudentAcademicPreferences(
            degree = state.degree,
            semester = state.semester,
            branch = state.branch,
            academicGoal = state.academicGoal,
            focusAreas = state.selectedFocusAreas.toList(),
            difficultyLevel = state.difficultyLevel,
            recentGpa = gpaVal
        )

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val userId = currentUser?.userId ?: "default_student"
            prefsManager.saveModulePreferences(userId, "student_academics", prefs.toJson())
            
            // Sync with student repo profile
            val currentProfile = studentRepo.getStudentProfile().getOrNull()
            if (currentProfile != null) {
                studentRepo.updateStudentProfile(
                    currentProfile.copy(
                        degree = state.degree,
                        branch = state.branch,
                        semester = state.semester,
                        cgpa = gpaVal ?: currentProfile.cgpa
                    )
                )
            }
            _uiState.value = StudentAcademicsPreScreenUiState.Saved
        }
    }

    private inline fun updateState(transform: StudentAcademicsPreScreenUiState.Content.() -> StudentAcademicsPreScreenUiState.Content) {
        val current = _uiState.value as? StudentAcademicsPreScreenUiState.Content ?: return
        _uiState.value = current.transform()
    }
}
