package com.campusverse.app.ui.screens.student.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.StudentProfileData
import com.campusverse.app.data.repository.CurrentUserProfile
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.data.repository.UserProfileRepository
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StudentProfileUiState {
    data object Loading : StudentProfileUiState
    data class Success(
        val profile: StudentProfileData,
        val isSaving: Boolean = false,
        val saveSuccessMessage: String? = null,
        val errorMessage: String? = null
    ) : StudentProfileUiState
    data class Error(val message: String) : StudentProfileUiState
}

class StudentProfileViewModel(
    private val repository: StudentRepository = NetworkStudentRepository.instance,
    private val userProfileRepository: UserProfileRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<StudentProfileUiState>(StudentProfileUiState.Loading)
    val uiState: StateFlow<StudentProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = StudentProfileUiState.Loading
            try {
                val result = repository.getStudentProfile()
                val p = result.getOrNull()
                if (p != null) {
                    _uiState.value = StudentProfileUiState.Success(profile = p)
                } else {
                    _uiState.value = StudentProfileUiState.Error("Failed to load profile data.")
                }
            } catch (e: Exception) {
                _uiState.value = StudentProfileUiState.Error(e.message ?: "Failed to load profile.")
            }
        }
    }

    fun saveProfile(
        fullName: String,
        bio: String?,
        location: String?,
        degree: String,
        branch: String,
        semester: Int,
        cgpa: Double,
        skills: List<String>
    ) {
        val currState = _uiState.value as? StudentProfileUiState.Success ?: return

        // Validation
        if (fullName.isBlank()) {
            _uiState.value = currState.copy(errorMessage = "Full name cannot be blank.")
            return
        }
        if (cgpa < 0.0 || cgpa > 10.0) {
            _uiState.value = currState.copy(errorMessage = "CGPA must be between 0.0 and 10.0.")
            return
        }
        if (semester < 1 || semester > 12) {
            _uiState.value = currState.copy(errorMessage = "Semester must be between 1 and 12.")
            return
        }

        val updatedProfile = currState.profile.copy(
            fullName = fullName.trim(),
            bio = bio?.trim(),
            location = location?.trim(),
            degree = degree.trim(),
            branch = branch.trim(),
            semester = semester,
            cgpa = cgpa,
            skills = skills
        )

        _uiState.value = currState.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val result = repository.updateStudentProfile(updatedProfile)
                if (result.isSuccess) {
                    try {
                        userProfileRepository?.updateProfile(
                            CurrentUserProfile(
                                userId = updatedProfile.userId,
                                name = updatedProfile.fullName,
                                email = updatedProfile.email,
                                photoUrl = updatedProfile.avatarUrl,
                                role = com.campusverse.app.data.model.UserRole.STUDENT,
                                profileData = updatedProfile
                            )
                        )
                    } catch (_: Exception) {}

                    _uiState.value = StudentProfileUiState.Success(
                        profile = updatedProfile,
                        isSaving = false,
                        saveSuccessMessage = "Student profile saved successfully!"
                    )
                } else {
                    _uiState.value = currState.copy(
                        isSaving = false,
                        errorMessage = "Failed to update profile on backend."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = currState.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Error saving profile."
                )
            }
        }
    }

    fun clearMessages() {
        val curr = _uiState.value as? StudentProfileUiState.Success ?: return
        _uiState.value = curr.copy(saveSuccessMessage = null, errorMessage = null)
    }
}
