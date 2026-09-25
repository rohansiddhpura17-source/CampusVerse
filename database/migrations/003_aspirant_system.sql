-- =============================================================================
-- 003_ASPIRANT_SYSTEM.SQL
-- =============================================================================

-- CreateTable
CREATE TABLE "CollegeProgram" (
    "id" TEXT NOT NULL,
    "institutionId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "degree" TEXT NOT NULL,
    "major" TEXT NOT NULL,
    "durationYears" DOUBLE PRECISION NOT NULL DEFAULT 4.0,
    "tuitionFee" TEXT NOT NULL,
    "minGpa" DOUBLE PRECISION,
    "entranceExams" TEXT,
    "deadline" TEXT,
    "overview" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CollegeProgram_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "CollegeProgram_institutionId_idx" ON "CollegeProgram"("institutionId");

CREATE INDEX "CollegeProgram_degree_idx" ON "CollegeProgram"("degree");

-- CreateTable
CREATE TABLE "SavedCollege" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "institutionId" TEXT NOT NULL,
    "savedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SavedCollege_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "SavedCollege_userId_idx" ON "SavedCollege"("userId");

CREATE INDEX "SavedCollege_institutionId_idx" ON "SavedCollege"("institutionId");

CREATE UNIQUE INDEX "SavedCollege_userId_institutionId_key" ON "SavedCollege"("userId", "institutionId");

-- CreateTable
CREATE TABLE "CollegePreference" (
    "id" TEXT NOT NULL,
    "aspirantId" TEXT NOT NULL,
    "stream" TEXT,
    "preferredLocations" TEXT,
    "maxBudget" TEXT,
    "targetDegree" TEXT,
    "targetUniversities" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CollegePreference_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "CollegePreference_aspirantId_key" ON "CollegePreference"("aspirantId");

CREATE INDEX "CollegePreference_aspirantId_idx" ON "CollegePreference"("aspirantId");

-- CreateTable
CREATE TABLE "CollegeRecommendation" (
    "id" TEXT NOT NULL,
    "aspirantId" TEXT NOT NULL,
    "institutionId" TEXT NOT NULL,
    "matchScore" DOUBLE PRECISION NOT NULL,
    "reasoning" TEXT NOT NULL,
    "fitCategory" TEXT NOT NULL DEFAULT 'BEST_FIT',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CollegeRecommendation_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "CollegeRecommendation_aspirantId_idx" ON "CollegeRecommendation"("aspirantId");

CREATE INDEX "CollegeRecommendation_institutionId_idx" ON "CollegeRecommendation"("institutionId");

CREATE UNIQUE INDEX "CollegeRecommendation_aspirantId_institutionId_key" ON "CollegeRecommendation"("aspirantId", "institutionId");

-- CreateTable
CREATE TABLE "CollegeApplication" (
    "id" TEXT NOT NULL,
    "aspirantId" TEXT NOT NULL,
    "institutionId" TEXT NOT NULL,
    "programName" TEXT NOT NULL,
    "degree" TEXT NOT NULL DEFAULT 'B.TECH',
    "status" TEXT NOT NULL DEFAULT 'SUBMITTED',
    "applicationFee" DOUBLE PRECISION,
    "documentsUrl" TEXT,
    "notes" TEXT,
    "applicationDate" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CollegeApplication_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "CollegeApplication_aspirantId_idx" ON "CollegeApplication"("aspirantId");

CREATE INDEX "CollegeApplication_institutionId_idx" ON "CollegeApplication"("institutionId");

CREATE INDEX "CollegeApplication_status_idx" ON "CollegeApplication"("status");

-- CreateTable
CREATE TABLE "EntranceExam" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "conductingBody" TEXT,
    "examDate" TIMESTAMP(3),
    "maxScore" DOUBLE PRECISION NOT NULL DEFAULT 300.0,
    "description" TEXT,
    "websiteUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "EntranceExam_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "EntranceExam_name_key" ON "EntranceExam"("name");

CREATE UNIQUE INDEX "EntranceExam_code_key" ON "EntranceExam"("code");

CREATE INDEX "EntranceExam_code_idx" ON "EntranceExam"("code");

-- CreateTable
CREATE TABLE "Scholarship" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "provider" TEXT NOT NULL,
    "amount" TEXT NOT NULL,
    "deadline" TEXT NOT NULL,
    "eligibility" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "requirements" TEXT,
    "applicationUrl" TEXT,
    "category" TEXT NOT NULL DEFAULT 'MERIT',
    "country" TEXT NOT NULL DEFAULT 'India',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Scholarship_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "Scholarship_category_idx" ON "Scholarship"("category");

CREATE INDEX "Scholarship_country_idx" ON "Scholarship"("country");

-- CreateTable
CREATE TABLE "SavedScholarship" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "scholarshipId" TEXT NOT NULL,
    "savedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SavedScholarship_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "SavedScholarship_userId_idx" ON "SavedScholarship"("userId");

CREATE INDEX "SavedScholarship_scholarshipId_idx" ON "SavedScholarship"("scholarshipId");

CREATE UNIQUE INDEX "SavedScholarship_userId_scholarshipId_key" ON "SavedScholarship"("userId", "scholarshipId");

-- CreateTable
CREATE TABLE "ScholarshipApplication" (
    "id" TEXT NOT NULL,
    "scholarshipId" TEXT NOT NULL,
    "applicantId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'SUBMITTED',
    "statementOfPurpose" TEXT,
    "documentsUrl" TEXT,
    "appliedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "reviewedAt" TIMESTAMP(3),
    "reviewNotes" TEXT,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ScholarshipApplication_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "ScholarshipApplication_scholarshipId_idx" ON "ScholarshipApplication"("scholarshipId");

CREATE INDEX "ScholarshipApplication_applicantId_idx" ON "ScholarshipApplication"("applicantId");

CREATE INDEX "ScholarshipApplication_status_idx" ON "ScholarshipApplication"("status");

CREATE UNIQUE INDEX "ScholarshipApplication_scholarshipId_applicantId_key" ON "ScholarshipApplication"("scholarshipId", "applicantId");

-- CreateTable
CREATE TABLE "AdmissionPrediction" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "institutionId" TEXT,
    "institutionName" TEXT NOT NULL,
    "programName" TEXT NOT NULL,
    "degree" TEXT NOT NULL DEFAULT 'B.TECH',
    "gpa" DOUBLE PRECISION NOT NULL,
    "testType" TEXT NOT NULL,
    "testScore" DOUBLE PRECISION NOT NULL,
    "predictionPercentage" DOUBLE PRECISION NOT NULL,
    "qualificationStatus" TEXT NOT NULL,
    "feedback" TEXT NOT NULL,
    "recommendations" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AdmissionPrediction_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "AdmissionPrediction_userId_idx" ON "AdmissionPrediction"("userId");

CREATE INDEX "AdmissionPrediction_institutionId_idx" ON "AdmissionPrediction"("institutionId");

ALTER TABLE "CollegeProgram" ADD CONSTRAINT "CollegeProgram_institutionId_fkey" FOREIGN KEY ("institutionId") REFERENCES "Institution"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SavedCollege" ADD CONSTRAINT "SavedCollege_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SavedCollege" ADD CONSTRAINT "SavedCollege_institutionId_fkey" FOREIGN KEY ("institutionId") REFERENCES "Institution"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CollegePreference" ADD CONSTRAINT "CollegePreference_aspirantId_fkey" FOREIGN KEY ("aspirantId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CollegeRecommendation" ADD CONSTRAINT "CollegeRecommendation_aspirantId_fkey" FOREIGN KEY ("aspirantId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CollegeRecommendation" ADD CONSTRAINT "CollegeRecommendation_institutionId_fkey" FOREIGN KEY ("institutionId") REFERENCES "Institution"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CollegeApplication" ADD CONSTRAINT "CollegeApplication_aspirantId_fkey" FOREIGN KEY ("aspirantId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CollegeApplication" ADD CONSTRAINT "CollegeApplication_institutionId_fkey" FOREIGN KEY ("institutionId") REFERENCES "Institution"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SavedScholarship" ADD CONSTRAINT "SavedScholarship_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SavedScholarship" ADD CONSTRAINT "SavedScholarship_scholarshipId_fkey" FOREIGN KEY ("scholarshipId") REFERENCES "Scholarship"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ScholarshipApplication" ADD CONSTRAINT "ScholarshipApplication_scholarshipId_fkey" FOREIGN KEY ("scholarshipId") REFERENCES "Scholarship"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ScholarshipApplication" ADD CONSTRAINT "ScholarshipApplication_applicantId_fkey" FOREIGN KEY ("applicantId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "AdmissionPrediction" ADD CONSTRAINT "AdmissionPrediction_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "AdmissionPrediction" ADD CONSTRAINT "AdmissionPrediction_institutionId_fkey" FOREIGN KEY ("institutionId") REFERENCES "Institution"("id") ON DELETE SET NULL ON UPDATE CASCADE;
