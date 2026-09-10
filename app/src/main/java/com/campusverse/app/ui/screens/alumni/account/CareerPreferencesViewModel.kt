package com.campusverse.app.ui.screens.alumni.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.CareerPreferenceData
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CareerPreferencesUiState {
    data object Loading : CareerPreferencesUiState
    data class Success(val preferences: CareerPreferenceData, val isSaving: Boolean = false, val saveMessage: String? = null) : CareerPreferencesUiState
    data class Error(val message: String) : CareerPreferencesUiState
}

class CareerPreferencesViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<CareerPreferencesUiState>(CareerPreferencesUiState.Loading)
    val uiState: StateFlow<CareerPreferencesUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.value = CareerPreferencesUiState.Loading
            repository.getCareerPreferences()
                .onSuccess { prefs ->
                    _uiState.value = CareerPreferencesUiState.Success(prefs)
                }
                .onFailure { err ->
                    _uiState.value = CareerPreferencesUiState.Error(err.message ?: "Failed to load preferences.")
                }
        }
    }

    fun savePreferences(updated: CareerPreferenceData) {
        val current = _uiState.value as? CareerPreferencesUiState.Success ?: return
        _uiState.value = current.copy(isSaving = true, saveMessage = null)

        viewModelScope.launch {
            repository.updateCareerPreferences(updated)
                .onSuccess { saved ->
                    _uiState.value = CareerPreferencesUiState.Success(saved, isSaving = false, saveMessage = "Preferences saved successfully!")
                }
                .onFailure { err ->
                    _uiState.value = current.copy(isSaving = false, saveMessage = err.message ?: "Update failed.")
                }
        }
    }
}
