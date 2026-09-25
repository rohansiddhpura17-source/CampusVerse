# CampusVerse — Production Admin Platform Architecture

## 1. Executive Architecture Overview

The CampusVerse Administration Platform provides authoritative, centralized, and role-guarded governance across all ecosystem entities (students, aspirants, alumni, colleges, courses, scholarships, projects, campus events, mentorship, moderation, and system settings).

```
Next.js Web Client (/admin/*)
         │
         ▼ (Bearer JWT, Strict CORS, JSON Envelope)
Express REST API (/api/v1/admin/*)
         │
         ├─── requireAuth (Token verification, active account assertion)
         ├─── requireAdmin (Admin elevation check)
         ├─── requirePermission (Granular RBAC: UserRole -> Role -> RolePermission -> Permission)
         ├─── Zod Input Validation & Sanitization
         ├─── Audit Logging (Actor, Action, Target, Old/New State)
         │
         ▼
Prisma ORM Service Layer
         │
         ▼ (SSL Pooler Connection)
Supabase PostgreSQL 15 (AWS Mumbai ap-south-1)
         ├─── Row Level Security (RLS) Active
         └─── Dual-Compatible RBAC Schema
```

---

## 2. Granular Role-Based Access Control (RBAC)

### 2.1 Role Hierarchy & Responsibilities

| Role | System Name | Scope & Authority |
| :--- | :--- | :--- |
| **Super Administrator** | `SUPER_ADMIN` | Unrestricted authority over all records, global settings, and sensitive audit records. Bypasses granular permission checks. |
| **Platform Administrator** | `ADMIN` | General day-to-day operations: user directory, campus events, university moderation, content review. |
| **Safety Moderator** | `MODERATOR` | Content moderation, user reports, flagged marketplace listings, community posts, and safety resolutions. |
| **Curriculum & Content** | `CONTENT_MANAGER` | Institutional management: colleges, courses, subjects, scholarships, events, and projects. |
| **Student Support** | `SUPPORT_ADMIN` | Identity verifications, student/aspirant records, admissions tracking, and alumni support. |
| **Analytics & Telemetry**| `ANALYTICS_ADMIN` | Read-only business intelligence, user growth analytics, and audit log inspection. |

### 2.2 Dual-Compatibility Design
To prevent any disruption to the native **Android Application** (`CampusVerse/app/`) or existing web sessions:
- The base `User.role` column (`STUDENT`, `ASPIRANT`, `ALUMNI`, `ADMIN`) and `User.isAdminAuthorized` are completely preserved.
- Granular permissions are evaluated via relational RBAC models: `UserRole` -> `Role` -> `RolePermission` -> `Permission`.
- Existing admin accounts are seeded with the `SUPER_ADMIN` role and assigned all permissions.

---

## 3. Database Schema Extensions

The following models were added to `backend/prisma/schema.prisma` and deployed to PostgreSQL:

```prisma
model Role {
  id          String           @id @default(uuid())
  name        String           @unique
  description String?
  isSystem    Boolean          @default(false)
  createdAt   DateTime         @default(now())
  updatedAt   DateTime         @updatedAt

  userRoles   UserRole[]
  permissions RolePermission[]
}

model Permission {
  id          String           @id @default(uuid())
  name        String           @unique
  module      String
  description String?
  createdAt   DateTime         @default(now())

  roles       RolePermission[]
}

model UserRole {
  id           String    @id @default(uuid())
  userId       String
  user         User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  roleId       String
  role         Role      @relation(fields: [roleId], references: [id], onDelete: Cascade)
  assignedAt   DateTime  @default(now())
  assignedById String?
  assignedBy   User?     @relation("RoleAssigner", fields: [assignedById], references: [id], onDelete: SetNull)

  @@unique([userId, roleId])
}

model RolePermission {
  id           String     @id @default(uuid())
  roleId       String
  role         Role       @relation(fields: [roleId], references: [id], onDelete: Cascade)
  permissionId String
  permission   Permission @relation(fields: [permissionId], references: [id], onDelete: Cascade)
  grantedAt    DateTime   @default(now())

  @@unique([roleId, permissionId])
}

model FeatureFlag {
  id          String   @id @default(uuid())
  key         String   @unique
  name        String
  description String?
  isEnabled   Boolean  @default(false)
  targetRoles String?
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt
}
```

---

## 4. API Endpoints Specification

All endpoints are mounted under `/api/v1/admin` and require valid JWT Bearer authentication.

