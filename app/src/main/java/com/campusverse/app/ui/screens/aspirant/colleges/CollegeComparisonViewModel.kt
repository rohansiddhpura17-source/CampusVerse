package com.campusverse.app.ui.screens.aspirant.colleges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.CollegeComparisonItem
import com.campusverse.app.data.model.CollegeItem
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CollegeComparisonUiState {
    data object Loading : CollegeComparisonUiState
    data class Success(
        val availableColleges: List<CollegeItem>,
        val selectedCollegeIds: List<String>,
        val comparisonResults: List<CollegeComparisonItem>
    ) : CollegeComparisonUiState
    data class Error(val message: String) : CollegeComparisonUiState
}

class CollegeComparisonViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<CollegeComparisonUiState>(CollegeComparisonUiState.Loading)
    val uiState: StateFlow<CollegeComparisonUiState> = _uiState.asStateFlow()

    private val selectedIds = mutableListOf<String>()
    private var allColleges = listOf<CollegeItem>()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = CollegeComparisonUiState.Loading
            repository.getColleges().onSuccess { colleges ->
                allColleges = colleges
                if (selectedIds.isEmpty() && colleges.size >= 2) {
                    selectedIds.add(colleges[0].id)
                    selectedIds.add(colleges[1].id)
                }
                compareSelected()
            }.onFailure { err ->
                _uiState.value = CollegeComparisonUiState.Error(err.message ?: "Failed to load colleges")
            }
        }
    }

    fun toggleCollegeSelection(id: String) {
        if (selectedIds.contains(id)) {
            if (selectedIds.size > 2) {
                selectedIds.remove(id)
                compareSelected()
            }
        } else {
            if (selectedIds.size < 4) {
                selectedIds.add(id)
                compareSelected()
            }
        }
    }

    private fun compareSelected() {
        viewModelScope.launch {
            _uiState.value = CollegeComparisonUiState.Loading
            repository.compareColleges(selectedIds).onSuccess { results ->
                _uiState.value = CollegeComparisonUiState.Success(
                    availableColleges = allColleges,
                    selectedCollegeIds = selectedIds.toList(),
                    comparisonResults = results
                )
            }.onFailure { err ->
                _uiState.value = CollegeComparisonUiState.Error(err.message ?: "Failed to compare colleges")
            }
        }
    }
}
