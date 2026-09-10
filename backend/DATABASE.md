# CampusVerse Database Schema Documentation

## Database Overview
* **Engine**: PostgreSQL (Production) / SQLite (Portable Local Dev & Automated Tests)
* **ORM**: Prisma Client v5.22.0
* **Schema Location**: `backend/prisma/schema.prisma`
* **Models Total**: 40 relational entities with foreign keys, indexes, and cascades.

---

## Entity Relationship Overview

```
User (1) ──┬── (1) Profile ──┬── (0..1) AspirantProfile
           │                 ├── (0..1) StudentProfile ── (N..1) Institution
           │                 ├── (0..1) AlumniProfile  ── (N..1) Institution
           │                 └── (0..1) AdminProfile
           ├── (1) PrivacySettings
           ├── (1) SecuritySettings
           ├── (0..1) MentorProfile ── (1..N) MentorshipRequest ── (1..N) MentorshipSession
           ├── (1..N) Verification (User / Reviewer)
           ├── (1..N) Event (Organizer) / EventRegistration (Participant)
           ├── (1..N) Community (Creator) / CommunityMember / Post / Comment
           ├── (1..N) MarketplaceItem (Seller) / MarketplaceTransaction
           ├── (1..N) ConversationParticipant ── (N..1) Conversation ── (1..N) Message
           ├── (1..N) Job (Poster) ── (1..N) JobApplication (Applicant)
           ├── (1..N) Referral (Alumni -> Student)
           ├── (1..N) Notification
           ├── (1..N) Announcement (Author)
           ├── (1..N) Report (Reporter / Reviewer)
           ├── (1..N) CareerRoadmap / SkillProgress / InterviewSession
           ├── (1..N) AIRecommendation
           └── (1..N) AuditLog (Actor)
```

---

## Complete Entity Catalog

### 1. Identity & Core Roles
* **`User`**: Core authentication record (`id`, `email` [unique], `passwordHash`, `role` [ASPIRANT/STUDENT/ALUMNI/ADMIN], `isEmailVerified`, `isAdminAuthorized`, `isActive`, timestamps).
* **`Profile`**: User bio and contact info (`userId` [FK, unique, cascade], `fullName`, `headline`, `bio`, `avatarUrl`, `phone`, `location`, `website`, `github`, `linkedin`).
* **`AspirantProfile`**: Aspirant-specific info (`profileId` [FK, cascade], `targetDegree`, `targetMajor`, `targetUniversities`, `highSchool`, `expectedGradYear`, `entranceExamScores`).
* **`StudentProfile`**: Student enrolled details (`profileId` [FK, cascade], `institutionId` [FK], `studentIdNumber`, `degree`, `major`, `semester`, `cgpa`, `graduationYear`).
* **`AlumniProfile`**: Alumni career & mentorship details (`profileId` [FK, cascade], `institutionId` [FK], `degree`, `graduationYear`, `currentCompany`, `currentDesignation`, `industry`, `yearsOfExperience`, `willingToMentor`, `willingToRefer`).
* **`AdminProfile`**: Admin staff details (`profileId` [FK, cascade], `department`, `accessLevel`, `employeeId`).
* **`Verification`**: Institutional verification document submissions (`userId` [FK], `documentType`, `documentUrl`, `status` [PENDING/APPROVED/REJECTED], `reviewerId` [FK], `rejectionReason`, `submittedAt`, `reviewedAt`).

### 2. Academics & Institutions
* **`Institution`**: Colleges and universities (`id`, `name`, `code` [unique], `domain`, `address`, `city`, `state`, `country`, `verified`).
* **`Course`**: Campus course catalog (`institutionId` [FK], `code`, `name`, `department`, `credits`, `semester`). Unique constraint on `[institutionId, code]`.
* **`Note`**: Peer study notes & shared study resources (`userId` [FK], `courseId` [FK], `title`, `description`, `fileUrl`, `tags`, `isPublic`, `downloadsCount`).
* **`LibraryItem`**: Campus library resources and books (`institutionId` [FK], `title`, `author`, `isbn`, `category`, `totalCopies`, `availableCopies`, `location`).

### 3. Events & Campus Life
* **`Event`**: Campus hackathons, webinars, workshops, cultural fests (`organizerId` [FK], `institutionId` [FK], `title`, `description`, `category`, `location`, `isOnline`, `meetingUrl`, `startTime`, `endTime`, `capacity`, `registeredCount`, `status`).
* **`EventRegistration`**: Student registrations for campus events (`eventId` [FK], `userId` [FK], `registeredAt`, `attended`). Unique constraint on `[eventId, userId]`.

### 4. Communities & Discussions
* **`Community`**: Interest & departmental student hubs (`id`, `name`, `slug` [unique], `description`, `coverImage`, `icon`, `isPrivate`, `memberCount`, `creatorId` [FK]).
* **`CommunityMember`**: Membership roles (`communityId` [FK], `userId` [FK], `role` [MEMBER/MODERATOR/ADMIN], `joinedAt`). Unique on `[communityId, userId]`.
* **`CommunityPost`**: Forum threads & technical discussions (`communityId` [FK], `authorId` [FK], `title`, `content`, `mediaUrls`, `likesCount`, `commentsCount`).
* **`CommunityComment`**: Nested replies to community posts (`postId` [FK], `authorId` [FK], `content`, `parentId` [FK, recursive reply relation]).

