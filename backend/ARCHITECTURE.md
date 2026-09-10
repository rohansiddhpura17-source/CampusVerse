# CampusVerse Architecture & System Design

## 1. System Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│               CampusVerse Android Client               │
│    (Jetpack Compose • MVI • StateFlow • Navigation)    │
└───────────────────────────┬────────────────────────────┘
                            │ HTTPS / REST (JSON)
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Backend API Layer                    │
│            (Node.js • TypeScript • Express)            │
├────────────────────────────────────────────────────────┤
│ Middlewares:                                           │
│  ├── JWT Auth Middleware (Bearer Token Verification)   │
│  ├── RBAC Middleware (requireRole, requireAdmin)       │
│  ├── Zod Request Payload & Query Validation            │
│  ├── Audit Logging Middleware                          │
│  └── Global Sanitized Error Handler (No stack traces)  │
├────────────────────────────────────────────────────────┤
│ Service & Controller Layer:                            │
│  ├── Auth & User Lifecycle Services                    │
│  ├── Mentorship & Job Application Services             │
│  ├── Marketplace & Event Processing                    │
│  └── Admin Operations & Safety Moderation              │
├────────────────────────────────────────────────────────┤
│ Data Access Layer:                                     │
│  └── Prisma ORM (40 Relational Models, Indexes, FKs)   │
└───────────────────────────┬────────────────────────────┘
                            │ SQL Queries
                            ▼
┌────────────────────────────────────────────────────────┐
│                    Database Engine                     │
│    (PostgreSQL Production • SQLite Local Test/Dev)     │
└────────────────────────────────────────────────────────┘
```

---

## 2. Core Security & RBAC Principles

1. **Zero Client Trust**: The backend never accepts role claims from request headers or query bodies. The user's role is read directly from verified database state and signed JWT tokens.
2. **Strict Admin Verification Gate**:
   - `UserRole.ADMIN` registration always initializes with `isAdminAuthorized = false`.
   - Admin routes (`/api/v1/admin/*`) require both `role === 'ADMIN'` and `isAdminAuthorized === true`.
   - Student, Alumni, and Aspirant roles receive strict `403 Forbidden` responses.
3. **Password Security**:
   - Passwords hashed using Bcrypt with 10 salt rounds (backend) and PBKDF2WithHmacSHA256 (Android local fallback).
   - Plaintext passwords are never logged, persisted, or returned in API responses.
4. **Audit Trail**:
   - Administrative and moderation actions (verifications, listing removals, user actions, announcements) create immutable records in `AuditLog`.

---

## 3. Local Setup & Reproduction Commands

### Prerequisites
* Node.js >= 18 (Tested on Node.js v24)
* npm >= 9

### Step-by-Step Backend Commands

```bash
# 1. Navigate to backend directory
cd backend

# 2. Install dependencies
npm install

# 3. Push database schema to create tables & relations
npx prisma db push

# 4. Generate Prisma Client
npx prisma generate

# 5. Seed development data (4 roles + demo data)
npm run prisma:seed

# 6. Run automated test suite
npm test

# 7. Start backend API server
npm run dev
```
Server will be live at `http://localhost:4000/api/v1`.
