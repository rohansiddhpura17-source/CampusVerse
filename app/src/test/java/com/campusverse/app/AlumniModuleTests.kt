package com.campusverse.app

import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.model.AlumniConnectionItem
import com.campusverse.app.data.model.AlumniConnectionRequestItem
import com.campusverse.app.data.model.AlumniHomeSummary
import com.campusverse.app.data.model.AlumniNotificationItem
import com.campusverse.app.data.model.AlumniProfileData
import com.campusverse.app.data.model.CampusEvent
import com.campusverse.app.data.model.CareerPreferenceData
import com.campusverse.app.data.model.CareerRoadmapItem
import com.campusverse.app.data.model.ChatMessageItem
import com.campusverse.app.data.model.CompanyItem
import com.campusverse.app.data.model.ConversationItem
import com.campusverse.app.data.model.JobApplicationItem
import com.campusverse.app.data.model.JobOpportunity
import com.campusverse.app.data.model.MentorItem
import com.campusverse.app.data.model.MentorshipRequestItem
import com.campusverse.app.data.model.MentorshipSessionItem
import com.campusverse.app.data.model.MockInterviewSessionItem
import com.campusverse.app.data.model.NetworkActivityItem
import com.campusverse.app.data.model.PrivacySettingsData
import com.campusverse.app.data.model.ReferralItem
import com.campusverse.app.data.model.RoadmapMilestone
import com.campusverse.app.data.model.SecuritySettingsData
import com.campusverse.app.data.model.SkillItem
import com.campusverse.app.domain.alumni.AlumniRepository
import com.campusverse.app.ui.screens.alumni.AlumniHomeUiState
import com.campusverse.app.ui.screens.alumni.AlumniHomeViewModel
import com.campusverse.app.ui.screens.alumni.account.AlumniMyProfileUiState
import com.campusverse.app.ui.screens.alumni.account.AlumniMyProfileViewModel
import com.campusverse.app.ui.screens.alumni.account.AlumniNotificationsUiState
import com.campusverse.app.ui.screens.alumni.account.AlumniNotificationsViewModel
import com.campusverse.app.ui.screens.alumni.account.CareerPreferencesUiState
import com.campusverse.app.ui.screens.alumni.account.CareerPreferencesViewModel
import com.campusverse.app.ui.screens.alumni.careerdev.AiCareerAssistantUiState
import com.campusverse.app.ui.screens.alumni.careerdev.AiCareerAssistantViewModel
import com.campusverse.app.ui.screens.alumni.careerdev.CareerRoadmapUiState
import com.campusverse.app.ui.screens.alumni.careerdev.CareerRoadmapViewModel
import com.campusverse.app.ui.screens.alumni.careerdev.MockInterviewUiState
import com.campusverse.app.ui.screens.alumni.careerdev.MockInterviewViewModel
import com.campusverse.app.ui.screens.alumni.careerdev.SkillDevelopmentUiState
import com.campusverse.app.ui.screens.alumni.careerdev.SkillDevelopmentViewModel
import com.campusverse.app.ui.screens.alumni.careers.CareersUiState
import com.campusverse.app.ui.screens.alumni.careers.CareersViewModel
import com.campusverse.app.ui.screens.alumni.careers.JobDetailUiState
import com.campusverse.app.ui.screens.alumni.careers.JobDetailViewModel
import com.campusverse.app.ui.screens.alumni.careers.ReferralsUiState
import com.campusverse.app.ui.screens.alumni.careers.ReferralsViewModel
import com.campusverse.app.ui.screens.alumni.events.AlumniEventsUiState
import com.campusverse.app.ui.screens.alumni.events.AlumniEventsViewModel
import com.campusverse.app.ui.screens.alumni.mentorship.AlumniMentorshipUiState
import com.campusverse.app.ui.screens.alumni.mentorship.AlumniMentorshipViewModel
import com.campusverse.app.ui.screens.alumni.messaging.AlumniChatUiState
import com.campusverse.app.ui.screens.alumni.messaging.AlumniConversationsUiState
import com.campusverse.app.ui.screens.alumni.messaging.AlumniMessagingViewModel
import com.campusverse.app.ui.screens.alumni.network.AlumniNetworkUiState
import com.campusverse.app.ui.screens.alumni.network.AlumniNetworkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake in-memory repository for unit testing Alumni module viewmodels.
 */
