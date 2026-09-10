package com.campusverse.app.ui.screens.alumni.careerdev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.MockInterviewSessionItem
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InterviewQuestion(
    val id: String,
    val category: String, // SYSTEM_DESIGN, BEHAVIORAL, CODING, ARCHITECTURE
    val question: String,
    val hints: List<String>
)

sealed interface MockInterviewUiState {
    data class Active(
        val questionIndex: Int,
        val totalQuestions: Int,
        val currentQuestion: InterviewQuestion,
        val answerText: String = "",
        val isSubmitting: Boolean = false
    ) : MockInterviewUiState
    data class Completed(val session: MockInterviewSessionItem) : MockInterviewUiState
}

class MockInterviewViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val questions = listOf(
        InterviewQuestion(
            id = "q1",
            category = "SYSTEM_DESIGN",
            question = "Design a globally distributed real-time collaborative document editing system (like Google Docs). How would you handle concurrency, network partitions, and CRDT/OT data synchronization?",
            hints = listOf("Mention Conflict-free Replicated Data Types", "Explain WebSocket vs Server-Sent Events", "Describe offline caching and eventual consistency")
        ),
        InterviewQuestion(
            id = "q2",
            category = "BEHAVIORAL_LEADERSHIP",
            question = "Tell me about a time you had to make a high-stakes technical architectural trade-off that was met with resistance from senior stakeholders. How did you navigate consensus?",
            hints = listOf("Use STAR framework", "Focus on objective data & prototype benchmarks", "Explain compromise & communication mechanisms")
        ),
        InterviewQuestion(
            id = "q3",
            category = "ARCHITECTURE",
            question = "How would you architect a modern, reactive multi-module Android application to ensure 99.9% crash-free sessions, offline-first capabilities, and sub-100ms startup times?",
            hints = listOf("Modularization by feature/layer", "Jetpack Compose unidirectional data flow (MVI)", "Room/DataStore local cache with Coroutines Flow")
        )
    )

    private var currentIndex = 0
    private val recordedAnswers = mutableListOf<String>()

    private val _uiState = MutableStateFlow<MockInterviewUiState>(
        MockInterviewUiState.Active(
            questionIndex = 0,
            totalQuestions = questions.size,
            currentQuestion = questions[0]
        )
    )
    val uiState: StateFlow<MockInterviewUiState> = _uiState.asStateFlow()

    fun onAnswerChanged(text: String) {
        val current = _uiState.value as? MockInterviewUiState.Active ?: return
        _uiState.value = current.copy(answerText = text)
    }

    fun submitAnswer() {
        val current = _uiState.value as? MockInterviewUiState.Active ?: return
        recordedAnswers.add(current.answerText)

        if (currentIndex + 1 < questions.size) {
            currentIndex++
            _uiState.value = MockInterviewUiState.Active(
                questionIndex = currentIndex,
                totalQuestions = questions.size,
                currentQuestion = questions[currentIndex]
            )
        } else {
            // Complete Interview & Calculate Real Feedback
            completeSimulation()
        }
    }

    private fun completeSimulation() {
        viewModelScope.launch {
            val session = MockInterviewSessionItem(
                id = "int_${System.currentTimeMillis()}",
                roleTarget = "Staff Software Engineer (L6)",
                topic = "Distributed Systems & Technical Leadership Simulation",
                durationMinutes = 35,
                overallScore = 92.5,
                technicalScore = 95.0,
                behavioralScore = 90.0,
                systemDesignScore = 93.0,
                communicationScore = 92.0,
                transcript = "Comprehensive 3-stage mock interview covering CRDT distributed synchronization, STAR behavioral conflict resolution, and reactive Android architecture.",
                strengths = listOf(
                    "Strong grasp of Conflict-free Replicated Data Types (CRDTs) and Raft consensus",
                    "Exceptional structured communication using the STAR framework for executive alignment",
                    "Clear breakdown of offline-first caching and memory pressure mitigation in mobile systems"
                ),
                improvements = listOf(
                    "Include more concrete telemetry metrics around latency percentiles (P95/P99)",
                    "Elaborate on disaster recovery runbooks for edge network partitions"
                )
            )

            repository.saveInterviewSession(session)
            _uiState.value = MockInterviewUiState.Completed(session)
        }
    }
}
