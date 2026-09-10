package com.campusverse.app

import com.campusverse.app.data.model.AdmissionPredictionItem
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.model.AspirantHomeSummary
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.data.model.CollegeComparisonItem
import com.campusverse.app.data.model.CollegeItem
import com.campusverse.app.data.model.CollegeProgramItem
import com.campusverse.app.data.model.PredictionRequest
import com.campusverse.app.data.model.ScholarshipItem
import com.campusverse.app.domain.aspirant.AspirantRepository
import com.campusverse.app.ui.screens.aspirant.AspirantHomeUiState
import com.campusverse.app.ui.screens.aspirant.AspirantHomeViewModel
import com.campusverse.app.ui.screens.aspirant.ai.AiRecommendationsUiState
import com.campusverse.app.ui.screens.aspirant.ai.AiRecommendationsViewModel
import com.campusverse.app.ui.screens.aspirant.colleges.CollegeComparisonUiState
import com.campusverse.app.ui.screens.aspirant.colleges.CollegeComparisonViewModel
import com.campusverse.app.ui.screens.aspirant.colleges.CollegeDetailUiState
import com.campusverse.app.ui.screens.aspirant.colleges.CollegeDetailViewModel
import com.campusverse.app.ui.screens.aspirant.colleges.CollegeExplorerUiState
import com.campusverse.app.ui.screens.aspirant.colleges.CollegeExplorerViewModel
import com.campusverse.app.ui.screens.aspirant.predictor.AdmissionPredictorUiState
import com.campusverse.app.ui.screens.aspirant.predictor.AdmissionPredictorViewModel
import com.campusverse.app.ui.screens.aspirant.profile.AspirantProfileUiState
import com.campusverse.app.ui.screens.aspirant.profile.AspirantProfileViewModel
import com.campusverse.app.ui.screens.aspirant.scholarships.ScholarshipDetailUiState
import com.campusverse.app.ui.screens.aspirant.scholarships.ScholarshipDetailViewModel
import com.campusverse.app.ui.screens.aspirant.scholarships.ScholarshipFinderUiState
import com.campusverse.app.ui.screens.aspirant.scholarships.ScholarshipFinderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake implementation of AspirantRepository for deterministic unit testing.
 */
class FakeAspirantRepository : AspirantRepository {

    var shouldReturnError = false

    var profile = AspirantProfileData(
        userId = "usr_aspirant_test",
        email = "aspirant@campusverse.edu",
        fullName = "Rohan Mehta",
        bio = "Prospective CS student.",
        targetDegree = "B.Tech",
        targetMajor = "Computer Science",
        targetUniversities = "NIT Bangalore, IIT Bombay",
        highSchool = "DPS Mumbai",
        expectedGradYear = 2027,
        entranceExamScores = mapOf("JEE_MAIN" to 98.4, "SAT" to 1490.0)
    )

    val colleges = mutableListOf(
        CollegeItem(
            id = "c1",
            name = "National Institute of Technology",
            code = "NIT",
            city = "Bangalore",
            country = "India",
            ranking = 12,
            acceptanceRate = 4.5,
            averageFees = "₹2,20,000 / year",
            programsCount = 1,
            programs = listOf(CollegeProgramItem("p1", "B.Tech CSE", "B.TECH", "Computer Science", 4.0, "₹2,20,000 / year")),
            isSaved = true
        ),
        CollegeItem(
            id = "c2",
            name = "IIT Bombay",
            code = "IITB",
            city = "Mumbai",
            country = "India",
            ranking = 1,
            acceptanceRate = 1.2,
            averageFees = "₹2,50,000 / year",
            programsCount = 1,
            programs = listOf(CollegeProgramItem("p2", "B.Tech CSE", "B.TECH", "Computer Science", 4.0, "₹2,50,000 / year")),
            isSaved = false
        ),
        CollegeItem(
            id = "c3",
            name = "Stanford University",
            code = "STANFORD",
            city = "Stanford",
            country = "United States",
            ranking = 2,
            acceptanceRate = 3.9,
            averageFees = "$62,000 / year",
            programsCount = 1,
            programs = listOf(CollegeProgramItem("p3", "BS Computer Science", "BS", "Computer Science", 4.0, "$62,000 / year")),
            isSaved = false
        )
    )