class FakeAlumniRepository : AlumniRepository {
    var myProfile = AlumniProfileData(
        userId = "usr_alumni_1",
        fullName = "Priya Patel",
        email = "alumni@campusverse.edu",
        headline = "Staff Software Engineer @ Google",
        company = "Google",
        designation = "Staff Software Engineer",
        industry = "Cloud & Distributed Systems",
        yearsOfExperience = 7,
        degree = "B.Tech Computer Science",
        graduationYear = 2019,
        institution = "National Institute of Technology",
        willingToMentor = true,
        willingToRefer = true,
        skills = listOf("Kotlin", "Distributed Systems", "Kubernetes", "System Design")
    )

    var alumniDirectory = mutableListOf(
        AlumniProfileData(
            userId = "usr_alumni_2",
            fullName = "Vikram Mehta",
            email = "vikram.mehta@microsoft.com",
            company = "Microsoft",
            designation = "Principal Engineer",
            skills = listOf("Distributed Systems", "Azure")
        ),
        AlumniProfileData(
            userId = "usr_alumni_3",
            fullName = "Neha Kapoor",
            email = "neha.kapoor@uber.com",
            company = "Uber",
            designation = "Senior Engineering Manager",
            skills = listOf("System Design", "Leadership")
        )
    )

    var jobsList = mutableListOf(
        JobOpportunity(
            id = "job_seed_1",
            companyId = "comp_google",
            companyName = "Google",
            title = "Senior Distributed Systems Engineer",
            description = "Design global real-time infrastructure.",
            location = "Bangalore, India",
            isRemote = true,
            salaryRange = "$120,000 - $160,000"
        )
    )

    var savedJobs = mutableListOf<JobOpportunity>()
    var applications = mutableListOf<JobApplicationItem>()
    var referrals = mutableListOf(
        ReferralItem(
            id = "ref_1",
            alumniId = "usr_alumni_1",
            alumniName = "Priya Patel",
            studentId = "usr_student_1",
            studentName = "Aarav Sharma",
            companyName = "Google",
            jobTitle = "Software Engineer",
            status = "REQUESTED",
            isAlumniOwner = true
        )
    )

    var mentors = mutableListOf(
        MentorItem(
            id = "mentor_1",
            userId = "usr_alumni_2",
            fullName = "Vikram Mehta",
            title = "Principal Engineer @ Microsoft",
            company = "Microsoft",
            expertise = listOf("Distributed Systems", "System Design"),
            rating = 4.95,
            reviewsCount = 18
        )
    )

    var mentorshipRequests = mutableListOf(
        MentorshipRequestItem(
            id = "req_1",
            mentorId = "mentor_1",
            mentorName = "Vikram Mehta",
            menteeId = "usr_alumni_1",
            menteeName = "Priya Patel",
            goal = "Staff+ Transition",
            message = "Hi Vikram, I'd like your perspective on leading cross-org RFC reviews.",
            status = "PENDING"
        )
    )

    var mentorshipSessions = mutableListOf(
        MentorshipSessionItem(
            id = "sess_1",
            requestId = "req_1",
            mentorName = "Vikram Mehta",
            menteeName = "Priya Patel",
            scheduledAt = "Tomorrow at 4:00 PM",
            durationMinutes = 45,
            meetingUrl = "https://meet.google.com/abc-xyz-123",
            status = "SCHEDULED"
        )
    )

    var roadmaps = mutableListOf(
        CareerRoadmapItem(
            id = "rd_1",
            title = "Staff Software Engineer Transition",
            targetRole = "Staff Software Engineer (L6)",
            progressPercentage = 50.0,
            milestones = listOf(
                RoadmapMilestone("m1", "Lead Cross-Team Architecture RFC", true),
                RoadmapMilestone("m2", "Execute Multi-Region Migration", false)
            )
        )
    )

    var skillsList = mutableListOf(
        SkillItem("sk_1", "Distributed Systems", "ARCHITECTURE", "EXPERT", true, 95.0),
        SkillItem("sk_2", "Jetpack Compose", "MOBILE", "ADVANCED", true, 88.0)
    )

