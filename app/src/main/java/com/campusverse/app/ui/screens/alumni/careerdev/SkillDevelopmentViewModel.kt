package com.campusverse.app.ui.screens.alumni.careerdev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.SkillItem
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SkillDevelopmentUiState {
    data object Loading : SkillDevelopmentUiState
    data class Success(val skills: List<SkillItem>) : SkillDevelopmentUiState
    data class Error(val message: String) : SkillDevelopmentUiState
}

class SkillDevelopmentViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<SkillDevelopmentUiState>(SkillDevelopmentUiState.Loading)
    val uiState: StateFlow<SkillDevelopmentUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.value = SkillDevelopmentUiState.Loading
            repository.getSkills()
                .onSuccess { skills ->
                    _uiState.value = SkillDevelopmentUiState.Success(skills)
                }
                .onFailure { err ->
                    _uiState.value = SkillDevelopmentUiState.Error(err.message ?: "Failed to load skills.")
                }
        }
    }

    fun upsertSkill(skillName: String, category: String, level: String) {
        viewModelScope.launch {
            repository.upsertSkill(skillName, category, level)
            loadSkills()
        }
    }

    fun deleteSkill(skillId: String) {
        viewModelScope.launch {
            repository.deleteSkill(skillId)
            loadSkills()
        }
    }
}