    val scholarships = mutableListOf(
        ScholarshipItem(
            id = "s1",
            name = "National Merit STEM Grant",
            provider = "Govt",
            amount = "₹2,50,000 / year",
            deadline = "2026-07-31",
            eligibility = "JEE >= 95%",
            description = "Govt grant",
            category = "MERIT",
            country = "India",
            isSaved = true
        ),
        ScholarshipItem(
            id = "s2",
            name = "Google Women Techmakers",
            provider = "Google",
            amount = "$10,000",
            deadline = "2026-05-30",
            eligibility = "Female CS undergrad",
            description = "Google scholarship",
            category = "WOMEN_IN_TECH",
            country = "United States",
            isSaved = false
        )
    )

    val predictions = mutableListOf<AdmissionPredictionItem>()

    override suspend fun getAspirantHomeSummary(): Result<AspirantHomeSummary> {
        if (shouldReturnError) return Result.failure(Exception("Network error"))
        return Result.success(
            AspirantHomeSummary(
                profile = profile,
                savedCollegesCount = colleges.count { it.isSaved },
                savedScholarshipsCount = scholarships.count { it.isSaved },
                recentPredictionsCount = predictions.size,
                recommendedColleges = colleges,
                recentPredictions = predictions
            )
        )
    }

    override suspend fun getColleges(search: String?, country: String?, degree: String?, sortBy: String?): Result<List<CollegeItem>> {
        if (shouldReturnError) return Result.failure(Exception("Network error"))
        var list = colleges.toList()
        if (!search.isNullOrBlank()) list = list.filter { it.name.contains(search, true) }
        if (!country.isNullOrBlank()) list = list.filter { it.country.equals(country, true) }
        return Result.success(list)
    }

    override suspend fun getCollegeById(id: String): Result<CollegeItem> {
        if (shouldReturnError) return Result.failure(Exception("College not found"))
        val item = colleges.find { it.id == id } ?: return Result.failure(Exception("Not found"))
        return Result.success(item)
    }

    override suspend fun saveCollege(collegeId: String): Result<Boolean> {
        val idx = colleges.indexOfFirst { it.id == collegeId }
        if (idx != -1) colleges[idx] = colleges[idx].copy(isSaved = true)
        return Result.success(true)
    }

    override suspend fun unsaveCollege(collegeId: String): Result<Boolean> {
        val idx = colleges.indexOfFirst { it.id == collegeId }
        if (idx != -1) colleges[idx] = colleges[idx].copy(isSaved = false)
        return Result.success(true)
    }

    override suspend fun getSavedColleges(): Result<List<CollegeItem>> {
        return Result.success(colleges.filter { it.isSaved })
    }

    override suspend fun compareColleges(collegeIds: List<String>): Result<List<CollegeComparisonItem>> {
        val list = colleges.filter { collegeIds.contains(it.id) }.map {
            CollegeComparisonItem(
                id = it.id,
                name = it.name,
                country = it.country,
                city = it.city,
                ranking = it.ranking,
                acceptanceRate = it.acceptanceRate,
                averageFees = it.averageFees,
                programs = it.programs
            )
        }
        return Result.success(list)
    }

    override suspend fun predictAdmission(request: PredictionRequest): Result<AdmissionPredictionItem> {
        val percentage = 94.5
        val item = AdmissionPredictionItem(
            id = "pred_test_1",
            institutionName = request.institutionName ?: "National Institute of Technology",
            programName = request.programName,
            degree = request.degree,
            gpa = request.gpa,
            testType = request.testType,
            testScore = request.testScore,
            predictionPercentage = percentage,
            qualificationStatus = "STRONG_CANDIDATE",
            feedback = "Strong applicant profile for target university.",
            recommendations = listOf("Apply in round 1", "Submit grant application")
        )
        predictions.add(0, item)
        return Result.success(item)
    }

