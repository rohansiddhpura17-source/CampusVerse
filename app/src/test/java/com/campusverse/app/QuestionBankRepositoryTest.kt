package com.campusverse.app

import com.campusverse.app.data.repository.LocalQuestionBankRepository
import com.campusverse.app.domain.student.questionbank.QuestionBankRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBankRepositoryTest {
    @Test
    fun localDemoRepositoryReturnsAnsweredQuestionsWithUniqueIds() = runTest {
        val repository: QuestionBankRepository = LocalQuestionBankRepository()
        val result = repository.getQuestions()

        assertTrue(result.isSuccess)
        val questions = result.getOrThrow()
        assertFalse(questions.isEmpty())
        assertTrue(questions.all { it.id.isNotBlank() && it.questionText.isNotBlank() && it.answerText.isNotBlank() })
        assertEquals(questions.size, questions.map { it.id }.distinct().size)
    }
}
