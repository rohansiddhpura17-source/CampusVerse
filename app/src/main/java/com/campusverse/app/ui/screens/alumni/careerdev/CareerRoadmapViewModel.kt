package com.campusverse.app.ui.screens.alumni.careerdev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.CareerRoadmapItem
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CareerRoadmapUiState {
    data object Loading : CareerRoadmapUiState
    data class Success(val roadmaps: List<CareerRoadmapItem>) : CareerRoadmapUiState
    data class Error(val message: String) : CareerRoadmapUiState
}

class CareerRoadmapViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<CareerRoadmapUiState>(CareerRoadmapUiState.Loading)
    val uiState: StateFlow<CareerRoadmapUiState> = _uiState.asStateFlow()

    init {
        loadRoadmaps()
    }

    fun loadRoadmaps() {
        viewModelScope.launch {
            _uiState.value = CareerRoadmapUiState.Loading
            repository.getRoadmaps()
                .onSuccess { list ->
                    _uiState.value = CareerRoadmapUiState.Success(list)
                }
                .onFailure { err ->
                    _uiState.value = CareerRoadmapUiState.Error(err.message ?: "Failed to load career roadmaps.")
                }
        }
    }

    fun toggleMilestone(roadmapId: String, milestoneId: String, currentCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateRoadmapMilestone(roadmapId, milestoneId, !currentCompleted)
            loadRoadmaps()
        }
    }

    fun createRoadmap(title: String, targetRole: String, milestones: List<String>) {
        viewModelScope.launch {
            repository.createRoadmap(title, targetRole, milestones)
            loadRoadmaps()
        }
    }
}
