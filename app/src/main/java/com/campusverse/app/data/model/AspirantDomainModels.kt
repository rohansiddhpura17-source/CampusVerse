package com.campusverse.app.data.model

import java.io.Serializable

/**
 * Domain models for the Aspirant (Prospective Student) module of CampusVerse.
 */

data class CollegeProgramItem(
    val id: String,
    val name: String,
    val degree: String = "B.TECH",
    val major: String,
    val durationYears: Double = 4.0,
    val tuitionFee: String,
    val minGpa: Double? = null,
    val entranceExams: List<String> = emptyList(),
    val deadline: String? = null,
    val overview: String? = null
) : Serializable

data class CollegeItem(
    val id: String,
    val name: String,
    val code: String,
    val city: String? = null,
    val state: String? = null,
    val country: String = "India",
    val ranking: Int = 99,
    val acceptanceRate: Double = 10.0,
    val averageFees: String = "Contact Institution",
    val overview: String? = null,
    val campusSize: String? = null,
    val websiteUrl: String? = null,
    val logoUrl: String? = null,
    val programsCount: Int = 0,
    val programs: List<CollegeProgramItem> = emptyList(),
    val isSaved: Boolean = false
) : Serializable

data class CollegeComparisonItem(
    val id: String,
    val name: String,
    val country: String,
    val city: String? = null,
    val state: String? = null,
    val ranking: Int = 99,
    val acceptanceRate: Double = 10.0,
    val averageFees: String = "N/A",
    val campusSize: String = "N/A",
    val websiteUrl: String = "N/A",
    val programs: List<CollegeProgramItem> = emptyList()
) : Serializable

data class ScholarshipItem(
    val id: String,
    val name: String,
    val provider: String,
    val amount: String,
    val deadline: String,
    val eligibility: String,
    val description: String,
    val requirements: List<String> = emptyList(),
    val applicationUrl: String? = null,
    val category: String = "MERIT", // MERIT, NEED_BASED, WOMEN_IN_TECH, INTERNATIONAL
    val country: String = "India",
    val isSaved: Boolean = false
) : Serializable

data class AdmissionPredictionItem(
    val id: String = "",
    val institutionId: String? = null,
    val institutionName: String,
    val programName: String,
    val degree: String = "B.TECH",
    val gpa: Double,
    val testType: String, // JEE_MAIN, JEE_ADVANCED, SAT, ACT, GRE, NEET, BITSAT, IELTS
    val testScore: Double,
    val predictionPercentage: Double,
    val qualificationStatus: String = "COMPETITIVE", // STRONG_CANDIDATE, COMPETITIVE, REACH, UNLIKELY
    val feedback: String,
    val recommendations: List<String> = emptyList(),
    val createdAt: String? = null
) : Serializable

data class PredictionRequest(
    val institutionId: String? = null,
    val institutionName: String? = null,
    val programName: String,
    val degree: String = "B.TECH",
    val gpa: Double,
    val testType: String,
    val testScore: Double
) : Serializable

data class AspirantProfileData(
    val userId: String,
    val email: String,
    val fullName: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val targetDegree: String = "B.Tech",
    val targetMajor: String = "Computer Science",
    val targetUniversities: String = "IIT Bombay, NIT Bangalore, BITS Pilani",
    val highSchool: String = "Delhi Public School",
    val expectedGradYear: Int = 2027,
    val entranceExamScores: Map<String, Double> = emptyMap()
) : Serializable

data class AspirantHomeSummary(
    val profile: AspirantProfileData,
    val savedCollegesCount: Int = 0,
    val savedScholarshipsCount: Int = 0,
    val recentPredictionsCount: Int = 0,
    val recommendedColleges: List<CollegeItem> = emptyList(),
    val recentPredictions: List<AdmissionPredictionItem> = emptyList(),
    val notifications: List<AlumniNotificationItem> = emptyList()
) : Serializable
