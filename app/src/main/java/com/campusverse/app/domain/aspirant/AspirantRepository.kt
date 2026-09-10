package com.campusverse.app.domain.aspirant

import com.campusverse.app.data.model.AdmissionPredictionItem
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.model.AspirantHomeSummary
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.data.model.CollegeComparisonItem
import com.campusverse.app.data.model.CollegeItem
import com.campusverse.app.data.model.PredictionRequest
import com.campusverse.app.data.model.ScholarshipItem

/**
 * Repository interface defining operations for the Aspirant (Prospective Student) module.
 */
interface AspirantRepository {

    // 1. Aspirant Home Dashboard
    suspend fun getAspirantHomeSummary(): Result<AspirantHomeSummary>

    // 2. College Explorer & Details
    suspend fun getColleges(
        search: String? = null,
        country: String? = null,
        degree: String? = null,
        sortBy: String? = null
    ): Result<List<CollegeItem>>

    suspend fun getCollegeById(id: String): Result<CollegeItem>
    suspend fun saveCollege(collegeId: String): Result<Boolean>
    suspend fun unsaveCollege(collegeId: String): Result<Boolean>
    suspend fun getSavedColleges(): Result<List<CollegeItem>>

    // 3. College Comparison
    suspend fun compareColleges(collegeIds: List<String>): Result<List<CollegeComparisonItem>>

    // 4. Admission Predictor
    suspend fun predictAdmission(request: PredictionRequest): Result<AdmissionPredictionItem>
    suspend fun getPredictionHistory(): Result<List<AdmissionPredictionItem>>

    // 5. Scholarships
    suspend fun getScholarships(
        search: String? = null,
        category: String? = null,
        country: String? = null
    ): Result<List<ScholarshipItem>>

    suspend fun getScholarshipById(id: String): Result<ScholarshipItem>
    suspend fun saveScholarship(scholarshipId: String): Result<Boolean>
    suspend fun unsaveScholarship(scholarshipId: String): Result<Boolean>
    suspend fun getSavedScholarships(): Result<List<ScholarshipItem>>

    // 6. AI Recommendations
    suspend fun getAiRecommendations(query: String, mode: String): Result<AiStudyQueryResponse>

    // 7. Aspirant Profile
    suspend fun getAspirantProfile(): Result<AspirantProfileData>
    suspend fun updateAspirantProfile(profile: AspirantProfileData): Result<AspirantProfileData>
}
