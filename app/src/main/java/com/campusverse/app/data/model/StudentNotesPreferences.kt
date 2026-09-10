package com.campusverse.app.data.model

import org.json.JSONArray
import org.json.JSONObject

data class StudentNotesPreferences(
    val studyingField: String = "Computer Science and Engineering",
    val prioritySubjects: List<String> = listOf("Distributed Systems", "Algorithms & DSA"),
    val preferredResourceTypes: List<String> = listOf("Notes", "Practice Questions", "Flashcards"),
    val dailyStudyTime: String = "2-4 hours/day"
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("studyingField", studyingField)
        json.put("prioritySubjects", JSONArray(prioritySubjects))
        json.put("preferredResourceTypes", JSONArray(preferredResourceTypes))
        json.put("dailyStudyTime", dailyStudyTime)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): StudentNotesPreferences {
            return try {
                val json = JSONObject(jsonStr)
                val subjArr = json.optJSONArray("prioritySubjects")
                val subjList = mutableListOf<String>()
                if (subjArr != null) {
                    for (i in 0 until subjArr.length()) {
                        subjList.add(subjArr.getString(i))
                    }
                }

                val typeArr = json.optJSONArray("preferredResourceTypes")
                val typeList = mutableListOf<String>()
                if (typeArr != null) {
                    for (i in 0 until typeArr.length()) {
                        typeList.add(typeArr.getString(i))
                    }
                }

                StudentNotesPreferences(
                    studyingField = json.optString("studyingField", "Computer Science and Engineering"),
                    prioritySubjects = if (subjList.isNotEmpty()) subjList else listOf("Distributed Systems", "Algorithms & DSA"),
                    preferredResourceTypes = if (typeList.isNotEmpty()) typeList else listOf("Notes", "Practice Questions"),
                    dailyStudyTime = json.optString("dailyStudyTime", "2-4 hours/day")
                )
            } catch (e: Exception) {
                StudentNotesPreferences()
            }
        }
    }
}
