package com.campusverse.app.ui.screens.aspirant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AspirantHomeSummary
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AspirantHomeUiState {
    data object Loading : AspirantHomeUiState
    data class Success(val summary: AspirantHomeSummary) : AspirantHomeUiState
    data class Error(val message: String) : AspirantHomeUiState
}

class AspirantHomeViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance,
    private val userProfileRepository: com.campusverse.app.data.repository.UserProfileRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AspirantHomeUiState>(AspirantHomeUiState.Loading)
    val uiState: StateFlow<AspirantHomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeSummary()
    }

    fun loadHomeSummary() {
        viewModelScope.launch {
            _uiState.value = AspirantHomeUiState.Loading
            try {
                userProfileRepository?.loadProfile(preferredRole = com.campusverse.app.data.model.UserRole.ASPIRANT)
            } catch (_: Exception) {}
            repository.getAspirantHomeSummary()
                .onSuccess { summary ->
                    _uiState.value = AspirantHomeUiState.Success(summary)
                }
                .onFailure { error ->
                    _uiState.value = AspirantHomeUiState.Error(error.message ?: "Failed to load aspirant dashboard")
                }
        }
    }

    fun toggleSaveCollege(collegeId: String, currentSaved: Boolean) {
        viewModelScope.launch {
            if (currentSaved) {
                repository.unsaveCollege(collegeId)
            } else {
                repository.saveCollege(collegeId)
            }
            loadHomeSummary()
        }
    }
}
