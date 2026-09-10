package com.campusverse.app.domain.alumni

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
import com.campusverse.app.data.model.SecuritySettingsData
import com.campusverse.app.data.model.SkillItem

/**
 * Interface defining all data operations for the Alumni Module.
 */
interface AlumniRepository {

    // 1. Alumni Home
    suspend fun getAlumniHomeSummary(): Result<AlumniHomeSummary>

    fun clearCache() {}

    // 2. Alumni Profile
    suspend fun getMyProfile(): Result<AlumniProfileData>
    suspend fun getAlumniProfileById(id: String): Result<AlumniProfileData>
    suspend fun updateAlumniProfile(profile: AlumniProfileData): Result<AlumniProfileData>

    // 3. Alumni Network & Connections
    suspend fun getAlumni(
        search: String? = null,
        company: String? = null,
        industry: String? = null,
        graduationYear: Int? = null,
        willingToMentor: Boolean? = null,
        willingToRefer: Boolean? = null
    ): Result<List<AlumniProfileData>>
    suspend fun getConnections(): Result<Pair<List<AlumniConnectionItem>, List<AlumniConnectionRequestItem>>>
    suspend fun connectWithAlumni(alumniId: String): Result<Boolean>
    suspend fun respondToConnectionRequest(connectionId: String, status: String): Result<Boolean>
    suspend fun deleteConnection(connectionId: String): Result<Boolean>
    suspend fun saveAlumni(alumniId: String): Result<Boolean>
    suspend fun unsaveAlumni(alumniId: String): Result<Boolean>
    suspend fun getSavedAlumni(): Result<List<AlumniProfileData>>
    suspend fun getNetworkActivity(): Result<List<NetworkActivityItem>>

    // 4. Careers & Opportunities
    suspend fun getJobs(search: String? = null, roleType: String? = null, isRemote: Boolean? = null, companyId: String? = null): Result<List<JobOpportunity>>
    suspend fun getRecommendedJobs(): Result<List<JobOpportunity>>
    suspend fun getJobById(id: String): Result<JobOpportunity>
    suspend fun saveJob(jobId: String): Result<Boolean>
    suspend fun unsaveJob(jobId: String): Result<Boolean>
    suspend fun getSavedJobs(): Result<List<JobOpportunity>>
    suspend fun applyForJob(jobId: String, resumeUrl: String, coverLetter: String?): Result<JobApplicationItem>
    suspend fun getApplications(): Result<List<JobApplicationItem>>
    suspend fun withdrawApplication(applicationId: String): Result<Boolean>

    // 5. Companies
    suspend fun getCompanies(search: String? = null): Result<List<CompanyItem>>
    suspend fun getCompanyById(id: String): Result<CompanyItem>
    suspend fun getCompanyJobs(companyId: String): Result<List<JobOpportunity>>

    // 6. Referrals
    suspend fun getReferrals(): Result<List<ReferralItem>>
    suspend fun requestReferral(alumniId: String, jobId: String?, companyName: String, notes: String?): Result<ReferralItem>
    suspend fun updateReferralStatus(referralId: String, status: String, notes: String?): Result<Boolean>

    // 7. Mentorship
    suspend fun getMentors(search: String? = null, expertise: String? = null): Result<List<MentorItem>>
    suspend fun getMentorById(mentorId: String): Result<MentorItem>
    suspend fun requestMentorship(mentorId: String, goal: String, message: String): Result<MentorshipRequestItem>
    suspend fun getMentorshipRequests(): Result<List<MentorshipRequestItem>>
    suspend fun respondMentorshipRequest(requestId: String, status: String): Result<Boolean>
    suspend fun getMentorshipSessions(): Result<List<MentorshipSessionItem>>
    suspend fun getMentorshipSessionById(sessionId: String): Result<MentorshipSessionItem>
    suspend fun updateMentorshipSession(sessionId: String, status: String?, notes: String?): Result<Boolean>

    // 8. Events & Networking
    suspend fun getEvents(search: String? = null, category: String? = null): Result<List<CampusEvent>>
    suspend fun getEventById(id: String): Result<CampusEvent>
    suspend fun registerForEvent(eventId: String): Result<Boolean>
    suspend fun unregisterFromEvent(eventId: String): Result<Boolean>

    // 9. Career Development & AI
    suspend fun askAiCareerAssistant(query: String, mode: String, topic: String? = null): Result<AiStudyQueryResponse>
    suspend fun getRoadmaps(): Result<List<CareerRoadmapItem>>
    suspend fun createRoadmap(title: String, targetRole: String, milestones: List<String>): Result<CareerRoadmapItem>
    suspend fun updateRoadmapMilestone(roadmapId: String, milestoneId: String, completed: Boolean): Result<Boolean>
    suspend fun deleteRoadmap(roadmapId: String): Result<Boolean>
    suspend fun getSkills(): Result<List<SkillItem>>
    suspend fun upsertSkill(skillName: String, category: String, level: String): Result<SkillItem>
    suspend fun deleteSkill(skillId: String): Result<Boolean>
    suspend fun getInterviews(): Result<List<MockInterviewSessionItem>>
    suspend fun getInterviewById(interviewId: String): Result<MockInterviewSessionItem>
    suspend fun saveInterviewSession(session: MockInterviewSessionItem): Result<MockInterviewSessionItem>
    suspend fun getCareerPreferences(): Result<CareerPreferenceData>
    suspend fun updateCareerPreferences(preferences: CareerPreferenceData): Result<CareerPreferenceData>

    // 10. Messaging & Conversations
    suspend fun getConversations(search: String? = null): Result<List<ConversationItem>>
    suspend fun getMessages(conversationId: String): Result<List<ChatMessageItem>>
    suspend fun startConversation(recipientUserId: String, initialMessage: String): Result<ConversationItem>
    suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String? = null): Result<ChatMessageItem>
    suspend fun deleteMessage(messageId: String): Result<Boolean>
    suspend fun reportUser(userId: String, reason: String): Result<Boolean>
    suspend fun blockUser(userId: String): Result<Boolean>

    // 11. Notifications
    suspend fun getNotifications(): Result<List<AlumniNotificationItem>>
    suspend fun markNotificationRead(notificationId: String): Result<Boolean>
    suspend fun markAllNotificationsRead(): Result<Boolean>

    // 12. Settings, Privacy & Security
    suspend fun getPrivacySettings(): Result<PrivacySettingsData>
    suspend fun updatePrivacySettings(settings: PrivacySettingsData): Result<PrivacySettingsData>
    suspend fun getSecuritySettings(): Result<SecuritySettingsData>
    suspend fun updateSecuritySettings(settings: SecuritySettingsData): Result<SecuritySettingsData>
    suspend fun setAccountRecoveryEmail(email: String): Result<Boolean>
}