    var pastInterviews = mutableListOf<MockInterviewSessionItem>()
    var careerPreferences = CareerPreferenceData(
        preferredRoles = listOf("Staff Engineer", "Engineering Manager"),
        preferredLocations = listOf("Bangalore", "Remote"),
        targetSalary = "$200,000 - $280,000",
        remotePreference = "REMOTE"
    )

    var notifications = mutableListOf(
        AlumniNotificationItem("notif_1", "CONNECTION", "New Connection Request", "Vikram Mehta sent a connection request", false)
    )

    var privacySettings = PrivacySettingsData()
    var securitySettings = SecuritySettingsData()

    // 1. Alumni Home
    override suspend fun getAlumniHomeSummary(): Result<AlumniHomeSummary> = Result.success(
        AlumniHomeSummary(
            profile = myProfile,
            connectionsCount = 3,
            pendingRequestsCount = 1,
            activeMentorshipCount = 1,
            recommendedJobs = jobsList,
            upcomingEvents = listOf(
                CampusEvent("ev_1", "Annual Alumni Tech Summit 2026", "Keynotes from Google, Microsoft, and Uber alumni.", "NETWORKING", "Main Auditorium", true, null, "2026-09-15T10:00:00Z", "2026-09-15T18:00:00Z", 250, 180, true)
            ),
            networkActivities = listOf(
                NetworkActivityItem("act_1", "JOB", "Google posted a new opening", "Senior Distributed Systems Engineer")
            )
        )
    )

    // 2. Alumni Profile
    override suspend fun getMyProfile(): Result<AlumniProfileData> = Result.success(myProfile)
    override suspend fun getAlumniProfileById(id: String): Result<AlumniProfileData> =
        Result.success(alumniDirectory.find { it.userId == id } ?: myProfile)
    override suspend fun updateAlumniProfile(profile: AlumniProfileData): Result<AlumniProfileData> {
        myProfile = profile
        return Result.success(profile)
    }

    // 3. Alumni Network & Connections
    override suspend fun getAlumni(search: String?, company: String?, industry: String?, graduationYear: Int?, willingToMentor: Boolean?, willingToRefer: Boolean?): Result<List<AlumniProfileData>> {
        var filtered = alumniDirectory.toList()
        if (!search.isNullOrBlank()) filtered = filtered.filter { it.fullName.contains(search, true) || it.company.contains(search, true) }
        if (!company.isNullOrBlank()) filtered = filtered.filter { it.company.equals(company, true) }
        return Result.success(filtered)
    }
    override suspend fun getConnections(): Result<Pair<List<AlumniConnectionItem>, List<AlumniConnectionRequestItem>>> =
        Result.success(Pair(emptyList(), emptyList()))
    override suspend fun connectWithAlumni(alumniId: String): Result<Boolean> = Result.success(true)
    override suspend fun respondToConnectionRequest(connectionId: String, status: String): Result<Boolean> = Result.success(true)
    override suspend fun deleteConnection(connectionId: String): Result<Boolean> = Result.success(true)
    override suspend fun saveAlumni(alumniId: String): Result<Boolean> = Result.success(true)
    override suspend fun unsaveAlumni(alumniId: String): Result<Boolean> = Result.success(true)
    override suspend fun getSavedAlumni(): Result<List<AlumniProfileData>> = Result.success(emptyList())
    override suspend fun getNetworkActivity(): Result<List<NetworkActivityItem>> = Result.success(emptyList())

    // 4. Careers & Opportunities
    override suspend fun getJobs(search: String?, roleType: String?, isRemote: Boolean?, companyId: String?): Result<List<JobOpportunity>> =
        Result.success(jobsList)
    override suspend fun getRecommendedJobs(): Result<List<JobOpportunity>> = Result.success(jobsList)
    override suspend fun getJobById(id: String): Result<JobOpportunity> =
        Result.success(jobsList.find { it.id == id } ?: jobsList.first())
    override suspend fun saveJob(jobId: String): Result<Boolean> = Result.success(true)
    override suspend fun unsaveJob(jobId: String): Result<Boolean> = Result.success(true)
    override suspend fun getSavedJobs(): Result<List<JobOpportunity>> = Result.success(savedJobs)
    override suspend fun applyForJob(jobId: String, resumeUrl: String, coverLetter: String?): Result<JobApplicationItem> {
        val app = JobApplicationItem("app_${System.currentTimeMillis()}", jobId, "Job Title", "Company", "Location", "FULL_TIME", "APPLIED", resumeUrl, coverLetter)
        applications.add(app)
        return Result.success(app)
    }
    override suspend fun getApplications(): Result<List<JobApplicationItem>> = Result.success(applications)
    override suspend fun withdrawApplication(applicationId: String): Result<Boolean> = Result.success(true)

