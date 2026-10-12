package com.campusverse.app.domain.student.questionbank

data class QuestionBankQuestion(
    val id: String,
    val questionText: String,
    val answerText: String,
    val subject: String,
    val course: String,
    val unit: String,
    val topic: String,
    val difficulty: QuestionDifficulty,
    val explanation: String? = null,
    val codeExample: String? = null,
    val tags: List<String> = emptyList(),
    val sourceReference: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

enum class QuestionDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard")
}

data class QuestionBankFilters(
    val subject: String? = null,
    val course: String? = null,
    val unit: String? = null,
    val topic: String? = null,
    val difficulty: String? = null
)

/** Case-insensitive search across question text, answer and available metadata. */
fun filterQuestionBankQuestions(
    questions: List<QuestionBankQuestion>,
    query: String,
    filters: QuestionBankFilters = QuestionBankFilters()
): List<QuestionBankQuestion> {
    val term = query.trim()
    return questions.filter { question ->
        val searchableValues = listOf(
            question.questionText, question.answerText, question.subject, question.course,
            question.unit, question.topic, question.difficulty.label,
            question.explanation.orEmpty(), question.codeExample.orEmpty(),
            question.tags.joinToString(" "), question.sourceReference.orEmpty()
        )
        (term.isEmpty() || searchableValues.any { it.contains(term, ignoreCase = true) }) &&
            (filters.subject == null || question.subject == filters.subject) &&
            (filters.course == null || question.course == filters.course) &&
            (filters.unit == null || question.unit == filters.unit) &&
            (filters.topic == null || question.topic == filters.topic) &&
            (filters.difficulty == null || question.difficulty.label == filters.difficulty)
    }
}
