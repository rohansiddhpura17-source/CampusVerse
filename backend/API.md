# CampusVerse REST API Contract Documentation

**Base URL**: `http://localhost:4000/api/v1` (or `http://10.0.2.2:4000/api/v1` in Android Emulator)  
**Standard Header**: `Authorization: Bearer <JWT_TOKEN>`

---

## 1. Authentication Endpoints

### `POST /auth/register`
* **Access**: Public
* **Request Body**:
  ```json
  {
    "name": "Aarav Sharma",
    "email": "student@campusverse.edu",
    "password": "Password123",
    "role": "STUDENT" // ASPIRANT | STUDENT | ALUMNI | ADMIN
  }
  ```
* **Response (201 Created)**:
  ```json
  {
    "success": true,
    "message": "Account registered successfully. Please verify your email.",
    "data": {
      "token": "eyJhbGciOi...",
      "user": {
        "userId": "usr_uuid",
        "email": "student@campusverse.edu",
        "name": "Aarav Sharma",
        "role": "STUDENT",
        "isEmailVerified": false,
        "isAdminAuthorized": false
      }
    }
  }
  ```

### `POST /auth/login`
* **Access**: Public
* **Request Body**:
  ```json
  {
    "email": "student@campusverse.edu",
    "password": "Password123"
  }
  ```
* **Response (200 OK)**:
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "token": "eyJhbGciOi...",
      "user": {
        "userId": "usr_uuid",
        "email": "student@campusverse.edu",
        "name": "Aarav Sharma",
        "role": "STUDENT",
        "isEmailVerified": true,
        "isAdminAuthorized": false
      }
    }
  }
  ```

### `GET /auth/me`
* **Access**: Authenticated (Bearer Token)
* **Response (200 OK)**: Current authenticated user details and profile.

---

## 2. Users & Profiles

### `GET /users/:id`
* **Access**: Authenticated
* **Response (200 OK)**: User profile details and role-specific subprofile.

### `PATCH /users/:id`
* **Access**: Authenticated (Owner or ADMIN)
* **Request Body**: Partial profile fields (`fullName`, `headline`, `bio`, `location`, `website`, `github`, `linkedin`).

---

## 3. Verification

### `GET /verifications`
* **Access**: **ADMIN only**
* **Query Params**: `status` (`PENDING` | `APPROVED` | `REJECTED`), `page`, `limit`
* **Response (200 OK)**: Paginated verification queue.

### `POST /verifications`
* **Access**: Authenticated (`STUDENT`, `ALUMNI`)
* **Request Body**:
  ```json
  {
    "documentType": "STUDENT_ID",
    "documentUrl": "https://docs.campusverse.edu/id.pdf"
  }
  ```

### `PATCH /verifications/:id`
* **Access**: **ADMIN only**
* **Request Body**: `{ "status": "APPROVED" }` or `{ "status": "REJECTED", "rejectionReason": "Blurry document" }`

---

## 4. Jobs & Applications

### `GET /jobs`
* **Access**: Authenticated
* **Query Params**: `search`, `roleType`, `isRemote`, `page`, `limit`
* **Response (200 OK)**: Paginated active job listings with company metadata.

### `POST /jobs`
* **Access**: `ALUMNI`, `ADMIN`
* **Request Body**: Job creation details (`companyId`, `title`, `description`, `roleType`, `location`, `isRemote`, `salaryRange`).

### `POST /jobs/:id/apply`
* **Access**: `STUDENT`, `ALUMNI`
* **Request Body**: `{ "resumeUrl": "https://docs.campusverse.edu/resume.pdf", "coverLetter": "..." }`

---

## 5. Mentorship & Sessions

### `GET /mentors`
* **Access**: Authenticated
* **Query Params**: `search`, `expertise`, `page`, `limit`
* **Response (200 OK)**: Available alumni mentor profiles and ratings.

### `POST /mentorship/requests`
* **Access**: `ASPIRANT`, `STUDENT`
* **Request Body**: `{ "mentorId": "...", "goal": "Resume Review", "message": "..." }`

### `PATCH /mentorship/requests/:id`
* **Access**: Mentor Owner / `ADMIN`
* **Request Body**: `{ "status": "ACCEPTED" }` (auto-schedules kickoff session).

---

## 6. Campus Marketplace

### `GET /marketplace`
* **Access**: Authenticated
* **Query Params**: `search`, `category`, `condition`, `minPrice`, `maxPrice`, `status`, `page`, `limit`

### `POST /marketplace`
* **Access**: `STUDENT`, `ALUMNI`, `ADMIN`
* **Request Body**: `{ "title": "Casio FX-991EX", "description": "...", "price": 650, "category": "ELECTRONICS", "condition": "LIKE_NEW" }`

### `PATCH /marketplace/:id` / `DELETE /marketplace/:id`
* **Access**: Item Owner or `ADMIN`

---

## 7. Safety Reports & Moderation

### `POST /reports`
* **Access**: Authenticated
* **Request Body**: `{ "targetType": "POST", "targetId": "...", "reason": "Spam" }`

### `GET /reports/:id`
* **Access**: Reporter or `ADMIN`

---

## 8. Admin Operations (Strict RBAC - ADMIN Only)

* `GET /admin/dashboard`: Platform metrics, user role breakdowns, pending counts, and audit logs.
* `GET /admin/users`: User management search & role inspection.
* `GET /admin/verifications`: Review queue.
* `GET /admin/reports`: Moderation queue.
* `GET /admin/marketplace`: Listings oversight.
* `GET /admin/events`: Campus events oversight.
* `GET /admin/jobs`: Job postings oversight.
* `GET /admin/mentorship`: Mentorship program oversight.
* `POST /admin/announcements`: Publish campus-wide alert with priority.

---

## Standard Error Response Schema

```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN", // UNAUTHORIZED | VALIDATION_ERROR | NOT_FOUND | EMAIL_EXISTS | ...
    "message": "Access denied. Role 'STUDENT' is not authorized for this resource.",
    "details": []
  }
}
```