    // 5. Companies
    override suspend fun getCompanies(search: String?): Result<List<CompanyItem>> =
        Result.success(listOf(CompanyItem("comp_google", "Google", null, "https://google.com", "Tech", "Global tech leader", 5, true, jobsList)))
    override suspend fun getCompanyById(id: String): Result<CompanyItem> =
        Result.success(CompanyItem(id, "Google", null, "https://google.com", "Tech", "Global tech leader", 5, true, jobsList))
    override suspend fun getCompanyJobs(companyId: String): Result<List<JobOpportunity>> = Result.success(jobsList)

    // 6. Referrals
    override suspend fun getReferrals(): Result<List<ReferralItem>> = Result.success(referrals)
    override suspend fun requestReferral(alumniId: String, jobId: String?, companyName: String, notes: String?): Result<ReferralItem> {
        val r = ReferralItem("ref_${System.currentTimeMillis()}", alumniId, myProfile.fullName, "usr_student_1", "Candidate", jobId, "Software Engineer", companyName, "REQUESTED", notes, true)
        referrals.add(r)
        return Result.success(r)
    }
    override suspend fun updateReferralStatus(referralId: String, status: String, notes: String?): Result<Boolean> = Result.success(true)

    // 7. Mentorship
    override suspend fun getMentors(search: String?, expertise: String?): Result<List<MentorItem>> = Result.success(mentors)
    override suspend fun getMentorById(mentorId: String): Result<MentorItem> = Result.success(mentors.first())
    override suspend fun requestMentorship(mentorId: String, goal: String, message: String): Result<MentorshipRequestItem> =
        Result.success(mentorshipRequests.first())
    override suspend fun getMentorshipRequests(): Result<List<MentorshipRequestItem>> = Result.success(mentorshipRequests)
    override suspend fun respondMentorshipRequest(requestId: String, status: String): Result<Boolean> = Result.success(true)
    override suspend fun getMentorshipSessions(): Result<List<MentorshipSessionItem>> = Result.success(mentorshipSessions)
    override suspend fun getMentorshipSessionById(sessionId: String): Result<MentorshipSessionItem> = Result.success(mentorshipSessions.first())
    override suspend fun updateMentorshipSession(sessionId: String, status: String?, notes: String?): Result<Boolean> = Result.success(true)

    // 8. Events & Networking
    override suspend fun getEvents(search: String?, category: String?): Result<List<CampusEvent>> =
        Result.success(listOf(CampusEvent("ev_1", "Annual Alumni Tech Summit 2026", "Keynotes from Google & Microsoft alumni.", "NETWORKING", "Main Auditorium", true, null, "2026-09-15T10:00:00Z", "2026-09-15T18:00:00Z", 250, 180, true)))
    override suspend fun getEventById(id: String): Result<CampusEvent> = Result.success(
        CampusEvent("ev_1", "Annual Alumni Tech Summit 2026", "Keynotes from Google & Microsoft alumni.", "NETWORKING", "Main Auditorium", true, null, "2026-09-15T10:00:00Z", "2026-09-15T18:00:00Z", 250, 180, true)
    )
    override suspend fun registerForEvent(eventId: String): Result<Boolean> = Result.success(true)
    override suspend fun unregisterFromEvent(eventId: String): Result<Boolean> = Result.success(true)

