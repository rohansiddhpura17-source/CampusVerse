package com.campusverse.app.ui.screens.student.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val sender: String, // "USER" or "AI"
    val text: String,
    val isErrorOrUnavailable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiStudyAssistantUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = "AI",
            text = "Hello! I am your CampusVerse AI Study Assistant. Ask me anything about Data Structures, Algorithms, Operating Systems, Database Systems, Computer Networks, or request a summary of complex topics."
        )
    ),
    val isLoading: Boolean = false,
    val selectedMode: String = "EXPLAIN",
    val suggestedTopics: List<String> = listOf(
        "Dijkstra Shortest Path",
        "Database Normalization (1NF to BCNF)",
        "Process Scheduling Algorithms",
        "TCP 3-Way Handshake"
    )
)

class AiStudyAssistantViewModel(
    private val repository: StudentRepository = NetworkStudentRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiStudyAssistantUiState())
    val uiState: StateFlow<AiStudyAssistantUiState> = _uiState.asStateFlow()

    fun setMode(mode: String) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun submitQuery(query: String) {
        if (query.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(sender = "USER", text = query.trim())
        val updatedMessages = _uiState.value.messages + userMessage

        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                val result = repository.askAiStudyAssistant(
                    query = query.trim(),
                    mode = _uiState.value.selectedMode
                )
                val response: AiStudyQueryResponse? = result.getOrNull()

                val aiMessage = if (response != null && response.available && !response.response.isNullOrBlank()) {
                    ChatMessage(sender = "AI", text = response.response)
                } else if (response != null && !response.available) {
                    ChatMessage(
                        sender = "AI",
                        text = response.message ?: "AI Study Assistant is currently unavailable on this backend server. (No API key configured)",
                        isErrorOrUnavailable = true
                    )
                } else {
                    ChatMessage(
                        sender = "AI",
                        text = "Unable to connect to the AI Study Assistant backend service. Please check network connectivity.",
                        isErrorOrUnavailable = true
                    )
                }

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isLoading = false,
                    suggestedTopics = response?.suggestedTopics?.ifEmpty { _uiState.value.suggestedTopics } ?: _uiState.value.suggestedTopics
                )
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    sender = "AI",
                    text = "An error occurred: ${e.message}",
                    isErrorOrUnavailable = true
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMsg,
                    isLoading = false
                )
            }
        }
    }

    fun restartChat() {
        _uiState.value = AiStudyAssistantUiState()
    }
}

