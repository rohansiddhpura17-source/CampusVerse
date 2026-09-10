package com.campusverse.app

import com.campusverse.app.data.model.StudentNotesPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentNotesPreferencesTest {

    @Test
    fun testSerialization_defaultValues() {
        val prefs = StudentNotesPreferences()
        val json = prefs.toJson()
        val restored = StudentNotesPreferences.fromJson(json)

        assertEquals("Computer Science and Engineering", restored.studyingField)
        assertEquals("2-4 hours/day", restored.dailyStudyTime)
        assertTrue(restored.prioritySubjects.contains("Distributed Systems"))
        assertTrue(restored.preferredResourceTypes.contains("Notes"))
    }

    @Test
    fun testSerialization_customValues() {
        val prefs = StudentNotesPreferences(
            studyingField = "Artificial Intelligence & ML",
            prioritySubjects = listOf("Deep Learning", "Natural Language Processing"),
            preferredResourceTypes = listOf("Flashcards & Summaries", "Practice Questions"),
            dailyStudyTime = "4+ hours/day"
        )
        val json = prefs.toJson()
        val restored = StudentNotesPreferences.fromJson(json)

        assertEquals("Artificial Intelligence & ML", restored.studyingField)
        assertEquals("4+ hours/day", restored.dailyStudyTime)
        assertEquals(2, restored.prioritySubjects.size)
        assertTrue(restored.prioritySubjects.contains("Deep Learning"))
        assertEquals(2, restored.preferredResourceTypes.size)
        assertTrue(restored.preferredResourceTypes.contains("Flashcards & Summaries"))
    }

    @Test
    fun testFromJson_invalidString_recoversGracefully() {
        val restored = StudentNotesPreferences.fromJson("invalid_json_data")
        assertNotNull(restored)
        assertEquals("Computer Science and Engineering", restored.studyingField)
    }
}
