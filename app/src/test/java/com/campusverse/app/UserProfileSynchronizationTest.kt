package com.campusverse.app

import com.campusverse.app.data.model.AlumniProfileData
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.data.model.StudentAcademicPreferences
import com.campusverse.app.data.model.StudentProfileData
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.preferences.ModulePreferencesManager
import com.campusverse.app.data.repository.CurrentUserProfile
import com.campusverse.app.data.repository.UserProfileRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import com.campusverse.app.domain.aspirant.AspirantRepository
import com.campusverse.app.domain.auth.AuthSession
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.InMemorySessionManager
import com.campusverse.app.domain.session.SessionManager
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileSynchronizationTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var fakeStudentRepo: FakeStudentRepository
    private lateinit var fakeAlumniRepo: FakeAlumniRepository
    private lateinit var fakeAspirantRepo: FakeAspirantRepository
    private lateinit var userProfileRepository: UserProfileRepository

    @Before
    fun setUp() {
        sessionManager = InMemorySessionManager()
        fakeStudentRepo = FakeStudentRepository()
        fakeAlumniRepo = FakeAlumniRepository()
        fakeAspirantRepo = FakeAspirantRepository()

        userProfileRepository = UserProfileRepository(
            sessionManager = sessionManager,
            studentRepository = fakeStudentRepo,
            alumniRepository = fakeAlumniRepo,
            aspirantRepository = fakeAspirantRepo,
            modulePreferencesManager = null
        )
    }

    @Test
    fun `loadProfile for STUDENT populates StateFlow with student profile data`() = runTest {
        val initialUser = AuthenticatedUser(
            userId = "std_101",
            email = "student@campusverse.edu",
            name = "Rohan Mehta",
            role = UserRole.STUDENT,
            isEmailVerified = true
        )
        sessionManager.saveSession(
            AuthSession("token_abc", initialUser, System.currentTimeMillis() + 100000)
        )

        fakeStudentRepo.profileToReturn = StudentProfileData(
            userId = "std_101",
            fullName = "Rohan Mehta",
            email = "student@campusverse.edu",
            role = "STUDENT",
            degree = "B.Tech",
            branch = "CSE",
            semester = 6,
            cgpa = 8.75
        )

        val loaded = userProfileRepository.loadProfile("std_101", UserRole.STUDENT)
        assertNotNull(loaded)
        assertEquals("Rohan Mehta", loaded?.name)
        assertEquals(UserRole.STUDENT, loaded?.role)

        val flowValue = userProfileRepository.profile.value
        assertNotNull(flowValue)
        assertEquals("Rohan Mehta", flowValue?.name)
        val sp = flowValue?.profileData as? StudentProfileData
        assertEquals(8.75, sp?.cgpa ?: 0.0, 0.001)
    }

    @Test
    fun `updateProfile synchronizes in-memory flow and updates sessionManager name`() = runTest {
        val initialUser = AuthenticatedUser(
            userId = "std_101",
            email = "student@campusverse.edu",
            name = "Rohan Mehta",
            role = UserRole.STUDENT,
            isEmailVerified = true
        )
        sessionManager.saveSession(
            AuthSession("token_abc", initialUser, System.currentTimeMillis() + 100000)
        )

        val updatedStudent = StudentProfileData(
            userId = "std_101",
            fullName = "Rohan Joshu Mehta",
            email = "student@campusverse.edu",
            role = "STUDENT",
            bio = "Lead Android Architect",
            degree = "B.Tech",
            branch = "CSE",
            semester = 7,
            cgpa = 9.10
        )

        val newProfile = CurrentUserProfile(
            userId = "std_101",
            name = "Rohan Joshu Mehta",
            email = "student@campusverse.edu",
            role = UserRole.STUDENT,
            profileData = updatedStudent
        )

        val result = userProfileRepository.updateProfile(newProfile)
        assertTrue(result.isSuccess)

        // Verify flow was updated
        assertEquals("Rohan Joshu Mehta", userProfileRepository.profile.value?.name)

        // Verify session manager was updated
        val updatedSession = sessionManager.getSession()
        assertNotNull(updatedSession)
        assertEquals("Rohan Joshu Mehta", updatedSession?.user?.name)

        // Verify fake repo received the update
        assertEquals("Rohan Joshu Mehta", fakeStudentRepo.updatedProfile?.fullName)
        assertEquals(7, fakeStudentRepo.updatedProfile?.semester)
    }

    @Test
    fun `loadProfile and updateProfile for ALUMNI role synchronizes properly`() = runTest {
        val initialUser = AuthenticatedUser(
            userId = "alm_202",
            email = "alumni@campusverse.edu",
            name = "Dr. Aisha Patel",
            role = UserRole.ALUMNI,
            isEmailVerified = true
        )
        sessionManager.saveSession(
            AuthSession("token_alm", initialUser, System.currentTimeMillis() + 100000)
        )

        fakeAlumniRepo.profileToReturn = AlumniProfileData(
            userId = "alm_202",
            fullName = "Dr. Aisha Patel",
            email = "alumni@campusverse.edu",
            company = "Google",
            designation = "Senior AI Engineer"
        )

        val loaded = userProfileRepository.loadProfile("alm_202", UserRole.ALUMNI)
        assertEquals("Dr. Aisha Patel", loaded?.name)
        assertEquals(UserRole.ALUMNI, loaded?.role)

        val updatedAlumni = fakeAlumniRepo.profileToReturn.copy(
            fullName = "Dr. Aisha Patel, Ph.D.",
            designation = "Principal AI Scientist"
        )

        val updated = CurrentUserProfile(
            userId = "alm_202",
            name = "Dr. Aisha Patel, Ph.D.",
            email = "alumni@campusverse.edu",
            role = UserRole.ALUMNI,
            profileData = updatedAlumni
        )

        val updateResult = userProfileRepository.updateProfile(updated)
        assertTrue(updateResult.isSuccess)

        assertEquals("Dr. Aisha Patel, Ph.D.", userProfileRepository.profile.value?.name)
        assertEquals("Dr. Aisha Patel, Ph.D.", sessionManager.getSession()?.user?.name)
    }

    @Test
    fun `loadProfile and updateProfile for ASPIRANT role synchronizes properly`() = runTest {
        val initialUser = AuthenticatedUser(
            userId = "asp_303",
            email = "aspirant@campusverse.edu",
            name = "Kavya Sharma",
            role = UserRole.ASPIRANT,
            isEmailVerified = true
        )
        sessionManager.saveSession(
            AuthSession("token_asp", initialUser, System.currentTimeMillis() + 100000)
        )

        fakeAspirantRepo.profileToReturn = AspirantProfileData(
            userId = "asp_303",
            email = "aspirant@campusverse.edu",
            fullName = "Kavya Sharma",
            targetDegree = "B.Tech",
            targetMajor = "AI & Data Science"
        )

        val loaded = userProfileRepository.loadProfile("asp_303", UserRole.ASPIRANT)
        assertEquals("Kavya Sharma", loaded?.name)
        assertEquals(UserRole.ASPIRANT, loaded?.role)

        val updatedAspirant = fakeAspirantRepo.profileToReturn.copy(
            fullName = "Kavya S. Sharma",
            targetUniversities = "IIT Bombay, NIT Surathkal"
        )

        val updated = CurrentUserProfile(
            userId = "asp_303",
            name = "Kavya S. Sharma",
            email = "aspirant@campusverse.edu",
            role = UserRole.ASPIRANT,
            profileData = updatedAspirant
        )

        val updateResult = userProfileRepository.updateProfile(updated)
        assertTrue(updateResult.isSuccess)

        assertEquals("Kavya S. Sharma", userProfileRepository.profile.value?.name)
        assertEquals("Kavya S. Sharma", sessionManager.getSession()?.user?.name)
    }

    @Test
    fun `updateProfile updates email and photo in session and repository`() = runTest {
        val initialUser = AuthenticatedUser(
            userId = "std_101",
            email = "student@campusverse.edu",
            name = "Rohan Mehta",
            role = UserRole.STUDENT,
            isEmailVerified = true
        )
        sessionManager.saveSession(
            AuthSession("token_abc", initialUser, System.currentTimeMillis() + 100000)
        )

        val updated = CurrentUserProfile(
            userId = "std_101",
            name = "Rohan Mehta",
            email = "rohan.new@campusverse.edu",
            photoUrl = "https://campusverse.edu/photos/rohan.png",
            role = UserRole.STUDENT
        )

        val result = userProfileRepository.updateProfile(updated)
        assertTrue(result.isSuccess)
        assertEquals("rohan.new@campusverse.edu", userProfileRepository.profile.value?.email)
        assertEquals("https://campusverse.edu/photos/rohan.png", userProfileRepository.profile.value?.photoUrl)
        assertEquals("rohan.new@campusverse.edu", sessionManager.getSession()?.user?.email)
    }

    @Test
    fun `logoutClearsSession - verify SessionManager returns null after logout`() = runTest {
        val user = AuthenticatedUser(
            userId = "std_101",
            email = "student@campusverse.edu",
            name = "Rohan Mehta",
            role = UserRole.STUDENT
        )
        sessionManager.saveSession(AuthSession("token_xyz", user, System.currentTimeMillis() + 100000))
        assertNotNull(sessionManager.getSession())

        sessionManager.clearSession()
        assertNull(sessionManager.getSession())
    }

    @Test
    fun `logoutClearsProfileRepository - verify UserProfileRepository profile value is null`() = runTest {
        val user = AuthenticatedUser(
            userId = "std_101",
            email = "student@campusverse.edu",
            name = "Rohan Mehta",
            role = UserRole.STUDENT
        )
        sessionManager.saveSession(AuthSession("token_xyz", user, System.currentTimeMillis() + 100000))
        fakeStudentRepo.profileToReturn = StudentProfileData(
            userId = "std_101",
            fullName = "Rohan Mehta",
            email = "student@campusverse.edu"
        )
        userProfileRepository.loadProfile("std_101", UserRole.STUDENT)
        assertNotNull(userProfileRepository.profile.value)

        userProfileRepository.clear()
        assertNull(userProfileRepository.profile.value)
    }

    @Test
    fun `clear wipes profile state on logout preventing cross-session leakage`() = runTest {
        val userA = AuthenticatedUser(
            userId = "std_101",
            email = "student_a@campusverse.edu",
            name = "Student A",
            role = UserRole.STUDENT
        )
        sessionManager.saveSession(AuthSession("token_a", userA, System.currentTimeMillis() + 100000))
        fakeStudentRepo.profileToReturn = StudentProfileData(
            userId = "std_101",
            fullName = "Student A",
            email = "student_a@campusverse.edu"
        )
        userProfileRepository.loadProfile("std_101", UserRole.STUDENT)

        assertEquals("Student A", userProfileRepository.profile.value?.name)

        // Logout action
        sessionManager.clearSession()
        userProfileRepository.clear()

        assertNull(userProfileRepository.profile.value)
        assertNull(sessionManager.getSession())

        // Login as User B
        val userB = AuthenticatedUser(
            userId = "alm_202",
            email = "alumni_b@campusverse.edu",
            name = "Alumni B",
            role = UserRole.ALUMNI
        )
        sessionManager.saveSession(AuthSession("token_b", userB, System.currentTimeMillis() + 100000))
        fakeAlumniRepo.profileToReturn = AlumniProfileData(
            userId = "alm_202",
            fullName = "Alumni B",
            email = "alumni_b@campusverse.edu"
        )
        userProfileRepository.loadProfile("alm_202", UserRole.ALUMNI)

        // Verify User B sees only User B data
        assertEquals("Alumni B", userProfileRepository.profile.value?.name)
        assertEquals("alumni_b@campusverse.edu", userProfileRepository.profile.value?.email)
        assertEquals(UserRole.ALUMNI, userProfileRepository.profile.value?.role)
    }

    // -------------------------------------------------------------------------
    // Test Doubles
    // -------------------------------------------------------------------------
    private class FakeStudentRepository : StudentRepository {
        var profileToReturn = StudentProfileData(
            userId = "std_101",
            fullName = "Default Student",
            email = "student@campusverse.edu"
        )
        var updatedProfile: StudentProfileData? = null

        override suspend fun getStudentProfile(): Result<StudentProfileData> = Result.success(profileToReturn)
        override suspend fun updateStudentProfile(data: StudentProfileData): Result<StudentProfileData> {
            updatedProfile = data
            profileToReturn = data
            return Result.success(data)
        }
        override suspend fun getAcademicSummary() = Result.failure<com.campusverse.app.data.model.AcademicSummary>(Exception())
        override suspend fun getCourses(semester: Int?, search: String?) = Result.success(emptyList<com.campusverse.app.data.model.CourseItem>())
        override suspend fun getNotes(search: String?, courseId: String?, tag: String?) = Result.success(emptyList<com.campusverse.app.data.model.NoteItem>())
        override suspend fun getMyNotes() = Result.success(emptyList<com.campusverse.app.data.model.NoteItem>())
        override suspend fun getNoteById(id: String) = Result.failure<com.campusverse.app.data.model.NoteItem>(Exception())
        override suspend fun createNote(title: String, description: String?, fileUrl: String, courseId: String?, tags: String?) = Result.failure<com.campusverse.app.data.model.NoteItem>(Exception())
        override suspend fun updateNote(id: String, title: String?, description: String?, tags: String?) = Result.failure<com.campusverse.app.data.model.NoteItem>(Exception())
        override suspend fun deleteNote(id: String) = Result.success(true)
        override suspend fun requestNoteRemoval(noteId: String, reason: String) = Result.failure<com.campusverse.app.data.model.NoteItem>(Exception())
        override suspend fun reportNote(noteId: String, reason: String) = Result.success(true)
        override suspend fun getLibraryResources(search: String?, category: String?) = Result.success(emptyList<com.campusverse.app.data.model.LibraryResource>())
        override suspend fun getLibraryResourceById(id: String) = Result.failure<com.campusverse.app.data.model.LibraryResource>(Exception())
        override suspend fun askAiStudyAssistant(query: String, mode: String, topic: String?) = Result.failure<com.campusverse.app.data.model.AiStudyQueryResponse>(Exception())
        override suspend fun getEvents(search: String?, category: String?) = Result.success(emptyList<com.campusverse.app.data.model.CampusEvent>())
        override suspend fun getEventById(id: String) = Result.failure<com.campusverse.app.data.model.CampusEvent>(Exception())
        override suspend fun registerForEvent(eventId: String) = Result.success(true)
        override suspend fun unregisterFromEvent(eventId: String) = Result.success(true)
        override suspend fun getCommunities(search: String?) = Result.success(emptyList<com.campusverse.app.data.model.CommunityItem>())
        override suspend fun getCommunityById(id: String) = Result.success(Pair(com.campusverse.app.data.model.CommunityItem("c1", "Test", "Desc", null, 10, 5, true), emptyList<com.campusverse.app.data.model.CommunityPostItem>()))
        override suspend fun joinCommunity(communityId: String) = Result.success(true)
        override suspend fun leaveCommunity(communityId: String) = Result.success(true)
        override suspend fun createCommunityPost(communityId: String, title: String, content: String) = Result.failure<com.campusverse.app.data.model.CommunityPostItem>(Exception())
        override suspend fun updateCommunityPost(postId: String, title: String?, content: String?) = Result.failure<com.campusverse.app.data.model.CommunityPostItem>(Exception())
        override suspend fun deleteCommunityPost(postId: String) = Result.success(true)
        override suspend fun likeCommunityPost(postId: String) = Result.success(1)
        override suspend fun createComment(postId: String, content: String) = Result.failure<com.campusverse.app.data.model.CommunityCommentItem>(Exception())
        override suspend fun reportContent(targetType: String, targetId: String, reason: String) = Result.success(true)
        override suspend fun getMarketplaceListings(search: String?, category: String?, condition: String?) = Result.success(emptyList<com.campusverse.app.data.model.MarketplaceProduct>())
        override suspend fun getMarketplaceProductById(id: String) = Result.failure<com.campusverse.app.data.model.MarketplaceProduct>(Exception())
        override suspend fun createMarketplaceListing(title: String, description: String, price: Double, category: String, condition: String) = Result.failure<com.campusverse.app.data.model.MarketplaceProduct>(Exception())
        override suspend fun updateMarketplaceListing(id: String, price: Double?, status: String?) = Result.failure<com.campusverse.app.data.model.MarketplaceProduct>(Exception())
        override suspend fun deleteMarketplaceListing(id: String) = Result.success(true)
    }

    private class FakeAlumniRepository : AlumniRepository {
        var profileToReturn = AlumniProfileData(
            userId = "alm_202",
            fullName = "Default Alumni",
            email = "alumni@campusverse.edu"
        )

        override suspend fun getMyProfile(): Result<AlumniProfileData> = Result.success(profileToReturn)
        override suspend fun updateAlumniProfile(profile: AlumniProfileData): Result<AlumniProfileData> {
            profileToReturn = profile
            return Result.success(profile)
        }
        override suspend fun getAlumniHomeSummary() = Result.failure<com.campusverse.app.data.model.AlumniHomeSummary>(Exception())
        override suspend fun getAlumniProfileById(id: String) = Result.success(profileToReturn)
        override suspend fun getAlumni(search: String?, company: String?, industry: String?, graduationYear: Int?, willingToMentor: Boolean?, willingToRefer: Boolean?) = Result.success(emptyList<AlumniProfileData>())
        override suspend fun getConnections() = Result.success(Pair(emptyList<com.campusverse.app.data.model.AlumniConnectionItem>(), emptyList<com.campusverse.app.data.model.AlumniConnectionRequestItem>()))
        override suspend fun connectWithAlumni(alumniId: String) = Result.success(true)
        override suspend fun respondToConnectionRequest(connectionId: String, status: String) = Result.success(true)
        override suspend fun deleteConnection(connectionId: String) = Result.success(true)
        override suspend fun saveAlumni(alumniId: String) = Result.success(true)
        override suspend fun unsaveAlumni(alumniId: String) = Result.success(true)
        override suspend fun getSavedAlumni() = Result.success(emptyList<AlumniProfileData>())
        override suspend fun getNetworkActivity() = Result.success(emptyList<com.campusverse.app.data.model.NetworkActivityItem>())
        override suspend fun getJobs(search: String?, roleType: String?, isRemote: Boolean?, companyId: String?) = Result.success(emptyList<com.campusverse.app.data.model.JobOpportunity>())
        override suspend fun getRecommendedJobs() = Result.success(emptyList<com.campusverse.app.data.model.JobOpportunity>())
        override suspend fun getJobById(id: String) = Result.failure<com.campusverse.app.data.model.JobOpportunity>(Exception())
        override suspend fun saveJob(jobId: String) = Result.success(true)
        override suspend fun unsaveJob(jobId: String) = Result.success(true)
        override suspend fun getSavedJobs() = Result.success(emptyList<com.campusverse.app.data.model.JobOpportunity>())
        override suspend fun applyForJob(jobId: String, resumeUrl: String, coverLetter: String?) = Result.failure<com.campusverse.app.data.model.JobApplicationItem>(Exception())
        override suspend fun getApplications() = Result.success(emptyList<com.campusverse.app.data.model.JobApplicationItem>())
        override suspend fun withdrawApplication(applicationId: String) = Result.success(true)
        override suspend fun getCompanies(search: String?) = Result.success(emptyList<com.campusverse.app.data.model.CompanyItem>())
        override suspend fun getCompanyById(id: String) = Result.failure<com.campusverse.app.data.model.CompanyItem>(Exception())
        override suspend fun getCompanyJobs(companyId: String) = Result.success(emptyList<com.campusverse.app.data.model.JobOpportunity>())
        override suspend fun getReferrals() = Result.success(emptyList<com.campusverse.app.data.model.ReferralItem>())
        override suspend fun requestReferral(alumniId: String, jobId: String?, companyName: String, notes: String?) = Result.failure<com.campusverse.app.data.model.ReferralItem>(Exception())
        override suspend fun updateReferralStatus(referralId: String, status: String, notes: String?) = Result.success(true)
        override suspend fun getMentors(search: String?, expertise: String?) = Result.success(emptyList<com.campusverse.app.data.model.MentorItem>())
        override suspend fun getMentorById(mentorId: String) = Result.failure<com.campusverse.app.data.model.MentorItem>(Exception())
        override suspend fun requestMentorship(mentorId: String, goal: String, message: String) = Result.failure<com.campusverse.app.data.model.MentorshipRequestItem>(Exception())
        override suspend fun getMentorshipRequests() = Result.success(emptyList<com.campusverse.app.data.model.MentorshipRequestItem>())
        override suspend fun respondMentorshipRequest(requestId: String, status: String) = Result.success(true)
        override suspend fun getMentorshipSessions() = Result.success(emptyList<com.campusverse.app.data.model.MentorshipSessionItem>())
        override suspend fun getMentorshipSessionById(sessionId: String) = Result.failure<com.campusverse.app.data.model.MentorshipSessionItem>(Exception())
        override suspend fun updateMentorshipSession(sessionId: String, status: String?, notes: String?) = Result.success(true)
        override suspend fun getEvents(search: String?, category: String?) = Result.success(emptyList<com.campusverse.app.data.model.CampusEvent>())
        override suspend fun getEventById(id: String) = Result.failure<com.campusverse.app.data.model.CampusEvent>(Exception())
        override suspend fun registerForEvent(eventId: String) = Result.success(true)
        override suspend fun unregisterFromEvent(eventId: String) = Result.success(true)
        override suspend fun askAiCareerAssistant(query: String, mode: String, topic: String?) = Result.failure<com.campusverse.app.data.model.AiStudyQueryResponse>(Exception())
        override suspend fun getRoadmaps() = Result.success(emptyList<com.campusverse.app.data.model.CareerRoadmapItem>())
        override suspend fun createRoadmap(title: String, targetRole: String, milestones: List<String>) = Result.failure<com.campusverse.app.data.model.CareerRoadmapItem>(Exception())
        override suspend fun updateRoadmapMilestone(roadmapId: String, milestoneId: String, completed: Boolean) = Result.success(true)
        override suspend fun deleteRoadmap(roadmapId: String) = Result.success(true)
        override suspend fun getSkills() = Result.success(emptyList<com.campusverse.app.data.model.SkillItem>())
        override suspend fun upsertSkill(skillName: String, category: String, level: String) = Result.failure<com.campusverse.app.data.model.SkillItem>(Exception())
        override suspend fun deleteSkill(skillId: String) = Result.success(true)
        override suspend fun getInterviews() = Result.success(emptyList<com.campusverse.app.data.model.MockInterviewSessionItem>())
        override suspend fun getInterviewById(interviewId: String) = Result.failure<com.campusverse.app.data.model.MockInterviewSessionItem>(Exception())
        override suspend fun saveInterviewSession(session: com.campusverse.app.data.model.MockInterviewSessionItem) = Result.failure<com.campusverse.app.data.model.MockInterviewSessionItem>(Exception())
        override suspend fun getCareerPreferences() = Result.success(com.campusverse.app.data.model.CareerPreferenceData())
        override suspend fun updateCareerPreferences(preferences: com.campusverse.app.data.model.CareerPreferenceData) = Result.success(preferences)
        override suspend fun getConversations(search: String?) = Result.success(emptyList<com.campusverse.app.data.model.ConversationItem>())
        override suspend fun getMessages(conversationId: String) = Result.success(emptyList<com.campusverse.app.data.model.ChatMessageItem>())
        override suspend fun startConversation(recipientUserId: String, initialMessage: String) = Result.failure<com.campusverse.app.data.model.ConversationItem>(Exception())
        override suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String?) = Result.failure<com.campusverse.app.data.model.ChatMessageItem>(Exception())
        override suspend fun deleteMessage(messageId: String) = Result.success(true)
        override suspend fun reportUser(userId: String, reason: String) = Result.success(true)
        override suspend fun blockUser(userId: String) = Result.success(true)
        override suspend fun getNotifications() = Result.success(emptyList<com.campusverse.app.data.model.AlumniNotificationItem>())
        override suspend fun markNotificationRead(notificationId: String) = Result.success(true)
        override suspend fun markAllNotificationsRead() = Result.success(true)
        override suspend fun getPrivacySettings() = Result.success(com.campusverse.app.data.model.PrivacySettingsData())
        override suspend fun updatePrivacySettings(settings: com.campusverse.app.data.model.PrivacySettingsData) = Result.success(settings)
        override suspend fun getSecuritySettings() = Result.success(com.campusverse.app.data.model.SecuritySettingsData())
        override suspend fun updateSecuritySettings(settings: com.campusverse.app.data.model.SecuritySettingsData) = Result.success(settings)
        override suspend fun setAccountRecoveryEmail(email: String) = Result.success(true)
    }

    private class FakeAspirantRepository : AspirantRepository {
        var profileToReturn = AspirantProfileData(
            userId = "asp_303",
            email = "aspirant@campusverse.edu",
            fullName = "Default Aspirant"
        )

        override suspend fun getAspirantProfile(): Result<AspirantProfileData> = Result.success(profileToReturn)
        override suspend fun updateAspirantProfile(profile: AspirantProfileData): Result<AspirantProfileData> {
            profileToReturn = profile
            return Result.success(profile)
        }
        override suspend fun getAspirantHomeSummary() = Result.failure<com.campusverse.app.data.model.AspirantHomeSummary>(Exception())
        override suspend fun getColleges(search: String?, country: String?, degree: String?, sortBy: String?) = Result.success(emptyList<com.campusverse.app.data.model.CollegeItem>())
        override suspend fun getCollegeById(id: String) = Result.failure<com.campusverse.app.data.model.CollegeItem>(Exception())
        override suspend fun saveCollege(collegeId: String) = Result.success(true)
        override suspend fun unsaveCollege(collegeId: String) = Result.success(true)
        override suspend fun getSavedColleges() = Result.success(emptyList<com.campusverse.app.data.model.CollegeItem>())
        override suspend fun compareColleges(collegeIds: List<String>) = Result.success(emptyList<com.campusverse.app.data.model.CollegeComparisonItem>())
        override suspend fun getScholarships(search: String?, category: String?, country: String?) = Result.success(emptyList<com.campusverse.app.data.model.ScholarshipItem>())
        override suspend fun getScholarshipById(id: String) = Result.failure<com.campusverse.app.data.model.ScholarshipItem>(Exception())
        override suspend fun saveScholarship(scholarshipId: String) = Result.success(true)
        override suspend fun unsaveScholarship(scholarshipId: String) = Result.success(true)
        override suspend fun getSavedScholarships() = Result.success(emptyList<com.campusverse.app.data.model.ScholarshipItem>())
        override suspend fun predictAdmission(request: com.campusverse.app.data.model.PredictionRequest) = Result.failure<com.campusverse.app.data.model.AdmissionPredictionItem>(Exception())
        override suspend fun getPredictionHistory() = Result.success(emptyList<com.campusverse.app.data.model.AdmissionPredictionItem>())
        override suspend fun getAiRecommendations(query: String, mode: String) = Result.failure<com.campusverse.app.data.model.AiStudyQueryResponse>(Exception())
    }
}