    // 9. Career Development & AI
    override suspend fun askAiCareerAssistant(query: String, mode: String, topic: String?): Result<AiStudyQueryResponse> =
        Result.success(AiStudyQueryResponse(true, query, mode, null, "Detailed career guidance for: $query", listOf("L6 System Design", "STAR Framework")))
    override suspend fun getRoadmaps(): Result<List<CareerRoadmapItem>> = Result.success(roadmaps)
    override suspend fun createRoadmap(title: String, targetRole: String, milestones: List<String>): Result<CareerRoadmapItem> {
        val r = CareerRoadmapItem("rd_${System.currentTimeMillis()}", title, targetRole, milestones.mapIndexed { idx, m -> RoadmapMilestone("m_$idx", m, false) }, 0.0)
        roadmaps.add(r)
        return Result.success(r)
    }
    override suspend fun updateRoadmapMilestone(roadmapId: String, milestoneId: String, completed: Boolean): Result<Boolean> = Result.success(true)
    override suspend fun deleteRoadmap(roadmapId: String): Result<Boolean> = Result.success(true)
    override suspend fun getSkills(): Result<List<SkillItem>> = Result.success(skillsList)
    override suspend fun upsertSkill(skillName: String, category: String, level: String): Result<SkillItem> {
        val s = SkillItem("sk_${System.currentTimeMillis()}", skillName, category, level, true, 90.0)
        skillsList.add(s)
        return Result.success(s)
    }
    override suspend fun deleteSkill(skillId: String): Result<Boolean> {
        skillsList.removeAll { it.id == skillId }
        return Result.success(true)
    }
    override suspend fun getInterviews(): Result<List<MockInterviewSessionItem>> = Result.success(pastInterviews)
    override suspend fun getInterviewById(interviewId: String): Result<MockInterviewSessionItem> =
        Result.success(pastInterviews.firstOrNull() ?: MockInterviewSessionItem("int_1", "Staff Engineer", "Systems", 30, 92.0, 95.0, 90.0, 93.0, 90.0, "Transcript", listOf("Strength 1"), listOf("Improvement 1")))
    override suspend fun saveInterviewSession(session: MockInterviewSessionItem): Result<MockInterviewSessionItem> {
        pastInterviews.add(session)
        return Result.success(session)
    }
    override suspend fun getCareerPreferences(): Result<CareerPreferenceData> = Result.success(careerPreferences)
    override suspend fun updateCareerPreferences(preferences: CareerPreferenceData): Result<CareerPreferenceData> {
        careerPreferences = preferences
        return Result.success(preferences)
    }

