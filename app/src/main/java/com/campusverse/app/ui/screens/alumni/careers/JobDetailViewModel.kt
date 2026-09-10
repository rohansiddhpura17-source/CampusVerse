package com.campusverse.app.ui.screens.alumni.careers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.JobOpportunity
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JobDetailUiState {
    data object Loading : JobDetailUiState
    data class Success(
        val job: JobOpportunity,
        val isApplying: Boolean = false,
        val applicationSuccess: Boolean = false,
        val applicationError: String? = null
    ) : JobDetailUiState
    data class Error(val message: String) : JobDetailUiState
}

class JobDetailViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<JobDetailUiState>(JobDetailUiState.Loading)
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    private var currentJobId: String = ""

    fun loadJob(jobId: String) {
        currentJobId = jobId
        viewModelScope.launch {
            _uiState.value = JobDetailUiState.Loading
            repository.getJobById(jobId)
                .onSuccess { job ->
                    _uiState.value = JobDetailUiState.Success(job)
                }
                .onFailure { err ->
                    _uiState.value = JobDetailUiState.Error(err.message ?: "Failed to load job details.")
                }
        }
    }

    fun toggleSave() {
        val current = (_uiState.value as? JobDetailUiState.Success)?.job ?: return
        viewModelScope.launch {
            if (current.isSaved) {
                repository.unsaveJob(currentJobId)
            } else {
                repository.saveJob(currentJobId)
            }
            loadJob(currentJobId)
        }
    }

    fun apply(resumeUrl: String, coverLetter: String?) {
        val current = (_uiState.value as? JobDetailUiState.Success) ?: return
        _uiState.value = current.copy(isApplying = true, applicationError = null)

        viewModelScope.launch {
            repository.applyForJob(currentJobId, resumeUrl, coverLetter)
                .onSuccess {
                    _uiState.value = current.copy(
                        isApplying = false,
                        applicationSuccess = true,
                        job = current.job.copy(hasApplied = true, applicationStatus = "APPLIED")
                    )
                }
                .onFailure { err ->
                    _uiState.value = current.copy(
                        isApplying = false,
                        applicationError = err.message ?: "Failed to submit application."
                    )
                }
        }
    }
}
