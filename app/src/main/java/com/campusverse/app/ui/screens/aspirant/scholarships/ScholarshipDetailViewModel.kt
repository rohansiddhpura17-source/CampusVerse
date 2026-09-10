package com.campusverse.app.ui.screens.aspirant.scholarships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.ScholarshipItem
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScholarshipDetailUiState {
    data object Loading : ScholarshipDetailUiState
    data class Success(val scholarship: ScholarshipItem) : ScholarshipDetailUiState
    data class Error(val message: String) : ScholarshipDetailUiState
}

class ScholarshipDetailViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScholarshipDetailUiState>(ScholarshipDetailUiState.Loading)
    val uiState: StateFlow<ScholarshipDetailUiState> = _uiState.asStateFlow()

    private var currentScholarshipId: String? = null

    fun loadScholarship(id: String) {
        currentScholarshipId = id
        viewModelScope.launch {
            _uiState.value = ScholarshipDetailUiState.Loading
            repository.getScholarshipById(id)
                .onSuccess { scholarship ->
                    _uiState.value = ScholarshipDetailUiState.Success(scholarship)
                }
                .onFailure { err ->
                    _uiState.value = ScholarshipDetailUiState.Error(err.message ?: "Failed to load scholarship details")
                }
        }
    }

    fun toggleSave() {
        val state = _uiState.value
        if (state is ScholarshipDetailUiState.Success) {
            val isCurrentlySaved = state.scholarship.isSaved
            viewModelScope.launch {
                if (isCurrentlySaved) {
                    repository.unsaveScholarship(state.scholarship.id)
                } else {
                    repository.saveScholarship(state.scholarship.id)
                }
                _uiState.value = ScholarshipDetailUiState.Success(state.scholarship.copy(isSaved = !isCurrentlySaved))
            }
        }
    }
}
