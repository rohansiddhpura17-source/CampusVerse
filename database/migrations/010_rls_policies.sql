-- =============================================================================
-- CAMPUSVERSE SUPABASE ROW LEVEL SECURITY (RLS) POLICIES
-- =============================================================================
-- All tables enforce strict RLS.
-- - Authenticated users access their own private rows (via auth.uid()::text = userId).
-- - Public users can only read explicitly public records (e.g. published notes, active jobs, active events).
-- - Platform admins (role = 'ADMIN' with isAdminAuthorized = true) have full administrative access.
-- =============================================================================

-- Enable RLS across all core tables
ALTER TABLE "User" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Profile" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "StudentProfile" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AspirantProfile" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AlumniProfile" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AdminProfile" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AcademicRecord" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Enrollment" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Grade" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "StudentSkill" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Project" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "ProjectMember" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Achievement" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Certification" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "CollegePreference" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "CollegeRecommendation" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "CollegeApplication" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "ScholarshipApplication" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "EmploymentRecord" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "HigherEducationRecord" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AlumniAchievement" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "EventRegistration" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "MentorshipRequest" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "MentorshipReview" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Notification" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "NotificationPreference" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AIChatSession" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AIChatMessage" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "CareerReadinessScore" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Document" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "AuditLog" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "Report" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "ModerationRecord" ENABLE ROW LEVEL SECURITY;

-- 1. USER & PROFILE POLICIES
CREATE POLICY "users_read_own" ON "User"
  FOR SELECT USING (auth.uid()::text = id OR EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN' AND u."isAdminAuthorized" = true
  ));

CREATE POLICY "users_update_own" ON "User"
  FOR UPDATE USING (auth.uid()::text = id);

CREATE POLICY "profiles_read_public" ON "Profile"
  FOR SELECT USING (true);

CREATE POLICY "profiles_modify_own" ON "Profile"
  FOR ALL USING (auth.uid()::text = "userId");

-- 2. STUDENT ACADEMIC POLICIES
CREATE POLICY "student_academic_records_own" ON "AcademicRecord"
  FOR SELECT USING (auth.uid()::text = "studentId" OR EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN'
  ));

CREATE POLICY "enrollments_own" ON "Enrollment"
  FOR ALL USING (auth.uid()::text = "studentId" OR EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN'
  ));

CREATE POLICY "grades_student_own" ON "Grade"
  FOR SELECT USING (EXISTS (
    SELECT 1 FROM "Enrollment" e WHERE e.id = "enrollmentId" AND (e."studentId" = auth.uid()::text OR EXISTS (
      SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN'
    ))
  ));

-- 3. PROJECTS & PORTFOLIO POLICIES
CREATE POLICY "projects_read_published" ON "Project"
  FOR SELECT USING ("isPublished" = true OR "userId" = auth.uid()::text);

CREATE POLICY "projects_manage_own" ON "Project"
  FOR ALL USING (auth.uid()::text = "userId");

-- 4. ASPIRANT POLICIES
CREATE POLICY "aspirant_preferences_own" ON "CollegePreference"
  FOR ALL USING (auth.uid()::text = "aspirantId");

CREATE POLICY "aspirant_recommendations_own" ON "CollegeRecommendation"
  FOR SELECT USING (auth.uid()::text = "aspirantId");

CREATE POLICY "aspirant_applications_own" ON "CollegeApplication"
  FOR ALL USING (auth.uid()::text = "aspirantId" OR EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN'
  ));

-- 5. SCHOLARSHIP APPLICATIONS
CREATE POLICY "scholarship_applications_own" ON "ScholarshipApplication"
  FOR ALL USING (auth.uid()::text = "applicantId" OR EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN'
  ));

-- 6. ALUMNI RECORDS POLICIES
CREATE POLICY "employment_records_read" ON "EmploymentRecord"
  FOR SELECT USING (true);

CREATE POLICY "employment_records_manage_own" ON "EmploymentRecord"
  FOR ALL USING (auth.uid()::text = "alumniId");

-- 7. NOTIFICATIONS & PREFERENCES
CREATE POLICY "notifications_own" ON "Notification"
  FOR ALL USING (auth.uid()::text = "userId");

CREATE POLICY "notification_preferences_own" ON "NotificationPreference"
  FOR ALL USING (auth.uid()::text = "userId");

-- 8. AI SESSIONS & CHAT POLICIES
CREATE POLICY "ai_sessions_own" ON "AIChatSession"
  FOR ALL USING (auth.uid()::text = "userId");

CREATE POLICY "ai_messages_own" ON "AIChatMessage"
  FOR ALL USING (EXISTS (
    SELECT 1 FROM "AIChatSession" s WHERE s.id = "sessionId" AND s."userId" = auth.uid()::text
  ));

CREATE POLICY "career_readiness_own" ON "CareerReadinessScore"
  FOR SELECT USING (auth.uid()::text = "userId");

-- 9. DOCUMENTS POLICIES
CREATE POLICY "documents_manage_own" ON "Document"
  FOR ALL USING (auth.uid()::text = "userId" OR EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN'
  ));

-- 10. AUDIT & MODERATION POLICIES (Admins only)
CREATE POLICY "audit_logs_admin_only" ON "AuditLog"
  FOR SELECT USING (EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN' AND u."isAdminAuthorized" = true
  ));

CREATE POLICY "moderation_records_admin_only" ON "ModerationRecord"
  FOR ALL USING (EXISTS (
    SELECT 1 FROM "User" u WHERE u.id = auth.uid()::text AND u.role = 'ADMIN' AND u."isAdminAuthorized" = true
  ));
