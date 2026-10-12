package com.campusverse.app.data.repository

import com.campusverse.app.domain.student.questionbank.QuestionBankQuestion
import com.campusverse.app.domain.student.questionbank.QuestionBankRepository
import com.campusverse.app.domain.student.questionbank.QuestionDifficulty

/**
 * Local read-only demonstration data. This is not synced with a live course API.
 * Replace with an API-backed implementation when a real endpoint is available.
 */
class LocalQuestionBankRepository : QuestionBankRepository {
    override suspend fun getQuestions(): Result<List<QuestionBankQuestion>> =
        runCatching { demoQuestions }

    private val demoQuestions = listOf(
        QuestionBankQuestion(
            id = "dsa-001",
            questionText = "What is the time complexity of binary search on a sorted array?",
            answerText = "O(log n), where n is the number of elements.",
            subject = "Computer Science", course = "Data Structures and Algorithms",
            unit = "Unit 2", topic = "Searching", difficulty = QuestionDifficulty.EASY,
            explanation = "Each comparison removes roughly half of the remaining search range.",
            tags = listOf("complexity", "searching", "arrays")
        ),
        QuestionBankQuestion(
            id = "dsa-002",
            questionText = "What principle does a stack follow?",
            answerText = "A stack follows Last In, First Out (LIFO): the most recently added item is removed first.",
            subject = "Computer Science", course = "Data Structures and Algorithms",
            unit = "Unit 1", topic = "Stacks and Queues", difficulty = QuestionDifficulty.EASY,
            explanation = "Push adds an item to the top; pop removes the top item."
        ),
        QuestionBankQuestion(
            id = "dbms-001",
            questionText = "What is a primary key in a relational database?",
            answerText = "A primary key is a column or group of columns that uniquely identifies each row. Its values must be unique and cannot be NULL.",
            subject = "Computer Science", course = "Database Management Systems",
            unit = "Unit 2", topic = "Keys and Constraints", difficulty = QuestionDifficulty.EASY
        ),
        QuestionBankQuestion(
            id = "dbms-002",
            questionText = "What is the purpose of database normalization?",
            answerText = "Normalization structures tables to reduce redundancy and help prevent insertion, update, and deletion anomalies.",
            subject = "Computer Science", course = "Database Management Systems",
            unit = "Unit 3", topic = "Normalization", difficulty = QuestionDifficulty.MEDIUM,
            explanation = "Normal forms provide progressively stronger rules based on relationships between attributes.",
            tags = listOf("1NF", "2NF", "3NF")
        ),
        QuestionBankQuestion(
            id = "os-001",
            questionText = "What is the difference between a process and a thread?",
            answerText = "A process is an executing program with its own resources and address space. Threads are execution paths within a process and generally share its memory.",
            subject = "Computer Science", course = "Operating Systems",
            unit = "Unit 2", topic = "Processes and Threads", difficulty = QuestionDifficulty.MEDIUM,
            explanation = "Threads can share data efficiently, but concurrent access requires synchronization."
        ),
        QuestionBankQuestion(
            id = "os-002",
            questionText = "What four conditions are classically required for deadlock?",
            answerText = "Mutual exclusion, hold and wait, no preemption, and circular wait.",
            subject = "Computer Science", course = "Operating Systems",
            unit = "Unit 3", topic = "Deadlocks", difficulty = QuestionDifficulty.MEDIUM,
            explanation = "These are the Coffman conditions. Preventing at least one condition can prevent deadlock."
        ),
        QuestionBankQuestion(
            id = "java-001",
            questionText = "What is encapsulation in object-oriented programming?",
            answerText = "Encapsulation bundles data and behavior in a class and controls access to internal state through a defined interface.",
            subject = "Programming", course = "Java Programming",
            unit = "Unit 1", topic = "Object-Oriented Programming", difficulty = QuestionDifficulty.EASY,
            codeExample = "class Account {\\n    private int balance;\\n    public int getBalance() { return balance; }\\n}"
        ),
        QuestionBankQuestion(
            id = "java-002",
            questionText = "What is method overloading in Java?",
            answerText = "Method overloading defines methods with the same name but different parameter lists. Return type alone cannot distinguish overloaded methods.",
            subject = "Programming", course = "Java Programming",
            unit = "Unit 2", topic = "Methods", difficulty = QuestionDifficulty.MEDIUM
        ),
        QuestionBankQuestion(
            id = "ml-001",
            questionText = "What is supervised learning?",
            answerText = "Supervised learning trains a model from examples containing inputs and known target labels or values.",
            subject = "Artificial Intelligence", course = "Machine Learning",
            unit = "Unit 1", topic = "Learning Paradigms", difficulty = QuestionDifficulty.EASY,
            explanation = "Classification predicts categories; regression predicts numerical values."
        ),
        QuestionBankQuestion(
            id = "ml-002",
            questionText = "What is overfitting in machine learning?",
            answerText = "Overfitting occurs when a model learns training data too closely, including noise, and performs poorly on unseen data.",
            subject = "Artificial Intelligence", course = "Machine Learning",
            unit = "Unit 3", topic = "Model Evaluation", difficulty = QuestionDifficulty.MEDIUM,
            explanation = "Validation data and regularization can help detect and reduce overfitting."
        )
    )
}