| Method | Endpoint | Required Permission | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/dashboard` | `analytics.read` | Telemetry KPIs, pending counts, and recent audit logs |
| `GET` | `/analytics` | `analytics.read` | Real-time BI, AI utilization, role distribution, 7-day trend |
| `GET` | `/users` | `users.read` | Server-side paginated user listing with search and status filters |
| `GET` | `/users/:id` | `users.read` | Full user detail profile, role data, and activity |
| `PATCH` | `/users/:id` | `users.update` | Update user status, role, verification, or profile details |
| `POST` | `/users/:id/suspend` | `users.suspend` | Instant user deactivation with audit log entry |
| `GET` | `/students` | `students.read` | Student directory with degree, major, semester, and GPA |
| `GET` | `/aspirants` | `aspirants.read` | Admissions pipeline with target programs and entrance scores |
| `GET` | `/alumni` | `alumni.read` | Graduate network with company, designation, and referral flags |
| `GET` | `/colleges` | `colleges.read` | Universities and institutions catalog with NIRF ranking |
| `POST` | `/colleges` | `colleges.create` | Create new college/institution with code and location |
| `PATCH` | `/colleges/:id` | `colleges.update` | Update institutional details and accreditation standing |
| `DELETE`| `/colleges/:id` | `colleges.delete` | Delete institution (blocked if students/alumni are attached) |
| `GET` | `/courses` | `courses.read` | Departmental courses, syllabi, and credit values |
| `GET` | `/scholarships` | `scholarships.read` | Grants, endowments, criteria, and applicant counters |
| `GET` | `/projects` | `projects.read` | Student project showcases and tech stack tags |
| `GET` | `/events` | `events.read` | Campus events, schedules, organizers, and attendee counts |
| `PATCH` | `/events/:id/moderate` | `events.update` | Approve, cancel, or flag events |
| `GET` | `/reports` | `moderation.read` | Content and user safety reports queue |
| `PATCH` | `/reports/:id` | `moderation.resolve` | Resolve report with disciplinary action note |
| `POST` | `/moderation/:id/resolve` | `moderation.resolve` | Unified moderation action across notes, posts, or marketplace |
| `GET` | `/audit-logs` | `audit.read` | Paginated, filterable immutable audit records |
| `GET` | `/settings` | `settings.manage` | Platform operational flags and maintenance mode |
| `PATCH` | `/settings` | `settings.manage` | Update platform flags, backup timestamps, and security rules |

---

## 5. Security & Audit Logging Principles

1. **Zero Client Trust**: The frontend NEVER decides access privileges. Client-side route guards provide visual redirection, while the Express API strictly validates tokens and permissions on every HTTP call.
2. **Audit Logging**: Every administrative mutation (`ADMIN_SUSPENDED_USER`, `ADMIN_UPDATED_USER`, `ADMIN_CREATED_COLLEGE`, `ADMIN_RESOLVED_REPORT`, etc.) writes an audit record capturing:
   - `actorId` (operator who executed the mutation)
   - `action` (semantic action identifier)
   - `targetType` & `targetId` (resource modified)
   - `metadata` (JSON payload containing previous and new states)
3. **No Secret Leakage**: Passwords, tokens, database connection strings, and API keys are strictly excluded from audit logs and API responses.
4. **Server-Side Pagination**: Every list endpoint (`/users`, `/students`, `/colleges`, `/audit-logs`) applies server-side `skip`/`take` pagination, ensuring memory and bandwidth efficiency.

---

## 6. Frontend Module Implementations

Located in `CampusVerse-Website/app/(app)/admin/`:

- **Executive Telemetry**: `dashboard/page.tsx` — Real-time metrics, AI telemetry card, role breakdown, and module hub.
- **User Directory**: `users/page.tsx` — Server-side search, suspension modal, role elevation, password reset.
- **Student Cohort**: `students/page.tsx` — Degree, major, GPA, semester, verified badges.
- **Aspirants Queue**: `aspirants/page.tsx` — Target majors, entrance exam scores, expected graduation year.
- **Alumni Directory**: `alumni/page.tsx` — Company, title, sector, mentorship availability, and referral offers.
- **Colleges & Universities**: `colleges/page.tsx` — Full CRUD for `Institution` with modal forms and dependency checks.
- **Courses**: `courses/page.tsx` — Catalog of courses, credits, and participating colleges.
- **Scholarships**: `scholarships/page.tsx` — Endowments, application review queues, grant values.
- **Projects**: `projects/page.tsx` — Repository showcases, tech stack tags, team collaboration counters.
- **Audit Trail**: `audit-logs/page.tsx` — Filterable audit table with JSON state inspection modal.
