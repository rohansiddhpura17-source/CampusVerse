package com.campusverse.app.data.model

import org.json.JSONArray
import org.json.JSONObject

data class StudentAcademicPreferences(
    val degree: String = "B.Tech",
    val semester: Int = 6,
    val branch: String = "Computer Science and Engineering",
    val academicGoal: String = "Placement Prep & Top Tech Jobs",
    val focusAreas: List<String> = listOf("Algorithms & Data Structures", "System Design & Distributed Systems"),
    val difficultyLevel: String = "Intermediate",
    val recentGpa: Double? = 8.75
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("degree", degree)
        json.put("semester", semester)
        json.put("branch", branch)
        json.put("academicGoal", academicGoal)
        json.put("focusAreas", JSONArray(focusAreas))
        json.put("difficultyLevel", difficultyLevel)
        if (recentGpa != null) {
            json.put("recentGpa", recentGpa)
        }
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): StudentAcademicPreferences {
            return try {
                val json = JSONObject(jsonStr)
                val focusArr = json.optJSONArray("focusAreas")
                val focusList = mutableListOf<String>()
                if (focusArr != null) {
                    for (i in 0 until focusArr.length()) {
                        focusList.add(focusArr.getString(i))
                    }
                }
                StudentAcademicPreferences(
                    degree = json.optString("degree", "B.Tech"),
                    semester = json.optInt("semester", 6),
                    branch = json.optString("branch", "Computer Science and Engineering"),
                    academicGoal = json.optString("academicGoal", "Placement Prep & Top Tech Jobs"),
                    focusAreas = if (focusList.isNotEmpty()) focusList else listOf("Algorithms & Data Structures"),
                    difficultyLevel = json.optString("difficultyLevel", "Intermediate"),
                    recentGpa = if (json.has("recentGpa") && !json.isNull("recentGpa")) json.optDouble("recentGpa") else null
                )
            } catch (e: Exception) {
                StudentAcademicPreferences()
            }
        }
    }
}
