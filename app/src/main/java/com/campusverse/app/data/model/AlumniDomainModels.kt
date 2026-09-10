package com.campusverse.app.data.model

/**
 * Domain models for the Alumni module of CampusVerse.
 */

data class AlumniProfileData(
    val userId: String,
    val fullName: String,
    val email: String,
    val role: String = "ALUMNI",
    val headline: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val location: String? = null,
    val phone: String? = null,
    val linkedin: String? = null,
    val website: String? = null,
    val github: String? = null,
    val company: String = "Technology Company",
    val designation: String = "Senior Software Engineer",
    val industry: String = "Technology & Cloud",
    val yearsOfExperience: Int = 5,
    val degree: String = "B.Tech Computer Science",
    val graduationYear: Int = 2021,
    val institution: String = "National Institute of Technology",
    val willingToMentor: Boolean = true,
    val willingToRefer: Boolean = true,
    val skills: List<String> = emptyList(),
    val connectionStatus: String = "NONE", // NONE, PENDING, RECEIVED, ACCEPTED, DECLINED
    val connectionId: String? = null,
    val mutualConnectionsCount: Int = 0,
    val isSaved: Boolean = false,
    val isSelf: Boolean = false
)

data class AlumniConnectionItem(
    val connectionId: String,
    val userId: String,
    val email: String,
    val fullName: String,
    val headline: String? = null,
    val avatarUrl: String? = null,
    val company: String? = null,
    val designation: String? = null,
    val connectedAt: String? = null
)

data class AlumniConnectionRequestItem(
    val connectionId: String,
    val userId: String,
    val email: String,
    val fullName: String,
    val headline: String? = null,
    val avatarUrl: String? = null,
    val company: String? = null,
    val designation: String? = null,
    val requestedAt: String? = null
)

data class NetworkActivityItem(
    val id: String,
    val type: String, // CONNECTION, JOB, MENTORSHIP
    val title: String,
    val description: String,
    val timestamp: String? = null
)

data class JobOpportunity(
    val id: String,
    val companyId: String,
    val companyName: String,
    val companyLogoUrl: String? = null,
    val title: String,
    val description: String,
    val roleType: String = "FULL_TIME", // FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT
    val location: String,
    val isRemote: Boolean = false,
    val salaryRange: String? = null,
    val requirements: String? = null,
    val posterName: String = "Alumni Recruiter",
    val isSaved: Boolean = false,
    val hasApplied: Boolean = false,
    val applicationStatus: String? = null, // APPLIED, SHORTLISTED, INTERVIEWING, OFFERED, REJECTED, WITHDRAWN
    val createdAt: String? = null
)

data class JobApplicationItem(
    val id: String,
    val jobId: String,
    val jobTitle: String,
    val companyName: String,
    val location: String,
    val roleType: String,
    val status: String = "APPLIED", // APPLIED, SHORTLISTED, INTERVIEWING, OFFERED, REJECTED, WITHDRAWN
    val resumeUrl: String,
    val coverLetter: String? = null,
    val appliedAt: String? = null
)

data class CompanyItem(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val website: String? = null,
    val industry: String? = null,
    val description: String? = null,
    val openJobsCount: Int = 0,
    val verified: Boolean = true,
    val jobs: List<JobOpportunity> = emptyList()
)

data class ReferralItem(
    val id: String,
    val alumniId: String,
    val alumniName: String,
    val studentId: String,
    val studentName: String,
    val jobId: String? = null,
    val jobTitle: String? = null,
    val companyName: String,
    val status: String = "REQUESTED", // REQUESTED, SUBMITTED, REJECTED, HIRED
    val notes: String? = null,
    val isAlumniOwner: Boolean = false,
    val createdAt: String? = null
)

data class MentorItem(
    val id: String,
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val headline: String? = null,
    val title: String,
    val company: String,
    val expertise: List<String> = emptyList(),
    val isAcceptingMentees: Boolean = true,
    val maxMentees: Int = 5,
    val rating: Double = 5.0,
    val reviewsCount: Int = 0,
    val bio: String? = null
)

data class MentorshipRequestItem(
    val id: String,
    val mentorId: String,
    val mentorName: String,
    val menteeId: String,
    val menteeName: String,
    val goal: String,
    val message: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED, CANCELLED
    val isMentor: Boolean = false,
    val requestedAt: String? = null
)

data class MentorshipSessionItem(
    val id: String,
    val requestId: String,
    val mentorName: String,
    val menteeName: String,
    val scheduledAt: String,
    val durationMinutes: Int = 45,
    val meetingUrl: String? = null,
    val notes: String? = null,
    val status: String = "SCHEDULED" // SCHEDULED, COMPLETED, CANCELLED
)

data class CareerRoadmapItem(
    val id: String,
    val title: String,
    val targetRole: String,
    val milestones: List<RoadmapMilestone> = emptyList(),
    val progressPercentage: Double = 0.0,
    val updatedAt: String? = null
)

data class RoadmapMilestone(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val targetQuarter: String? = null
)

data class SkillItem(
    val id: String,
    val skillName: String,
    val category: String = "TECHNICAL",
    val level: String = "INTERMEDIATE", // BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    val verified: Boolean = true,
    val assessmentScore: Double = 85.0
)

data class MockInterviewSessionItem(
    val id: String,
    val roleTarget: String,
    val topic: String,
    val durationMinutes: Int = 45,
    val overallScore: Double = 90.0,
    val technicalScore: Double = 92.0,
    val behavioralScore: Double = 88.0,
    val systemDesignScore: Double = 91.0,
    val communicationScore: Double = 89.0,
    val transcript: String? = null,
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val completedAt: String? = null
)

data class CareerPreferenceData(
    val id: String = "",
    val preferredRoles: List<String> = emptyList(),
    val preferredLocations: List<String> = emptyList(),
    val targetSalary: String = "Competitive",
    val remotePreference: String = "HYBRID", // REMOTE, HYBRID, ONSITE, ANY
    val industries: List<String> = emptyList(),
    val jobAlerts: Boolean = true
)

data class ConversationItem(
    val id: String,
    val title: String,
    val otherParticipantId: String,
    val otherParticipantName: String,
    val otherParticipantAvatar: String? = null,
    val otherParticipantHeadline: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0
)

data class ChatMessageItem(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val mediaUrl: String? = null,
    val isRead: Boolean = false,
    val isMine: Boolean = false,
    val createdAt: String? = null
)

data class AlumniNotificationItem(
    val id: String,
    val type: String, // CONNECTION, APPLICATION, MENTORSHIP, MESSAGE, EVENT, SYSTEM
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val createdAt: String? = null
)

data class PrivacySettingsData(
    val showEmail: Boolean = false,
    val showPhone: Boolean = true,
    val showGpa: Boolean = false,
    val allowMessagesFrom: String = "ALL", // ALL, CONNECTIONS_ONLY, NOBODY
    val allowMentorshipRequests: Boolean = true
)

data class SecuritySettingsData(
    val twoFactorEnabled: Boolean = false,
    val loginAlertsEnabled: Boolean = true,
    val lastPasswordChange: String? = null
)

data class AlumniHomeSummary(
    val profile: AlumniProfileData,
    val connectionsCount: Int = 3,
    val pendingRequestsCount: Int = 1,
    val activeMentorshipCount: Int = 1,
    val recommendedJobs: List<JobOpportunity> = emptyList(),
    val upcomingEvents: List<CampusEvent> = emptyList(),
    val networkActivities: List<NetworkActivityItem> = emptyList()
)
