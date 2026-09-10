package com.campusverse.app.ui.screens.alumni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AlumniHomeSummary
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlumniHomeUiState {
    data object Loading : AlumniHomeUiState
    data class Success(val summary: AlumniHomeSummary) : AlumniHomeUiState
    data class Error(val message: String) : AlumniHomeUiState
}

class AlumniHomeViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance,
    private val userProfileRepository: com.campusverse.app.data.repository.UserProfileRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlumniHomeUiState>(AlumniHomeUiState.Loading)
    val uiState: StateFlow<AlumniHomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeSummary()
    }

    fun loadHomeSummary() {
        viewModelScope.launch {
            _uiState.value = AlumniHomeUiState.Loading
            try {
                userProfileRepository?.loadProfile(preferredRole = com.campusverse.app.data.model.UserRole.ALUMNI)
            } catch (_: Exception) {}
            repository.getAlumniHomeSummary()
                .onSuccess { summary ->
                    _uiState.value = AlumniHomeUiState.Success(summary)
                }
                .onFailure { error ->
                    _uiState.value = AlumniHomeUiState.Error(error.message ?: "Failed to load alumni dashboard.")
                }
        }
    }

    fun saveJob(jobId: String) {
        viewModelScope.launch {
            repository.saveJob(jobId)
            loadHomeSummary()
        }
    }

    fun registerEvent(eventId: String) {
        viewModelScope.launch {
            repository.registerForEvent(eventId)
            loadHomeSummary()
        }
    }
}
