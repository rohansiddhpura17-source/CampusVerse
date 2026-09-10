package com.campusverse.app.ui.screens.aspirant.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AspirantProfileUiState {
    data object Loading : AspirantProfileUiState
    data class Success(
        val profile: AspirantProfileData,
        val isSaving: Boolean = false,
        val saveSuccess: Boolean = false,
        val errorMessage: String? = null
    ) : AspirantProfileUiState
    data class Error(val message: String) : AspirantProfileUiState
}

class AspirantProfileViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance,
    private val userProfileRepository: com.campusverse.app.data.repository.UserProfileRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<AspirantProfileUiState>(AspirantProfileUiState.Loading)
    val uiState: StateFlow<AspirantProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = AspirantProfileUiState.Loading
            repository.getAspirantProfile()
                .onSuccess { profile ->
                    _uiState.value = AspirantProfileUiState.Success(profile)
                }
                .onFailure { err ->
                    _uiState.value = AspirantProfileUiState.Error(err.message ?: "Failed to load profile")
                }
        }
    }

    fun saveProfile(
        fullName: String,
        bio: String,
        targetDegree: String,
        targetMajor: String,
        targetUniversities: String,
        highSchool: String,
        expectedGradYear: Int,
        entranceExamScores: Map<String, Double> = emptyMap()
    ) {
        val current = _uiState.value as? AspirantProfileUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, saveSuccess = false, errorMessage = null)
            val updated = current.profile.copy(
                fullName = fullName,
                bio = bio,
                targetDegree = targetDegree,
                targetMajor = targetMajor,
                targetUniversities = targetUniversities,
                highSchool = highSchool,
                expectedGradYear = expectedGradYear,
                entranceExamScores = entranceExamScores
            )

            repository.updateAspirantProfile(updated)
                .onSuccess { saved ->
                    try {
                        userProfileRepository?.updateProfile(
                            com.campusverse.app.data.repository.CurrentUserProfile(
                                userId = saved.userId,
                                name = saved.fullName,
                                email = saved.email,
                                photoUrl = saved.avatarUrl,
                                role = com.campusverse.app.data.model.UserRole.ASPIRANT,
                                profileData = saved
                            )
                        )
                    } catch (_: Exception) {}
                    _uiState.value = AspirantProfileUiState.Success(
                        profile = saved,
                        isSaving = false,
                        saveSuccess = true
                    )
                }
                .onFailure { err ->
                    _uiState.value = current.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Failed to save profile changes"
                    )
                }
        }
    }
}
