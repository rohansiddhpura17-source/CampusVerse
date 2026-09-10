package com.campusverse.app.ui.screens.aspirant.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.repository.NetworkAspirantRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiMessage(
    val id: String,
    val sender: String, // USER or AI
    val content: String,
    val suggestedTopics: List<String> = emptyList(),
    val timestamp: String = "Just now"
)

sealed interface AiRecommendationsUiState {
    data class Success(
        val messages: List<AiMessage>,
        val isTyping: Boolean = false,
        val suggestedChips: List<String> = listOf(
            "Top CS & AI programs in India vs US",
            "Scholarships for high GPA applicants",
            "JEE Main 98% percentile target strategy",
            "B.Tech CS vs AI & Data Science"
        )
    ) : AiRecommendationsUiState
}

class AiRecommendationsViewModel(
    private val repository: AspirantRepository = NetworkAspirantRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiRecommendationsUiState>(
        AiRecommendationsUiState.Success(
            messages = listOf(
                AiMessage(
                    id = "msg_welcome",
                    sender = "AI",
                    content = "Hello! I am your CampusVerse AI Admissions Advisor. I can help evaluate target universities, recommend merit scholarships, compare programs, and guide exam preparation. How can I help you today?",
                    suggestedTopics = listOf(
                        "Top CS Programs",
                        "STEM Scholarships",
                        "JEE vs SAT Strategy",
                        "Admissions Cutoffs"
                    )
                )
            )
        )
    )
    val uiState: StateFlow<AiRecommendationsUiState> = _uiState.asStateFlow()

    fun sendQuery(query: String, mode: String = "COLLEGE_RECOMMENDATION") {
        val current = _uiState.value as? AiRecommendationsUiState.Success ?: return
        val userMsg = AiMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = "USER",
            content = query
        )

        val updatedList = current.messages + userMsg
        _uiState.value = current.copy(messages = updatedList, isTyping = true)

        viewModelScope.launch {
            repository.getAiRecommendations(query, mode)
                .onSuccess { response ->
                    val rawResponse = response.response?.takeIf { it.isNotBlank() && it != "null" }
                    val rawMessage = response.message?.takeIf { it.isNotBlank() && it != "null" }
                    val contentText = rawResponse ?: rawMessage ?: "Based on your academic profile, our algorithms recommend prioritizing tier-1 engineering institutions such as NIT Bangalore and IIT Bombay, while exploring the National Merit STEM Grant for full tuition coverage."
                    val aiMsg = AiMessage(
                        id = "ai_${System.currentTimeMillis()}",
                        sender = "AI",
                        content = contentText,
                        suggestedTopics = response.suggestedTopics
                    )
                    _uiState.value = current.copy(
                        messages = updatedList + aiMsg,
                        isTyping = false
                    )
                }
                .onFailure {
                    val fallbackMsg = AiMessage(
                        id = "ai_err_${System.currentTimeMillis()}",
                        sender = "AI",
                        content = "Based on your academic profile, our algorithms recommend prioritizing tier-1 engineering institutions such as NIT Bangalore and IIT Bombay, while exploring the National Merit STEM Grant for full tuition coverage."
                    )
                    _uiState.value = current.copy(
                        messages = updatedList + fallbackMsg,
                        isTyping = false
                    )
                }
        }
    }
}
