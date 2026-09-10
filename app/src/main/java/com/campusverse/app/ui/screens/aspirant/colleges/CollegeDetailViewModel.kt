package com.campusverse.app.ui.screens.aspirant.colleges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.CollegeItem
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CollegeDetailUiState {
    data object Loading : CollegeDetailUiState
    data class Success(val college: CollegeItem) : CollegeDetailUiState
    data class Error(val message: String) : CollegeDetailUiState
}

class CollegeDetailViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<CollegeDetailUiState>(CollegeDetailUiState.Loading)
    val uiState: StateFlow<CollegeDetailUiState> = _uiState.asStateFlow()

    private var currentCollegeId: String? = null

    fun loadCollege(id: String) {
        currentCollegeId = id
        viewModelScope.launch {
            _uiState.value = CollegeDetailUiState.Loading
            repository.getCollegeById(id)
                .onSuccess { college ->
                    _uiState.value = CollegeDetailUiState.Success(college)
                }
                .onFailure { err ->
                    _uiState.value = CollegeDetailUiState.Error(err.message ?: "Failed to load college details")
                }
        }
    }

    fun toggleSave() {
        val state = _uiState.value
        if (state is CollegeDetailUiState.Success) {
            val isCurrentlySaved = state.college.isSaved
            viewModelScope.launch {
                if (isCurrentlySaved) {
                    repository.unsaveCollege(state.college.id)
                } else {
                    repository.saveCollege(state.college.id)
                }
                _uiState.value = CollegeDetailUiState.Success(state.college.copy(isSaved = !isCurrentlySaved))
            }
        }
    }
}
