package com.campusverse.app.domain.student.questionbank

interface QuestionBankRepository {
    suspend fun getQuestions(): Result<List<QuestionBankQuestion>>
}