    override suspend fun getPredictionHistory(): Result<List<AdmissionPredictionItem>> {
        return Result.success(predictions)
    }

    override suspend fun getScholarships(search: String?, category: String?, country: String?): Result<List<ScholarshipItem>> {
        if (shouldReturnError) return Result.failure(Exception("Network error"))
        var list = scholarships.toList()
        if (!search.isNullOrBlank()) list = list.filter { it.name.contains(search, true) }
        if (!category.isNullOrBlank()) list = list.filter { it.category.equals(category, true) }
        return Result.success(list)
    }

    override suspend fun getScholarshipById(id: String): Result<ScholarshipItem> {
        val item = scholarships.find { it.id == id } ?: return Result.failure(Exception("Not found"))
        return Result.success(item)
    }

    override suspend fun saveScholarship(scholarshipId: String): Result<Boolean> {
        val idx = scholarships.indexOfFirst { it.id == scholarshipId }
        if (idx != -1) scholarships[idx] = scholarships[idx].copy(isSaved = true)
        return Result.success(true)
    }

    override suspend fun unsaveScholarship(scholarshipId: String): Result<Boolean> {
        val idx = scholarships.indexOfFirst { it.id == scholarshipId }
        if (idx != -1) scholarships[idx] = scholarships[idx].copy(isSaved = false)
        return Result.success(true)
    }

    override suspend fun getSavedScholarships(): Result<List<ScholarshipItem>> {
        return Result.success(scholarships.filter { it.isSaved })
    }

    override suspend fun getAiRecommendations(query: String, mode: String): Result<AiStudyQueryResponse> {
        return Result.success(
            AiStudyQueryResponse(
                available = true,
                query = query,
                mode = mode,
                response = "Prioritize NIT Bangalore CSE and IIT Bombay based on your test profile.",
                suggestedTopics = listOf("Counseling Checklist", "Fee Comparison")
            )
        )
    }

    override suspend fun getAspirantProfile(): Result<AspirantProfileData> {
        return Result.success(profile)
    }

