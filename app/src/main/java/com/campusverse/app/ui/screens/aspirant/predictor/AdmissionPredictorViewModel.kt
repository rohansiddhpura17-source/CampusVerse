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
        val programName: String = "",
        val degree: String = "B.TECH",
        val gpa: String = "",
        val testType: String = "JEE_MAIN",
        val testScore: String = "",
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null
    ) : AdmissionPredictorUiState

    data class ResultSuccess(
        val prediction: AdmissionPredictionItem,
        val lastForm: Form? = null,
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
                    selectedCollege = selected,
                    programName = selected?.programs?.firstOrNull()?.name ?: "",
                    degree = selected?.programs?.firstOrNull()?.degree ?: "B.TECH",
                    gpa = "",
                    testType = "JEE_MAIN",
                    testScore = ""
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
                programName = college.programs.firstOrNull()?.name ?: current.programName,
                errorMessage = null
            )
        }
    }

    fun onProgramNameChanged(name: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(programName = name, errorMessage = null)
        }
    }

    fun onDegreeChanged(degree: String) {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.Form) {
            _uiState.value = current.copy(degree = degree, errorMessage = null)
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
            _uiState.value = current.copy(testType = testType, errorMessage = null)
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
        if (current.isSubmitting) return

        if (current.programName.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Please enter or select a target major/program")
            return
        }

        val gpaTrimmed = current.gpa.trim()
        if (gpaTrimmed.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Please enter your 12th GPA (0.0-10.0) or percentage (0-100%)")
            return
        }
        val gpaNum = gpaTrimmed.toDoubleOrNull()
        if (gpaNum == null) {
            _uiState.value = current.copy(errorMessage = "Please enter a valid numeric value for academic performance")
            return
        }
        if (gpaNum < 0.0 || gpaNum > 100.0 || (gpaNum > 10.0 && gpaNum < 35.0)) {
            _uiState.value = current.copy(errorMessage = "Academic score must be between 0.0 - 10.0 (GPA) or 35 - 100 (Percentage)")
            return
        }
        // Normalize: if user entered percentage >= 35, scale to 10-point GPA for standard calculation
        val normalizedGpa = if (gpaNum >= 35.0) (gpaNum / 10.0).coerceIn(0.0, 10.0) else gpaNum

        val scoreTrimmed = current.testScore.trim()
        if (scoreTrimmed.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Please enter your standardized test score or percentile")
            return
        }
        val scoreNum = scoreTrimmed.toDoubleOrNull()
        if (scoreNum == null) {
            _uiState.value = current.copy(errorMessage = "Please enter a valid numeric test score")
            return
        }

        // Validate score ranges according to exam type
        when (current.testType) {
            "JEE_MAIN" -> {
                if (scoreNum !in 0.0..100.0) {
                    _uiState.value = current.copy(errorMessage = "JEE Main percentile must be between 0.0 and 100.0")
                    return
                }
            }
            "JEE_ADVANCED" -> {
                if (scoreNum !in 0.0..360.0) {
                    _uiState.value = current.copy(errorMessage = "JEE Advanced marks must be between 0 and 360")
                    return
                }
            }
            "SAT" -> {
                if (scoreNum !in 400.0..1600.0) {
                    _uiState.value = current.copy(errorMessage = "SAT total score must be between 400 and 1600")
                    return
                }
            }
            "ACT" -> {
                if (scoreNum !in 1.0..36.0) {
                    _uiState.value = current.copy(errorMessage = "ACT composite score must be between 1 and 36")
                    return
                }
            }
            "BITSAT" -> {
                if (scoreNum !in 0.0..390.0) {
                    _uiState.value = current.copy(errorMessage = "BITSAT score must be between 0 and 390")
                    return
                }
            }
            "GRE" -> {
                if (scoreNum !in 260.0..340.0) {
                    _uiState.value = current.copy(errorMessage = "GRE score must be between 260 and 340")
                    return
                }
            }
            "NEET" -> {
                if (scoreNum !in 0.0..720.0) {
                    _uiState.value = current.copy(errorMessage = "NEET score must be between 0 and 720")
                    return
                }
            }
            else -> {
                if (scoreNum < 0.0) {
                    _uiState.value = current.copy(errorMessage = "Exam score cannot be negative")
                    return
                }
            }
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isSubmitting = true, errorMessage = null)
            val request = PredictionRequest(
                institutionId = current.selectedCollege?.id,
                institutionName = current.selectedCollege?.name ?: "Target Institution",
                programName = current.programName,
                degree = current.degree,
                gpa = normalizedGpa,
                testType = current.testType,
                testScore = scoreNum
            )

            repository.predictAdmission(request)
                .onSuccess { result ->
                    val updatedHistory = listOf(result) + predictionHistory
                    _uiState.value = AdmissionPredictorUiState.ResultSuccess(
                        prediction = result,
                        lastForm = current.copy(isSubmitting = false),
                        history = updatedHistory
                    )
                }
                .onFailure { err ->
                    _uiState.value = current.copy(
                        isSubmitting = false,
                        errorMessage = err.message ?: "Failed to calculate prediction"
                    )
                }
        }
    }

    fun editLastInputs() {
        val current = _uiState.value
        if (current is AdmissionPredictorUiState.ResultSuccess && current.lastForm != null) {
            _uiState.value = current.lastForm.copy(errorMessage = null)
        } else {
            resetForm()
        }
    }

    fun resetForm() {
        val firstCollege = availableColleges.firstOrNull()
        _uiState.value = AdmissionPredictorUiState.Form(
            colleges = availableColleges,
            selectedCollege = firstCollege,
            programName = firstCollege?.programs?.firstOrNull()?.name ?: "",
            degree = firstCollege?.programs?.firstOrNull()?.degree ?: "B.TECH",
            gpa = "",
            testType = "JEE_MAIN",
            testScore = "",
            errorMessage = null
        )
    }
}
