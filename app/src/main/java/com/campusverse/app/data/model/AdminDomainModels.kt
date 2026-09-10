package com.campusverse.app.data.model

data class AdminDashboardMetrics(
    val totalUsers: Int = 0,
    val studentsCount: Int = 0,
    val alumniCount: Int = 0,
    val aspirantsCount: Int = 0,
    val pendingVerifications: Int = 0,
    val activeJobs: Int = 0,
    val pendingJobs: Int = 0,
    val activeMarketplaceListings: Int = 0,
    val pendingReports: Int = 0,
    val totalEvents: Int = 0,
    val systemStatus: String = "OPERATIONAL",
    val databaseUptime: String = "99.98%",
    val activeSessions: Int = 142,
    val securityAlerts: Int = 0
)

data class AuditLogItem(
    val id: String,
    val actorEmail: String,
    val actorName: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val timestamp: String,
    val metadata: String? = null
)

data class AdminDashboardData(
    val metrics: AdminDashboardMetrics,
    val recentAuditLogs: List<AuditLogItem> = emptyList()
)

data class AdminUserItem(
    val id: String,
    val email: String,
    val fullName: String,
    val role: String,
    val isActive: Boolean,
    val isEmailVerified: Boolean,
    val avatarUrl: String? = null,
    val headline: String? = null,
    val location: String? = null,
    val createdAt: String,
    val verificationStatus: String? = null
)

data class AdminVerificationItem(
    val id: String,
    val userId: String,
    val userEmail: String,
    val userName: String,
    val userRole: String,
    val documentType: String,
    val documentUrl: String,
    val status: String,
    val submittedAt: String,
    val rejectionReason: String? = null,
    val reviewerName: String? = null
)

data class AdminReportItem(
    val id: String,
    val reporterId: String,
    val reporterName: String,
    val reporterEmail: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val status: String,
    val resolutionNotes: String? = null,
    val reviewerName: String? = null,
    val createdAt: String
)

data class AdminMarketplaceItem(
    val id: String,
    val title: String,
    val price: Double,
    val category: String,
    val status: String,
    val sellerName: String,
    val sellerEmail: String,
    val createdAt: String
)

data class AdminEventItem(
    val id: String,
    val title: String,
    val eventType: String,
    val date: String,
    val location: String,
    val status: String,
    val organizerName: String,
    val organizerEmail: String
)

data class AdminJobItem(
    val id: String,
    val title: String,
    val companyName: String,
    val jobType: String,
    val location: String,
    val status: String,
    val posterName: String,
    val posterEmail: String
)

data class AdminMentorItem(
    val id: String,
    val userId: String,
    val mentorName: String,
    val mentorEmail: String,
    val title: String,
    val company: String,
    val expertise: String,
    val isAcceptingMentees: Boolean,
    val requestsCount: Int = 0,
    val rating: Double = 5.0
)

data class AdminAnnouncementItem(
    val id: String,
    val title: String,
    val content: String,
    val targetRole: String? = null,
    val priority: String = "NORMAL",
    val authorName: String,
    val createdAt: String,
    val deliveredCount: Int = 0
)

data class AdminPlatformSettingsData(
    val maintenanceMode: Boolean = false,
    val allowNewRegistrations: Boolean = true,
    val autoModeration: Boolean = true,
    val strictVerification: Boolean = true,
    val require2FAForAdmins: Boolean = true,
    val systemVersion: String = "2.4.0-phase7",
    val lastBackup: String = "2026-08-28T10:00:00Z"
)