    override suspend fun updateAspirantProfile(profile: AspirantProfileData): Result<AspirantProfileData> {
        this.profile = profile
        return Result.success(this.profile)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AspirantModuleTests {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeAspirantRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeAspirantRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. Aspirant Home Dashboard Tests
    // -------------------------------------------------------------------------
    @Test
    fun testAspirantHomeViewModel_LoadsSummarySuccessfully() = runTest {
        val viewModel = AspirantHomeViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AspirantHomeUiState.Success)
        val success = state as AspirantHomeUiState.Success
        assertEquals("Rohan Mehta", success.summary.profile.fullName)
        assertEquals(1, success.summary.savedCollegesCount)
        assertEquals(1, success.summary.savedScholarshipsCount)
        assertEquals(3, success.summary.recommendedColleges.size)
    }

    @Test
    fun testAspirantHomeViewModel_TogglesSaveCollege() = runTest {
        val viewModel = AspirantHomeViewModel(fakeRepo)
        advanceUntilIdle()

        // Toggle unsave for c1
        viewModel.toggleSaveCollege("c1", currentSaved = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as AspirantHomeUiState.Success
        assertEquals(0, state.summary.savedCollegesCount)
    }

    // -------------------------------------------------------------------------
    // 2. College Explorer Tests
    // -------------------------------------------------------------------------
    @Test
    fun testCollegeExplorerViewModel_FiltersAndSearches() = runTest {
        val viewModel = CollegeExplorerViewModel(fakeRepo)
        advanceUntilIdle()

        val state1 = viewModel.uiState.value as CollegeExplorerUiState.Success
        assertEquals(3, state1.colleges.size)

        // Filter by Country (United States)
        viewModel.onCountrySelected("United States")
        advanceUntilIdle()

        val state2 = viewModel.uiState.value as CollegeExplorerUiState.Success
        assertEquals(1, state2.colleges.size)
        assertEquals("Stanford University", state2.colleges.first().name)

        // Search by query "IIT"
        viewModel.onCountrySelected(null)
        viewModel.onSearchQueryChanged("IIT")
        advanceUntilIdle()

        val state3 = viewModel.uiState.value as CollegeExplorerUiState.Success
        assertEquals(1, state3.colleges.size)
        assertEquals("IIT Bombay", state3.colleges.first().name)
    }

    // -------------------------------------------------------------------------
    // 3. College Details Tests
    // -------------------------------------------------------------------------
    @Test
    fun testCollegeDetailViewModel_LoadsCollegeAndTogglesSave() = runTest {
        val viewModel = CollegeDetailViewModel(fakeRepo)
        viewModel.loadCollege("c2")
        advanceUntilIdle()

        val state = viewModel.uiState.value as CollegeDetailUiState.Success
        assertEquals("IIT Bombay", state.college.name)
        assertFalse(state.college.isSaved)

        viewModel.toggleSave()
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as CollegeDetailUiState.Success
        assertTrue(updatedState.college.isSaved)
    }

    // -------------------------------------------------------------------------
    // 4. College Comparison Tests
    // -------------------------------------------------------------------------
    @Test
    fun testCollegeComparisonViewModel_InitializesAndTogglesSelection() = runTest {
        val viewModel = CollegeComparisonViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as CollegeComparisonUiState.Success
        assertEquals(2, state.selectedCollegeIds.size)
        assertEquals(2, state.comparisonResults.size)

        // Add 3rd college
        viewModel.toggleCollegeSelection("c3")
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as CollegeComparisonUiState.Success
        assertEquals(3, updatedState.selectedCollegeIds.size)
        assertEquals(3, updatedState.comparisonResults.size)
    }

    // -------------------------------------------------------------------------
    // 5. Admission Predictor Tests
    // -------------------------------------------------------------------------
    @Test
    fun testAdmissionPredictorViewModel_ValidatesGpaAndCalculatesChances() = runTest {
        val viewModel = AdmissionPredictorViewModel(fakeRepo)
        advanceUntilIdle()

        // 1. Invalid GPA (12.0)
        viewModel.onGpaChanged("12.0")
        viewModel.submitPrediction()
        advanceUntilIdle()

        val formState = viewModel.uiState.value
        assertTrue("Expected Form state with error", formState is AdmissionPredictorUiState.Form)
        val errorState = formState as AdmissionPredictorUiState.Form
        assertNotNull("Expected error message for invalid GPA", errorState.errorMessage)

        // 2. Valid submission
        viewModel.onGpaChanged("9.5")
        viewModel.onTestTypeChanged("JEE_MAIN")
        viewModel.onTestScoreChanged("99.1")
        viewModel.submitPrediction()
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertTrue("Expected ResultSuccess state but got $finalState", finalState is AdmissionPredictorUiState.ResultSuccess)
        val resultState = finalState as AdmissionPredictorUiState.ResultSuccess
        assertEquals("STRONG_CANDIDATE", resultState.prediction.qualificationStatus)
        assertEquals(94.5, resultState.prediction.predictionPercentage, 0.1)
    }

    // -------------------------------------------------------------------------
    // 6. Scholarship Finder Tests
    // -------------------------------------------------------------------------
    @Test
    fun testScholarshipFinderViewModel_FiltersByCategory() = runTest {
        val viewModel = ScholarshipFinderViewModel(fakeRepo)
        advanceUntilIdle()

        val state1 = viewModel.uiState.value as ScholarshipFinderUiState.Success
        assertEquals(2, state1.scholarships.size)

        viewModel.onCategorySelected("WOMEN_IN_TECH")
        advanceUntilIdle()

        val state2 = viewModel.uiState.value as ScholarshipFinderUiState.Success
        assertEquals(1, state2.scholarships.size)
        assertEquals("Google Women Techmakers", state2.scholarships.first().name)

        // Search query test
        viewModel.onCategorySelected(null)
        viewModel.onSearchQueryChanged("STEM")
        advanceUntilIdle()

        val state3 = viewModel.uiState.value as ScholarshipFinderUiState.Success
        assertEquals(1, state3.scholarships.size)
        assertEquals("National Merit STEM Grant", state3.scholarships.first().name)
    }

    // -------------------------------------------------------------------------
    // 7. Scholarship Details Tests
    // -------------------------------------------------------------------------
    @Test
    fun testScholarshipDetailViewModel_LoadsDetails() = runTest {
        val viewModel = ScholarshipDetailViewModel(fakeRepo)
        viewModel.loadScholarship("s1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ScholarshipDetailUiState.Success
        assertEquals("National Merit STEM Grant", state.scholarship.name)
        assertTrue(state.scholarship.isSaved)
    }

    // -------------------------------------------------------------------------
    // 8. AI Recommendations Tests
    // -------------------------------------------------------------------------
    @Test
    fun testAiRecommendationsViewModel_SendsQueryAndReceivesResponse() = runTest {
        val viewModel = AiRecommendationsViewModel(fakeRepo)
        val initialSize = (viewModel.uiState.value as AiRecommendationsUiState.Success).messages.size

        viewModel.sendQuery("What are the best CS universities?")
        advanceUntilIdle()

        val state = viewModel.uiState.value as AiRecommendationsUiState.Success
        assertEquals(initialSize + 2, state.messages.size)
        assertEquals("AI", state.messages.last().sender)
        assertTrue(state.messages.last().content.contains("NIT Bangalore"))
    }

    // -------------------------------------------------------------------------
    // 9. Aspirant Profile Tests
    // -------------------------------------------------------------------------
    @Test
    fun testAspirantProfileViewModel_UpdatesAcademicGoals() = runTest {
        val viewModel = AspirantProfileViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as AspirantProfileUiState.Success
        assertEquals("B.Tech", state.profile.targetDegree)

        viewModel.saveProfile(
            fullName = "Rohan Mehta",
            bio = "Aspiring AI Researcher",
            targetDegree = "BS",
            targetMajor = "Artificial Intelligence",
            targetUniversities = "MIT, Stanford",
            highSchool = "DPS Mumbai",
            expectedGradYear = 2028
        )
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as AspirantProfileUiState.Success
        assertTrue(updatedState.saveSuccess)
        assertEquals("BS", updatedState.profile.targetDegree)
        assertEquals("Artificial Intelligence", updatedState.profile.targetMajor)
        assertEquals(2028, updatedState.profile.expectedGradYear)
    }

    @Test
    fun testAspirantProfileViewModel_UpdatesIeltsAndExamScores() = runTest {
        val viewModel = AspirantProfileViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.saveProfile(
            fullName = "Rohan Mehta",
            bio = "Aiming for Global Universities",
            targetDegree = "MS",
            targetMajor = "Computer Science",
            targetUniversities = "Oxford, Cambridge, Stanford",
            highSchool = "DPS Mumbai",
            expectedGradYear = 2026,
            entranceExamScores = mapOf("IELTS" to 8.0, "SAT" to 1540.0, "GPA" to 94.5)
        )
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as AspirantProfileUiState.Success
        assertTrue(updatedState.saveSuccess)
        assertEquals(8.0, updatedState.profile.entranceExamScores["IELTS"] ?: 0.0, 0.01)
        assertEquals(1540.0, updatedState.profile.entranceExamScores["SAT"] ?: 0.0, 0.01)
        assertEquals(94.5, updatedState.profile.entranceExamScores["GPA"] ?: 0.0, 0.01)
    }
}
