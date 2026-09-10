package com.campusverse.app.ui.screens.alumni.careerdev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CareerAssistantMessage(
    val id: String,
    val sender: String, // USER or AI
    val content: String,
    val timestamp: String = "Just now"
)

sealed interface AiCareerAssistantUiState {
    data object Idle : AiCareerAssistantUiState
    data object Loading : AiCareerAssistantUiState
    data class Success(
        val messages: List<CareerAssistantMessage>,
        val currentMode: String = "CAREER_GUIDANCE",
        val suggestedTopics: List<String> = emptyList()
    ) : AiCareerAssistantUiState
}

class AiCareerAssistantViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _messages = mutableListOf<CareerAssistantMessage>()
    private val _uiState = MutableStateFlow<AiCareerAssistantUiState>(
        AiCareerAssistantUiState.Success(
            messages = listOf(
                CareerAssistantMessage(
                    id = "msg_welcome",
                    sender = "AI",
                    content = "Hello! I am your CampusVerse Career Coach. I can help with L6/L7 system design strategies, resume reviews, behavioral STAR questions, and career roadmap transitions."
                )
            ),
            suggestedTopics = listOf(
                "How to transition from Senior Engineer to Staff Engineer?",
                "System Design: Distributed Real-Time Messaging Architecture",
                "STAR method for Engineering Leadership behavioral questions",
                "High-impact quantifiable resume bullet points"
            )
        )
    )
    val uiState: StateFlow<AiCareerAssistantUiState> = _uiState.asStateFlow()

    private var selectedMode: String = "CAREER_GUIDANCE"

    init {
        _messages.add(
            CareerAssistantMessage(
                id = "msg_welcome",
                sender = "AI",
                content = "Hello! I am your CampusVerse Career Coach. I can help with L6/L7 system design strategies, resume reviews, behavioral STAR questions, and career roadmap transitions."
            )
        )
    }

    fun setMode(mode: String) {
        selectedMode = mode
    }

    fun sendQuery(query: String) {
        if (query.isBlank()) return

        val userMsg = CareerAssistantMessage("usr_${System.currentTimeMillis()}", "USER", query)
        _messages.add(userMsg)
        _uiState.value = AiCareerAssistantUiState.Success(_messages.toList(), selectedMode)

        viewModelScope.launch {
            repository.askAiCareerAssistant(query, selectedMode)
                .onSuccess { response ->
                    val aiText = response.response ?: response.message ?: "Guidance generated."
                    _messages.add(CareerAssistantMessage("ai_${System.currentTimeMillis()}", "AI", aiText))
                    _uiState.value = AiCareerAssistantUiState.Success(
                        messages = _messages.toList(),
                        currentMode = selectedMode,
                        suggestedTopics = response.suggestedTopics
                    )
                }
                .onFailure {
                    _messages.add(CareerAssistantMessage("ai_${System.currentTimeMillis()}", "AI", "Sorry, I encountered an issue reaching the career advisor. Please try again."))
                    _uiState.value = AiCareerAssistantUiState.Success(_messages.toList(), selectedMode)
                }
        }
    }
}
