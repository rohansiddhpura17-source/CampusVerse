package com.campusverse.app

import com.campusverse.app.data.model.StudentAcademicPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentAcademicPreferencesTest {

    @Test
    fun testSerialization_defaultValues() {
        val prefs = StudentAcademicPreferences()
        val json = prefs.toJson()
        val restored = StudentAcademicPreferences.fromJson(json)

        assertEquals("B.Tech", restored.degree)
        assertEquals(6, restored.semester)
        assertEquals("Computer Science and Engineering", restored.branch)
        assertEquals("Placement Prep & Top Tech Jobs", restored.academicGoal)
        assertEquals("Intermediate", restored.difficultyLevel)
        assertEquals(8.75, restored.recentGpa ?: 0.0, 0.01)
        assertTrue(restored.focusAreas.contains("Algorithms & Data Structures"))
    }

    @Test
    fun testSerialization_customValues() {
        val prefs = StudentAcademicPreferences(
            degree = "M.Tech",
            semester = 2,
            branch = "Artificial Intelligence & ML",
            academicGoal = "Research & Higher Studies (MS/PhD)",
            focusAreas = listOf("AI & Machine Learning", "Cloud Computing & DevOps"),
            difficultyLevel = "Advanced",
            recentGpa = 9.45
        )
        val json = prefs.toJson()
        val restored = StudentAcademicPreferences.fromJson(json)

        assertEquals("M.Tech", restored.degree)
        assertEquals(2, restored.semester)
        assertEquals("Artificial Intelligence & ML", restored.branch)
        assertEquals("Research & Higher Studies (MS/PhD)", restored.academicGoal)
        assertEquals("Advanced", restored.difficultyLevel)
        assertEquals(9.45, restored.recentGpa ?: 0.0, 0.01)
        assertEquals(2, restored.focusAreas.size)
        assertTrue(restored.focusAreas.contains("AI & Machine Learning"))
    }

    @Test
    fun testFromJson_invalidString_recoversGracefully() {
        val restored = StudentAcademicPreferences.fromJson("invalid_json_string")
        assertNotNull(restored)
        assertEquals("B.Tech", restored.degree)
    }
}
