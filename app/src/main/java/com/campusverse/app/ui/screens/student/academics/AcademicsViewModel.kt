package com.campusverse.app.ui.screens.student.academics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AcademicSummary
import com.campusverse.app.data.model.CourseItem
import com.campusverse.app.data.model.StudentAcademicPreferences
import com.campusverse.app.data.preferences.ModulePreferencesManager
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AcademicsUiState {
    data object Loading : AcademicsUiState
    data object PreScreenRequired : AcademicsUiState
    data class Success(
        val summary: AcademicSummary,
        val preferences: StudentAcademicPreferences?,
        val filteredCourses: List<CourseItem>,
        val searchQuery: String = "",
        val selectedSemester: Int? = null
    ) : AcademicsUiState
    data class Error(val message: String) : AcademicsUiState
}

class AcademicsViewModel(
    private val repository: StudentRepository = NetworkStudentRepository.instance,
    private var prefsManager: ModulePreferencesManager? = ModulePreferencesManager.getInstanceOrNull()
) : androidx.lifecycle.ViewModel() {

    constructor(application: Application) : this(
        repository = NetworkStudentRepository.instance,
        prefsManager = ModulePreferencesManager.getInstance(application)
    )

    fun setPreferencesManager(manager: ModulePreferencesManager) {
        this.prefsManager = manager
    }

    private val _uiState = MutableStateFlow<AcademicsUiState>(AcademicsUiState.Loading)
    val uiState: StateFlow<AcademicsUiState> = _uiState.asStateFlow()

    init {
        // If no prefsManager is provided (e.g. unit testing), load immediately
        if (prefsManager == null) {
            viewModelScope.launch {
                loadAcademicsInternal("default_student")
            }
        }
    }

    fun loadAcademics(userId: String = "default_student") {
        viewModelScope.launch {
            loadAcademicsInternal(userId)
        }
    }

    fun checkAndLoadAcademics(currentUser: AuthenticatedUser?, forcePreferencesEdit: Boolean = false) {
        viewModelScope.launch {
            val userId = currentUser?.userId ?: "default_student"
            val isSetupDone = prefsManager?.isModuleSetupCompleted(userId, "student_academics") ?: false

            if (!isSetupDone || forcePreferencesEdit) {
                _uiState.value = AcademicsUiState.PreScreenRequired
                return@launch
            }

            loadAcademicsInternal(userId)
        }
    }

    fun onPreScreenCompleted(currentUser: AuthenticatedUser?) {
        val userId = currentUser?.userId ?: "default_student"
        viewModelScope.launch {
            loadAcademicsInternal(userId)
        }
    }

    private suspend fun loadAcademicsInternal(userId: String) {
        _uiState.value = AcademicsUiState.Loading
        try {
            val prefsJson = prefsManager?.getModulePreferences(userId, "student_academics")
            val prefs = prefsJson?.let { StudentAcademicPreferences.fromJson(it) }

            val result = repository.getAcademicSummary()
            val summary = result.getOrNull()
            if (summary != null) {
                _uiState.value = AcademicsUiState.Success(
                    summary = summary,
                    preferences = prefs,
                    filteredCourses = summary.courses
                )
            } else {
                _uiState.value = AcademicsUiState.Error("Could not retrieve academic records.")
            }
        } catch (e: Exception) {
            _uiState.value = AcademicsUiState.Error(e.message ?: "Failed to load academics.")
        }
    }

    fun onSearchQueryChange(query: String) {
        val currentState = _uiState.value as? AcademicsUiState.Success ?: return
        val filtered = currentState.summary.courses.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.code.contains(query, ignoreCase = true) ||
            (it.department?.contains(query, ignoreCase = true) == true)
        }
        _uiState.value = currentState.copy(
            searchQuery = query,
            filteredCourses = filtered
        )
    }

    fun editPreferences() {
        _uiState.value = AcademicsUiState.PreScreenRequired
    }
}
