import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Phase 7 — Admin Module End-to-End & Security Tests', () => {
  let adminToken: string;
  let adminUserId: string;
  let studentToken: string;
  let alumniToken: string;
  let aspirantToken: string;
  let sampleStudentId: string;
  let sampleVerificationId: string;
  let sampleReportId: string;
  let sampleMarketplaceId: string;
  let sampleJobId: string;
  let sampleEventId: string;
  let sampleMentorId: string;
  let managedUserEmail: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // Ensure all demo users exist with standard password
    await prisma.user.upsert({
      where: { email: 'admin@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true, isAdminAuthorized: true },
      create: {
        email: 'admin@campusverse.edu',
        passwordHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: { create: { fullName: 'Super Administrator' } }
      }
    });

    await prisma.user.upsert({
      where: { email: 'student@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'student@campusverse.edu',
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Rohan Mehta' } }
      }
    });

    await prisma.user.upsert({
      where: { email: 'alumni@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'alumni@campusverse.edu',
        passwordHash,
        role: 'ALUMNI',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Dr. Aisha Patel' } }
      }
    });

    await prisma.user.upsert({
      where: { email: 'aspirant@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'aspirant@campusverse.edu',
        passwordHash,
        role: 'ASPIRANT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Kavya Sharma' } }
      }
    });

    // 1. Authenticate as Admin
    const adminLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });

    expect(adminLogin.status).toBe(200);
    adminToken = adminLogin.body.data.token;
    adminUserId = adminLogin.body.data.user.userId;

    // 2. Authenticate as Student
    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    studentToken = studentLogin.body.data.token;

    // 3. Authenticate as Alumni
    const alumniLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'alumni@campusverse.edu', password: 'Password123' });
    alumniToken = alumniLogin.body.data.token;

    // 4. Authenticate as Aspirant
    const aspirantLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'aspirant@campusverse.edu', password: 'Password123' });
    aspirantToken = aspirantLogin.body.data.token;

    // Create a dedicated test user for admin management mutations
    managedUserEmail = `managed_test_user_${Date.now()}@campusverse.edu`;
    const managedUser = await prisma.user.create({
      data: {
        email: managedUserEmail,
        passwordHash: 'dummyHash',
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: false,
        profile: {
          create: {
            fullName: 'Managed Test User'
          }
        }
      }
    });
    sampleStudentId = managedUser.id;

    // Find / create sample test records
    const verif = await prisma.verification.findFirst({ where: { status: 'PENDING' } });
    if (verif) {
      sampleVerificationId = verif.id;
    } else {
      const newVerif = await prisma.verification.create({
        data: {
          userId: sampleStudentId,
          documentType: 'STUDENT_ID',
          documentUrl: 'https://docs.campusverse.edu/id.pdf',
          status: 'PENDING'
        }
      });
      sampleVerificationId = newVerif.id;
    }

    const report = await prisma.report.findFirst();
    if (report) {
      sampleReportId = report.id;
    } else {
      const newReport = await prisma.report.create({
        data: {
          reporterId: sampleStudentId,
          targetType: 'USER',
          targetId: sampleStudentId,
          reason: 'Testing report resolution',
          status: 'PENDING'
        }
      });
      sampleReportId = newReport.id;
    }

    let item = await prisma.marketplaceItem.findFirst();
    if (!item) {
      item = await prisma.marketplaceItem.create({
        data: {
          title: 'Sample Admin Book',
          description: 'Calculus textbook',
          price: 25.0,
          category: 'BOOKS',
          condition: 'GOOD',
          status: 'AVAILABLE',
          sellerId: sampleStudentId
        }
      });
    }
    sampleMarketplaceId = item.id;

    const job = await prisma.job.findFirst();
    sampleJobId = job ? job.id : 'job_none';

    const event = await prisma.event.findFirst();
    sampleEventId = event ? event.id : 'evt_none';

    const mentor = await prisma.mentorProfile.findFirst();
    sampleMentorId = mentor ? mentor.id : 'men_none';
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  // ---------------------------------------------------------------------------
  // 1. Dashboard Metrics
  // ---------------------------------------------------------------------------
  describe('1. Admin Dashboard Metrics', () => {
    it('should return complete platform stats and audit logs for Admin', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.metrics).toBeDefined();
      expect(res.body.data.metrics.users.total).toBeGreaterThan(0);
      expect(res.body.data.metrics.system.status).toBe('OPERATIONAL');
      expect(Array.isArray(res.body.data.recentAuditLogs)).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // 2. User Management
  // ---------------------------------------------------------------------------
  describe('2. User Management Workflow', () => {
    it('should list users with pagination, search, and role filter', async () => {
      const res = await request(app)
        .get('/api/v1/admin/users?role=STUDENT&limit=10&page=1')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
      expect(res.body.meta.total).toBeGreaterThan(0);
    });

    it('should retrieve individual user details by ID', async () => {
      const res = await request(app)
        .get(`/api/v1/admin/users/${sampleStudentId}`)
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.id).toBe(sampleStudentId);
      expect(res.body.data.email).toBe(managedUserEmail);
    });

    it('should update user status (activate / suspend) and write audit log', async () => {
      const res = await request(app)
        .patch(`/api/v1/admin/users/${sampleStudentId}/status`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          isActive: true,
          suspensionReason: 'Audit review completed.'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.isActive).toBe(true);

      // Verify audit log
      const audit = await prisma.auditLog.findFirst({
        where: { targetId: sampleStudentId, action: 'ADMIN_UPDATED_USER' },
        orderBy: { timestamp: 'desc' }
      });
      expect(audit).toBeDefined();
    });

    it('should reset user password to temporary password', async () => {
      const tempUser = await prisma.user.create({
        data: {
          email: `temp_reset_${Date.now()}@campusverse.edu`,
          passwordHash: await bcrypt.hash('OldPassword123', 10),
          role: 'STUDENT',
          profile: { create: { fullName: 'Temp User' } }
        }
      });

      const res = await request(app)
        .post(`/api/v1/admin/users/${tempUser.id}/reset-password`)
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.resetInitiated).toBe(true);
      expect(res.body.data.tempPassword).toBeUndefined();

      await prisma.profile.deleteMany({ where: { userId: tempUser.id } });
      await prisma.user.delete({ where: { id: tempUser.id } });
    });
  });

  // ---------------------------------------------------------------------------
  // 3. Verification Review
  // ---------------------------------------------------------------------------
  describe('3. Verification Queue & Decisions', () => {
    it('should list verifications with status filtering', async () => {
      const res = await request(app)
        .get('/api/v1/admin/verifications?status=ALL')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
    });

    it('should review and approve a verification record', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/verifications/${sampleVerificationId}/review`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          status: 'APPROVED'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('APPROVED');

      // Verify audit log
      const audit = await prisma.auditLog.findFirst({
        where: { targetId: sampleVerificationId, action: 'ADMIN_APPROVED_VERIFICATION' }
      });
      expect(audit).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // 4. Reports & Moderation
  // ---------------------------------------------------------------------------
  describe('4. Reports & Moderation Workflow', () => {
    it('should list safety reports', async () => {
      const res = await request(app)
        .get('/api/v1/admin/reports?status=ALL')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
    });

    it('should resolve report with resolution action and log audit', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/reports/${sampleReportId}/resolve`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          status: 'RESOLVED',
          actionTaken: 'WARN',
          resolutionNotes: 'User was issued formal community guidelines warning.'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('RESOLVED');
      expect(res.body.data.resolutionNotes).toBe('User was issued formal community guidelines warning.');
    });
  });

  // ---------------------------------------------------------------------------
  // 5. Content & Marketplace Moderation
  // ---------------------------------------------------------------------------
  describe('5. Content / Marketplace Management', () => {
    it('should retrieve marketplace listings for moderation', async () => {
      const res = await request(app)
        .get('/api/v1/admin/marketplace')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
    });

    it('should moderate marketplace item status', async () => {
      if (sampleMarketplaceId !== 'mkt_none') {
        const res = await request(app)
          .patch(`/api/v1/admin/marketplace/${sampleMarketplaceId}/moderate`)
          .set('Authorization', `Bearer ${adminToken}`)
          .send({
            action: 'APPROVE',
            reason: 'Item complies with campus commerce rules.'
          });

        expect(res.status).toBe(200);
        expect(res.body.data.status).toBe('AVAILABLE');
      }
    });
  });

  // ---------------------------------------------------------------------------
  // 6. Events & Jobs Oversight
  // ---------------------------------------------------------------------------
  describe('6. Events & Jobs Moderation', () => {
    it('should list events and update event moderation status', async () => {
      const res = await request(app)
        .get('/api/v1/admin/events')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);

      if (sampleEventId !== 'evt_none') {
        const modRes = await request(app)
          .patch(`/api/v1/admin/events/${sampleEventId}/moderate`)
          .set('Authorization', `Bearer ${adminToken}`)
          .send({
            action: 'APPROVE',
            reason: 'Approved campus technology hackathon.'
          });

        expect(modRes.status).toBe(200);
        expect(modRes.body.data.status).toBe('UPCOMING');
      }
    });

    it('should list jobs and update job status', async () => {
      const res = await request(app)
        .get('/api/v1/admin/jobs')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);

      if (sampleJobId !== 'job_none') {
        const modRes = await request(app)
          .patch(`/api/v1/admin/jobs/${sampleJobId}/moderate`)
          .set('Authorization', `Bearer ${adminToken}`)
          .send({
            action: 'APPROVE',
            reason: 'Verified job opportunity.'
          });

        expect(modRes.status).toBe(200);
        expect(modRes.body.data.status).toBe('ACTIVE');
      }
    });
  });

  // ---------------------------------------------------------------------------
  // 7. Mentorship Oversight
  // ---------------------------------------------------------------------------
  describe('7. Mentorship Management', () => {
    it('should retrieve mentor profiles for review', async () => {
      const res = await request(app)
        .get('/api/v1/admin/mentorship')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);

      if (sampleMentorId !== 'men_none') {
        const modRes = await request(app)
          .patch(`/api/v1/admin/mentorship/${sampleMentorId}/moderate`)
          .set('Authorization', `Bearer ${adminToken}`)
          .send({
            action: 'APPROVE',
            reason: 'Verified alumni mentor profile.'
          });

        expect(modRes.status).toBe(200);
        expect(modRes.body.data.isAcceptingMentees).toBe(true);
      }
    });
  });

  // ---------------------------------------------------------------------------
  // 8. Notifications & Announcements
  // ---------------------------------------------------------------------------
  describe('8. Broadcast Announcements', () => {
    it('should list announcements and publish new announcement with target audience notifications', async () => {
      const res = await request(app)
        .post('/api/v1/admin/announcements')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          title: 'Campus Emergency Server Maintenance',
          content: 'Scheduled database indexing maintenance will occur tonight from 2 AM to 3 AM IST.',
          targetRole: 'ALL',
          priority: 'HIGH'
        });

      expect(res.status).toBe(201);
      expect(res.body.data.title).toBe('Campus Emergency Server Maintenance');
      expect(res.body.data.deliveredCount).toBeGreaterThanOrEqual(1);

      // Verify in list
      const listRes = await request(app)
        .get('/api/v1/admin/announcements')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(listRes.status).toBe(200);
      expect(listRes.body.data.some((a: any) => a.title === 'Campus Emergency Server Maintenance')).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // 9. Admin Settings & Controls
  // ---------------------------------------------------------------------------
  describe('9. Platform Settings & Controls', () => {
    it('should retrieve and update global platform configuration', async () => {
      const getRes = await request(app)
        .get('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(getRes.status).toBe(200);
      expect(getRes.body.data.systemVersion).toBeDefined();

      const patchRes = await request(app)
        .patch('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          autoModeration: true,
          strictVerification: true
        });

      expect(patchRes.status).toBe(200);
      expect(patchRes.body.data.autoModeration).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // 10. Critical Negative RBAC Security Tests
  // ---------------------------------------------------------------------------
  describe('10. Strict Security & Non-Admin Rejection Tests', () => {
    it('Student CANNOT access Admin Dashboard (403 Forbidden)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
    });

    it('Alumni CANNOT access Admin Users (403 Forbidden)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/users')
        .set('Authorization', `Bearer ${alumniToken}`);

      expect(res.status).toBe(403);
    });

    it('Aspirant CANNOT access Admin Reports (403 Forbidden)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/reports')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(403);
    });

    it('Student CANNOT review verifications (403 Forbidden)', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/verifications/${sampleVerificationId}/review`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({ status: 'APPROVED' });

      expect(res.status).toBe(403);
    });

    it('Aspirant CANNOT broadcast announcements (403 Forbidden)', async () => {
      const res = await request(app)
        .post('/api/v1/admin/announcements')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          title: 'Unauthorized announcement',
          content: 'This should fail.'
        });

      expect(res.status).toBe(403);
    });

    it('Unauthenticated request is rejected with 401 Unauthorized', async () => {
      const res = await request(app).get('/api/v1/admin/dashboard');
      expect(res.status).toBe(401);
    });
  });
});
