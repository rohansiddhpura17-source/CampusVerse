package com.campusverse.app

import com.campusverse.app.domain.student.questionbank.QuestionBankFilters
import com.campusverse.app.domain.student.questionbank.QuestionBankQuestion
import com.campusverse.app.domain.student.questionbank.QuestionDifficulty
import com.campusverse.app.domain.student.questionbank.filterQuestionBankQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBankFilteringTest {
    private val questions = listOf(
        QuestionBankQuestion(
            id = "q1", questionText = "Explain binary search",
            answerText = "It repeatedly halves the search range.",
            subject = "Computer Science", course = "Data Structures and Algorithms",
            unit = "Unit 2", topic = "Searching", difficulty = QuestionDifficulty.EASY,
            tags = listOf("arrays")
        ),
        QuestionBankQuestion(
            id = "q2", questionText = "What is a primary key?",
            answerText = "A unique, non-null identifier for a row.",
            subject = "Computer Science", course = "Database Management Systems",
            unit = "Unit 2", topic = "Keys and Constraints", difficulty = QuestionDifficulty.EASY
        ),
        QuestionBankQuestion(
            id = "q3", questionText = "Explain overfitting",
            answerText = "A model learns training noise and generalizes poorly.",
            subject = "Artificial Intelligence", course = "Machine Learning",
            unit = "Unit 3", topic = "Model Evaluation", difficulty = QuestionDifficulty.MEDIUM
        )
    )

    @Test
    fun searchFindsQuestionAnswerMetadataAndTags() {
        assertEquals("q1", filterQuestionBankQuestions(questions, "BINARY").single().id)
        assertEquals("q2", filterQuestionBankQuestions(questions, "non-null").single().id)
        assertEquals("q3", filterQuestionBankQuestions(questions, "artificial intelligence").single().id)
        assertEquals("q1", filterQuestionBankQuestions(questions, "arrays").single().id)
    }

    @Test
    fun filtersAreCombined() {
        val result = filterQuestionBankQuestions(
            questions, "",
            QuestionBankFilters(
                subject = "Computer Science", course = "Data Structures and Algorithms",
                unit = "Unit 2", topic = "Searching", difficulty = "Easy"
            )
        )
        assertEquals(listOf("q1"), result.map { it.id })
    }

    @Test
    fun noMatchesReturnEmptyAndNullOptionalFieldsAreSafe() {
        assertTrue(filterQuestionBankQuestions(questions, "made-up term").isEmpty())
        assertEquals("q3", filterQuestionBankQuestions(questions, "model evaluation").single().id)
        assertEquals(3, filterQuestionBankQuestions(questions, "   ").size)
    }
}
