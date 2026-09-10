package com.campusverse.app.ui.screens.alumni.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AlumniProfileData
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlumniProfileDetailUiState {
    data object Loading : AlumniProfileDetailUiState
    data class Success(val profile: AlumniProfileData) : AlumniProfileDetailUiState
    data class Error(val message: String) : AlumniProfileDetailUiState
}

class AlumniProfileDetailViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlumniProfileDetailUiState>(AlumniProfileDetailUiState.Loading)
    val uiState: StateFlow<AlumniProfileDetailUiState> = _uiState.asStateFlow()

    private var currentAlumniId: String = ""

    fun loadProfile(alumniId: String) {
        currentAlumniId = alumniId
        viewModelScope.launch {
            _uiState.value = AlumniProfileDetailUiState.Loading
            repository.getAlumniProfileById(alumniId)
                .onSuccess { profile ->
                    _uiState.value = AlumniProfileDetailUiState.Success(profile)
                }
                .onFailure { error ->
                    _uiState.value = AlumniProfileDetailUiState.Error(error.message ?: "Failed to load alumni profile.")
                }
        }
    }

    fun connect() {
        viewModelScope.launch {
            repository.connectWithAlumni(currentAlumniId)
            loadProfile(currentAlumniId)
        }
    }

    fun toggleSave() {
        val current = (_uiState.value as? AlumniProfileDetailUiState.Success)?.profile ?: return
        viewModelScope.launch {
            if (current.isSaved) {
                repository.unsaveAlumni(currentAlumniId)
            } else {
                repository.saveAlumni(currentAlumniId)
            }
            loadProfile(currentAlumniId)
        }
    }
}
