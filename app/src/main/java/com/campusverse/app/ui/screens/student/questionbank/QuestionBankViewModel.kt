package com.campusverse.app.ui.screens.student.questionbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.repository.LocalQuestionBankRepository
import com.campusverse.app.domain.student.questionbank.QuestionBankFilters
import com.campusverse.app.domain.student.questionbank.QuestionBankQuestion
import com.campusverse.app.domain.student.questionbank.QuestionBankRepository
import com.campusverse.app.domain.student.questionbank.filterQuestionBankQuestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuestionBankUiState(
    val isLoading: Boolean = true,
    val questions: List<QuestionBankQuestion> = emptyList(),
    val searchQuery: String = "",
    val filters: QuestionBankFilters = QuestionBankFilters(),
    val expandedQuestionIds: Set<String> = emptySet(),
    val errorMessage: String? = null
) {
    val visibleQuestions: List<QuestionBankQuestion>
        get() = filterQuestionBankQuestions(questions, searchQuery, filters)
}

class QuestionBankViewModel(
    private val repository: QuestionBankRepository = LocalQuestionBankRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuestionBankUiState())
    val uiState: StateFlow<QuestionBankUiState> = _uiState.asStateFlow()

    init {
        loadQuestions()
    }

    fun loadQuestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getQuestions().fold(
                onSuccess = { questions ->
                    val validIds = questions.map { it.id }.toSet()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            questions = questions,
                            errorMessage = null,
                            expandedQuestionIds = it.expandedQuestionIds.intersect(validIds)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load questions.")
                    }
                }
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSubject(value: String?) = updateFilters { copy(subject = value) }
    fun setCourse(value: String?) = updateFilters { copy(course = value) }
    fun setUnit(value: String?) = updateFilters { copy(unit = value) }
    fun setTopic(value: String?) = updateFilters { copy(topic = value) }
    fun setDifficulty(value: String?) = updateFilters { copy(difficulty = value) }

    fun clearFilters() {
        _uiState.update { it.copy(searchQuery = "", filters = QuestionBankFilters()) }
    }

    fun toggleExpanded(questionId: String) {
        _uiState.update { state ->
            state.copy(
                expandedQuestionIds = if (questionId in state.expandedQuestionIds) {
                    state.expandedQuestionIds - questionId
                } else {
                    state.expandedQuestionIds + questionId
                }
            )
        }
    }

    private fun updateFilters(transform: QuestionBankFilters.() -> QuestionBankFilters) {
        _uiState.update { it.copy(filters = it.filters.transform()) }
    }
}
