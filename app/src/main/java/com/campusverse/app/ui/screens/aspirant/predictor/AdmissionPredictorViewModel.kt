package com.campusverse.app.ui.screens.aspirant.predictor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdmissionPredictionItem
import com.campusverse.app.data.model.CollegeItem
import com.campusverse.app.data.model.PredictionRequest
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdmissionPredictorUiState {
    data class Form(
        val colleges: List<CollegeItem> = emptyList(),
        val selectedCollege: CollegeItem? = null,
        val programName: String = "B.Tech in Computer Science and Engineering",
        val degree: String = "B.TECH",
        val gpa: String = "9.2",
        val testType: String = "JEE_MAIN",
        val testScore: String = "98.4",
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null
    ) : AdmissionPredictorUiState

    data class ResultSuccess(
        val prediction: AdmissionPredictionItem,
        val history: List<AdmissionPredictionItem> = emptyList()
    ) : AdmissionPredictorUiState
}

class AdmissionPredictorViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdmissionPredictorUiState>(AdmissionPredictorUiState.Form())
    val uiState: StateFlow<AdmissionPredictorUiState> = _uiState.asStateFlow()

    private var availableColleges = listOf<CollegeItem>()
    private var predictionHistory = listOf<AdmissionPredictionItem>()

    init {
        loadInitialData()
    }

    fun loadInitialData(presetCollegeId: String? = null) {
        viewModelScope.launch {
            repository.getColleges().onSuccess { colleges ->
                availableColleges = colleges
                val selected = if (presetCollegeId != null) {
                    colleges.find { it.id == presetCollegeId } ?: colleges.firstOrNull()
                } else colleges.firstOrNull()

                _uiState.value = AdmissionPredictorUiState.Form(
                    colleges = colleges,
                    selectedCollege = selected
                )
            }

            repository.getPredictionHistory().onSuccess { history ->
                predictionHistory = history
            }
        }
    }

    fun onCollegeSelected(college: CollegeItem) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(
                selectedCollege = college,
                programName = college.programs.firstOrNull()?.name ?: "B.Tech in Computer Science and Engineering"
            )
        }
    }

    fun onProgramNameChanged(name: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(programName = name)
        }
    }

    fun onDegreeChanged(degree: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(degree = degree)
        }
    }

    fun onGpaChanged(gpa: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(gpa = gpa, errorMessage = null)
        }
    }

    fun onTestTypeChanged(testType: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(testType = testType)
        }
    }

    fun onTestScoreChanged(score: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(testScore = score, errorMessage = null)
        }
    }

    fun submitPrediction() {
        val current = _uiState.value
        if (current !is AdmissionPredictorUiState.Form) return

        val gpaVal = current.gpa.toDoubleOrNull()
        if (gpaVal == null || gpaVal < 0.0 || gpaVal > 10.0) {
            _uiState.value = current.copy(errorMessage = "Please enter a valid GPA between 0.0 and 10.0")
            return
        }

        val scoreVal = current.testScore.toDoubleOrNull()
        if (scoreVal == null || scoreVal < 0.0) {
            _uiState.value = current.copy(errorMessage = "Please enter a valid standardized test score")
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isSubmitting = true, errorMessage = null)
            val request = PredictionRequest(
                institutionId = current.selectedCollege?.id,
                institutionName = current.selectedCollege?.name ?: "Target Institution",
                programName = current.programName,
                degree = current.degree,
                gpa = gpaVal,
                testType = current.testType,
                testScore = scoreVal
            )

            repository.predictAdmission(request)
                .onSuccess { result ->
                    val updatedHistory = listOf(result) + predictionHistory
                    _uiState.value = AdmissionPredictorUiState.ResultSuccess(
                        prediction = result,
                        history = updatedHistory
                    )
                }
                .onFailure { err ->
                    _uiState.value = current.copy(isSubmitting = false, errorMessage = err.message ?: "Failed to calculate prediction")
                }
        }
    }

    fun resetForm() {
        _uiState.value = AdmissionPredictorUiState.Form(
            colleges = availableColleges,
            selectedCollege = availableColleges.firstOrNull()
        )
    }
}
