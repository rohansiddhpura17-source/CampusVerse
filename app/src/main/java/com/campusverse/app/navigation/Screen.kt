package com.campusverse.app.navigation

/**
 * Type-safe navigation routes for CampusVerse.
 */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Welcome : Screen("welcome")
    data object Login : Screen("login")
    data object CreateAccount : Screen("create_account")
    data object RoleSelection : Screen("role_selection")
    data object Verification : Screen("verification")
    data object VerificationSuccess : Screen("verification_success")
    data object ForgotPassword : Screen("forgot_password")
    data object ResetPassword : Screen("reset_password")

    // Student Module Screens
    data object StudentHome : Screen("student_home")
    data object Academics : Screen("student_academics")
    data object NotesHub : Screen("student_notes")
    data object Library : Screen("student_library")
    data object AiStudyAssistant : Screen("student_ai_assistant")
    data object Events : Screen("student_events")
    data object CommunityList : Screen("student_communities")
    data object CommunityDetail : Screen("student_community_detail/{communityId}") {
        fun createRoute(communityId: String) = "student_community_detail/$communityId"
    }
    data object Marketplace : Screen("student_marketplace")
    data object StudentProfile : Screen("student_profile")
    data object StudentSettings : Screen("student_settings")

    // Alumni Module Screens (Core, Careers, Mentorship, Events, Career Dev, Messaging, Account, Settings, Legal)
    data object AlumniHome : Screen("alumni_home")
    data object AlumniNetwork : Screen("alumni_network")
    data object AlumniConnections : Screen("alumni_connections")
    data object AlumniProfileDetail : Screen("alumni_profile_detail/{alumniId}") {
        fun createRoute(alumniId: String) = "alumni_profile_detail/$alumniId"
    }

    // Careers
    data object AlumniCareers : Screen("alumni_careers")
    data object AlumniJobDetails : Screen("alumni_job_details/{jobId}") {
        fun createRoute(jobId: String) = "alumni_job_details/$jobId"
    }
    data object AlumniSavedJobs : Screen("alumni_saved_jobs")
    data object AlumniApplicationTracking : Screen("alumni_applications")
    data object AlumniReferrals : Screen("alumni_referrals")
    data object AlumniCompanyProfile : Screen("alumni_company_profile/{companyId}") {
        fun createRoute(companyId: String) = "alumni_company_profile/$companyId"
    }
    data object AlumniCompanyJobs : Screen("alumni_company_jobs/{companyId}") {
        fun createRoute(companyId: String) = "alumni_company_jobs/$companyId"
    }

    // Mentorship
    data object AlumniMentorship : Screen("alumni_mentorship")
    data object AlumniMentorProfile : Screen("alumni_mentor_profile/{mentorId}") {
        fun createRoute(mentorId: String) = "alumni_mentor_profile/$mentorId"
    }
    data object AlumniMentorshipRequests : Screen("alumni_mentorship_requests")
    data object AlumniSessionDetails : Screen("alumni_session_details/{sessionId}") {
        fun createRoute(sessionId: String) = "alumni_session_details/$sessionId"
    }

    // Events
    data object AlumniEvents : Screen("alumni_events")

    // Career Development
    data object AlumniAiCareerAssistant : Screen("alumni_ai_career_assistant")
    data object AlumniCareerRoadmap : Screen("alumni_career_roadmap")
    data object AlumniSkillDevelopment : Screen("alumni_skill_development")
    data object AlumniInterviewPrep : Screen("alumni_interview_prep")
    data object AlumniMockInterview : Screen("alumni_mock_interview")

    // Messaging
    data object AlumniMessages : Screen("alumni_messages")
    data object AlumniChat : Screen("alumni_chat/{conversationId}") {
        fun createRoute(conversationId: String) = "alumni_chat/$conversationId"
    }

    // Account & Settings
    data object AlumniProfile : Screen("alumni_profile")
    data object AlumniSettings : Screen("alumni_settings")
    data object AlumniCareerPreferences : Screen("alumni_career_preferences")
    data object AlumniNotifications : Screen("alumni_notifications")
    data object AlumniPrivacySettings : Screen("alumni_privacy_settings")
    data object AlumniSecuritySettings : Screen("alumni_security_settings")
    data object AlumniAccountRecovery : Screen("alumni_account_recovery")

    // Help & Legal
    data object AlumniHelpSupport : Screen("alumni_help_support")
    data object AlumniAbout : Screen("alumni_about")
    data object AlumniTerms : Screen("alumni_terms")
    data object AlumniPrivacyPolicy : Screen("alumni_privacy_policy")
    data object AlumniCommunityGuidelines : Screen("alumni_community_guidelines")

    // Aspirant Module Screens (Core, Colleges, Predictor, Scholarships, AI, Profile, Settings)
    data object AspirantHome : Screen("aspirant_home")
    data object AspirantCollegeExplorer : Screen("aspirant_colleges")
    data object AspirantCollegeComparison : Screen("aspirant_college_comparison")
    data object AspirantPredictor : Screen("aspirant_predictor")
    data object AspirantScholarships : Screen("aspirant_scholarships")
    data object AspirantSavedScholarships : Screen("aspirant_saved_scholarships")
    data object AspirantAiRecommendations : Screen("aspirant_ai_recommendations")
    data object AspirantProfile : Screen("aspirant_profile")
    data object AspirantSettings : Screen("aspirant_settings")
    data object AspirantNotifications : Screen("aspirant_notifications")

    // Admin Module Screens
    data object AdminLogin : Screen("admin_login")
    data object AdminDashboard : Screen("admin_dashboard")
    data object AdminUserManagement : Screen("admin_users")
    data object AdminVerification : Screen("admin_verification")
    data object AdminReports : Screen("admin_reports")
    data object AdminMarketplace : Screen("admin_marketplace")
    data object AdminEvents : Screen("admin_events")
    data object AdminJobs : Screen("admin_jobs")
    data object AdminMentorship : Screen("admin_mentorship")
    data object AdminAnnouncements : Screen("admin_announcements")
    data object AdminSettings : Screen("admin_settings")
    data object AdminProfile : Screen("admin_profile")
    data object AdminNotes : Screen("admin_notes")
    data object AdminFinance : Screen("admin_finance")

    // Payment & Store Screens
    data object Store : Screen("store")
    data object MyTransactions : Screen("my_transactions")
    data object MyEntitlements : Screen("my_entitlements")
}