### 5. Marketplace & Campus Commerce
* **`MarketplaceItem`**: Peer-to-peer item listings (`sellerId` [FK], `title`, `description`, `price`, `category` [TEXTBOOK/ELECTRONICS/NOTES/FURNITURE/OTHER], `condition` [NEW/LIKE_NEW/GOOD/FAIR], `images`, `status` [AVAILABLE/RESERVED/SOLD]).
* **`MarketplaceTransaction`**: Order & purchase records (`itemId` [FK], `buyerId` [FK], `sellerId` [FK], `amount`, `status`, `transactionDate`).

### 6. Mentorship & Professional Networking
* **`MentorProfile`**: Alumni mentor directory (`userId` [FK, unique], `title`, `company`, `expertise`, `hourlyRate`, `maxMentees`, `isAcceptingMentees`, `bio`, `rating`, `reviewsCount`).
* **`MentorshipRequest`**: Mentee requests (`mentorId` [FK], `menteeId` [FK], `goal`, `message`, `status` [PENDING/ACCEPTED/DECLINED/COMPLETED], `requestedAt`, `respondedAt`).
* **`MentorshipSession`**: Scheduled 1:1 sessions (`requestId` [FK], `scheduledAt`, `durationMinutes`, `meetingUrl`, `notes`, `status` [SCHEDULED/COMPLETED/CANCELLED]).

### 7. Direct Messaging
* **`Conversation`**: 1-on-1 or group chat threads (`id`, `isGroup`, `title`, `createdAt`, `updatedAt`).
* **`ConversationParticipant`**: Members in conversation (`conversationId` [FK], `userId` [FK], `joinedAt`, `lastReadAt`). Unique on `[conversationId, userId]`.
* **`Message`**: Chat messages with media (`conversationId` [FK], `senderId` [FK], `content`, `mediaUrl`, `isRead`).

### 8. Jobs, Careers & Referrals
* **`Company`**: Hiring organizations (`name` [unique], `logoUrl`, `website`, `industry`, `description`, `verified`).
* **`Job`**: Campus placements & external job openings (`companyId` [FK], `posterId` [FK], `title`, `description`, `roleType` [FULL_TIME/PART_TIME/INTERNSHIP/CONTRACT], `location`, `isRemote`, `salaryRange`, `requirements`, `status` [ACTIVE/CLOSED], `deadline`).
* **`JobApplication`**: Student job applications (`jobId` [FK], `applicantId` [FK], `resumeUrl`, `coverLetter`, `status`). Unique on `[jobId, applicantId]`.
* **`Referral`**: Alumni employee referral requests (`alumniId` [FK], `studentId` [FK], `jobId` [FK], `companyName`, `status` [REQUESTED/SUBMITTED/REJECTED/HIRED], `notes`).

### 9. Engagement, Safety & Audit Trail
* **`Notification`**: Real-time push & in-app alerts (`userId` [FK], `type`, `title`, `message`, `data`, `isRead`).
* **`Announcement`**: Campus-wide broadcasts (`authorId` [FK], `title`, `content`, `targetRole`, `priority`, `isPublished`).
* **`Report`**: Content & user moderation flags (`reporterId` [FK], `targetType`, `targetId`, `reason`, `status`, `resolutionNotes`, `reviewerId` [FK]).
* **`AuditLog`**: Security and administrative actions (`actorId` [FK], `action`, `targetType`, `targetId`, `metadata`, `timestamp`).

### 10. AI Career Growth & Settings
* **`CareerRoadmap`**: Goal tracking milestones (`userId` [FK], `title`, `targetRole`, `milestones`, `progressPercentage`).
* **`SkillProgress`**: Verified technical skills (`userId` [FK], `skillName`, `category`, `level`, `verified`, `assessmentScore`). Unique on `[userId, skillName]`.
* **`InterviewSession`**: AI mock interview transcripts and scores (`userId` [FK], `roleTarget`, `topic`, `durationMinutes`, `feedbackScore`, `transcript`, `strengths`, `improvements`).
* **`AIRecommendation`**: Personalized recommendation engine scores (`userId` [FK], `type`, `referenceId`, `score`, `reasoning`).
* **`PrivacySettings` / `SecuritySettings` / `AccountRecovery`**: User preferences and 2FA flags.

---

## Database Constraints & Indexing Strategy
1. **Foreign Key Enforcement**: All child models use `@relation(fields: [...], references: [...], onDelete: Cascade / SetNull)`.
2. **Search Indexing**:
   - `User`: `@@index([email])`, `@@index([role])`
   - `Job`: `@@index([companyId])`, `@@index([posterId])`, `@@index([status])`
   - `Event`: `@@index([organizerId])`, `@@index([status])`, `@@index([startTime])`
   - `MarketplaceItem`: `@@index([sellerId])`, `@@index([category])`, `@@index([status])`
   - `CommunityPost`: `@@index([communityId])`, `@@index([authorId])`, `@@index([createdAt])`
   - `AuditLog`: `@@index([actorId])`, `@@index([action])`, `@@index([timestamp])`
3. **Compound Unique Constraints**:
   - `EventRegistration`: `@@unique([eventId, userId])`
   - `JobApplication`: `@@unique([jobId, applicantId])`
   - `CommunityMember`: `@@unique([communityId, userId])`
   - `ConversationParticipant`: `@@unique([conversationId, userId])`
   - `SkillProgress`: `@@unique([userId, skillName])`
   - `Course`: `@@unique([institutionId, code])`