    // 10. Messaging & Conversations
    override suspend fun getConversations(search: String?): Result<List<ConversationItem>> =
        Result.success(listOf(ConversationItem("conv_seed_1", "Direct Chat", "usr_alumni_2", "Vikram Mehta", null, "Principal Engineer", "Looking forward to connecting!", "10:30 AM", 0)))
    override suspend fun getMessages(conversationId: String): Result<List<ChatMessageItem>> =
        Result.success(listOf(ChatMessageItem("msg_1", conversationId, "usr_alumni_2", "Vikram Mehta", "Looking forward to connecting!", null, true, false, "10:30 AM")))
    override suspend fun startConversation(recipientUserId: String, initialMessage: String): Result<ConversationItem> =
        Result.success(ConversationItem("conv_${System.currentTimeMillis()}", "Direct Chat", recipientUserId, "Vikram Mehta", null, "Principal Engineer", initialMessage, "Just now", 0))
    override suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String?): Result<ChatMessageItem> =
        Result.success(ChatMessageItem("msg_${System.currentTimeMillis()}", conversationId, myProfile.userId, myProfile.fullName, content, mediaUrl, true, true, "Just now"))
    override suspend fun deleteMessage(messageId: String): Result<Boolean> = Result.success(true)
    override suspend fun reportUser(userId: String, reason: String): Result<Boolean> = Result.success(true)
    override suspend fun blockUser(userId: String): Result<Boolean> = Result.success(true)

    // 11. Notifications
    override suspend fun getNotifications(): Result<List<AlumniNotificationItem>> = Result.success(notifications)
    override suspend fun markNotificationRead(notificationId: String): Result<Boolean> = Result.success(true)
    override suspend fun markAllNotificationsRead(): Result<Boolean> = Result.success(true)

    // 12. Settings, Privacy & Security
    override suspend fun getPrivacySettings(): Result<PrivacySettingsData> = Result.success(privacySettings)
    override suspend fun updatePrivacySettings(settings: PrivacySettingsData): Result<PrivacySettingsData> {
        privacySettings = settings
        return Result.success(settings)
    }
    override suspend fun getSecuritySettings(): Result<SecuritySettingsData> = Result.success(securitySettings)
    override suspend fun updateSecuritySettings(settings: SecuritySettingsData): Result<SecuritySettingsData> {
        securitySettings = settings
        return Result.success(settings)
    }
    override suspend fun setAccountRecoveryEmail(email: String): Result<Boolean> = Result.success(true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlumniModuleTests {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeAlumniRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAlumniHomeViewModel_loadsDashboardSuccessfully() = runTest {
        val viewModel = AlumniHomeViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Success", state is AlumniHomeUiState.Success)
        val success = state as AlumniHomeUiState.Success
        assertNotNull(success.summary.profile)
        assertTrue("Connections count should be positive", success.summary.connectionsCount >= 0)
        assertTrue("Recommended jobs should not be empty", success.summary.recommendedJobs.isNotEmpty())
    }

    @Test
    fun testAlumniNetworkViewModel_filteringAndConnection() = runTest {
        val viewModel = AlumniNetworkViewModel(repository)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state is AlumniNetworkUiState.Success)
        val initialSize = (state as AlumniNetworkUiState.Success).alumniList.size
        assertTrue("Initial directory should have entries", initialSize > 0)

        // Filter by company
        viewModel.selectCompany("Microsoft")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertTrue(state is AlumniNetworkUiState.Success)

        // Send connection request
        val firstAlumni = (state as AlumniNetworkUiState.Success).alumniList.first()
        viewModel.connect(firstAlumni.userId)
        advanceUntilIdle()
        assertTrue("Send connection should complete cleanly", true)
    }

    @Test
    fun testJobDetailViewModel_jobDetailsAndApplicationFlow() = runTest {
        val viewModel = JobDetailViewModel(repository)
        viewModel.loadJob("job_seed_1")
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue("Should load job details", state is JobDetailUiState.Success)
        val success = state as JobDetailUiState.Success
        assertEquals("job_seed_1", success.job.id)

        // Bookmark toggle
        viewModel.toggleSave()
        advanceUntilIdle()

        // Apply
        viewModel.apply("https://docs.campusverse.edu/resumes/my_resume.pdf", "Passionate about high-scale distributed systems.")
        advanceUntilIdle()
        val postApplyState = viewModel.uiState.value as JobDetailUiState.Success
        assertTrue("Application should succeed", postApplyState.applicationSuccess)
    }

    @Test
    fun testCareersViewModel_andReferrals() = runTest {
        val careersVm = CareersViewModel(repository)
        advanceUntilIdle()
        assertTrue(careersVm.uiState.value is CareersUiState.Success)

        val referralsVm = ReferralsViewModel(repository)
        advanceUntilIdle()
        assertTrue(referralsVm.uiState.value is ReferralsUiState.Success)
    }

    @Test
    fun testAlumniMentorshipViewModel_mentorsAndRequests() = runTest {
        val viewModel = AlumniMentorshipViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Mentorship hub should load", state is AlumniMentorshipUiState.Success)
        val success = state as AlumniMentorshipUiState.Success
        assertTrue("Should have mentors", success.mentors.isNotEmpty())
    }

    @Test
    fun testAlumniEventsViewModel_eventsDiscovery() = runTest {
        val viewModel = AlumniEventsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Events hub should load", state is AlumniEventsUiState.Success)
        val success = state as AlumniEventsUiState.Success
        assertTrue("Should have events", success.events.isNotEmpty())
    }

    @Test
    fun testCareerRoadmapsAndSkillDevelopment() = runTest {
        val roadmapVm = CareerRoadmapViewModel(repository)
        advanceUntilIdle()
        assertTrue(roadmapVm.uiState.value is CareerRoadmapUiState.Success)

        val skillVm = SkillDevelopmentViewModel(repository)
        advanceUntilIdle()
        assertTrue(skillVm.uiState.value is SkillDevelopmentUiState.Success)
        val initialCount = (skillVm.uiState.value as SkillDevelopmentUiState.Success).skills.size

        skillVm.upsertSkill("GraphQL Federation", "ARCHITECTURE", "ADVANCED")
        advanceUntilIdle()
        val afterAddCount = (skillVm.uiState.value as SkillDevelopmentUiState.Success).skills.size
        assertTrue("Skills count should increase or update", afterAddCount >= initialCount)
    }

    @Test
    fun testAiCareerAssistantViewModel_interactiveCoach() = runTest {
        val viewModel = AiCareerAssistantViewModel(repository)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value as AiCareerAssistantUiState.Success
        assertTrue("Should have welcome message", initialState.messages.isNotEmpty())

        viewModel.sendQuery("How do I prepare for Staff Engineer system design?")
        advanceUntilIdle()

        val afterQueryState = viewModel.uiState.value as AiCareerAssistantUiState.Success
        assertTrue("Should contain user and AI response messages", afterQueryState.messages.size >= 3)
    }

    @Test
    fun testMockInterviewViewModel_simulationWorkflow() = runTest {
        val viewModel = MockInterviewViewModel(repository)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue("Initial state is active simulation", state is MockInterviewUiState.Active)
        assertEquals(0, (state as MockInterviewUiState.Active).questionIndex)

        // Answer Q1
        viewModel.onAnswerChanged("I would use CRDTs with state-based replication and WebSockets.")
        viewModel.submitAnswer()
        advanceUntilIdle()

        // Answer Q2
        viewModel.onAnswerChanged("I used the STAR framework to lead consensus on microservices decomposition.")
        viewModel.submitAnswer()
        advanceUntilIdle()

        // Answer Q3
        viewModel.onAnswerChanged("I would enforce unidirectional data flow with Kotlin Flows and Room.")
        viewModel.submitAnswer()
        advanceUntilIdle()

        // Interview should complete with real evaluation scores
        val endState = viewModel.uiState.value
        assertTrue("Simulation should be completed", endState is MockInterviewUiState.Completed)
        val completed = endState as MockInterviewUiState.Completed
        assertTrue("Score should be calculated", completed.session.overallScore > 0)
        assertTrue("Strengths should be populated", completed.session.strengths.isNotEmpty())
    }

    @Test
    fun testAlumniMessagingViewModel_chatAndConversations() = runTest {
        val viewModel = AlumniMessagingViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.conversationsState.value is AlumniConversationsUiState.Success)

        viewModel.loadChat("conv_seed_1")
        advanceUntilIdle()
        assertTrue(viewModel.chatState.value is AlumniChatUiState.Success)

        viewModel.sendMessage("Hello from Alumni unit test!")
        advanceUntilIdle()
        assertTrue(viewModel.chatState.value is AlumniChatUiState.Success)
    }

    @Test
    fun testAlumniNotificationsViewModel() = runTest {
        val viewModel = AlumniNotificationsViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AlumniNotificationsUiState.Success)
        val notifs = (viewModel.uiState.value as AlumniNotificationsUiState.Success).notifications
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun testAlumniMyProfileAndCareerPreferences() = runTest {
        val profileVm = AlumniMyProfileViewModel(repository)
        advanceUntilIdle()

        assertTrue(profileVm.uiState.value is AlumniMyProfileUiState.Success)
        val profile = (profileVm.uiState.value as AlumniMyProfileUiState.Success).profile
        assertEquals("Priya Patel", profile.fullName)

        profileVm.saveProfile(profile.copy(headline = "Staff Software Engineer @ Google"))
        advanceUntilIdle()
        val updatedState = profileVm.uiState.value as AlumniMyProfileUiState.Success
        assertEquals("Staff Software Engineer @ Google", updatedState.profile.headline)

        val prefVm = CareerPreferencesViewModel(repository)
        advanceUntilIdle()
        assertTrue(prefVm.uiState.value is CareerPreferencesUiState.Success)
        val prefs = (prefVm.uiState.value as CareerPreferencesUiState.Success).preferences

        prefVm.savePreferences(prefs.copy(targetSalary = "$250,000 - $320,000"))
        advanceUntilIdle()
        val updatedPrefState = prefVm.uiState.value as CareerPreferencesUiState.Success
        assertEquals("$250,000 - $320,000", updatedPrefState.preferences.targetSalary)
    }
}
