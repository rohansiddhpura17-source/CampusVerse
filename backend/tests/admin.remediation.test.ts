import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';
import { emailService } from '../src/services/email.service';

describe('Admin Remediation — Persistent Settings & Secure Password Reset', () => {
  let adminToken: string;
  let studentToken: string;
  let testUserId: string;
  const testUserEmail = `remediation_test_${Date.now()}@campusverse.edu`;
  const initialPassword = 'InitialPassword123!';

  beforeAll(async () => {
    // 0. Ensure admin and student credentials
    const stdHash = await bcrypt.hash('Password123', 10);
    await prisma.user.upsert({
      where: { email: 'admin@campusverse.edu' },
      update: { passwordHash: stdHash, isActive: true, isEmailVerified: true, isAdminAuthorized: true },
      create: {
        email: 'admin@campusverse.edu',
        passwordHash: stdHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: { create: { fullName: 'Super Administrator' } }
      }
    });
    await prisma.user.upsert({
      where: { email: 'student@campusverse.edu' },
      update: { passwordHash: stdHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'student@campusverse.edu',
        passwordHash: stdHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Demo Student' } }
      }
    });

    // 1. Authenticate Admin
    const adminLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });
    adminToken = adminLogin.body.data.token;

    // 2. Authenticate Student
    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    studentToken = studentLogin.body.data.token;

    // 3. Create disposable test user for password reset tests
    const passwordHash = await bcrypt.hash(initialPassword, 10);
    const createdUser = await prisma.user.create({
      data: {
        email: testUserEmail,
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Remediation Test Student' } }
      }
    });
    testUserId = createdUser.id;
  });

  afterAll(async () => {
    // Clean up test user & OTP records
    await prisma.otpToken.deleteMany({ where: { email: testUserEmail } });
    await prisma.profile.deleteMany({ where: { userId: testUserId } });
    await prisma.user.deleteMany({ where: { id: testUserId } });

    // Restore platform settings to default state
    await prisma.platformSettings.upsert({
      where: { id: 'GLOBAL_SETTINGS' },
      update: {
        maintenanceMode: false,
        allowNewRegistrations: true,
        autoModeration: true,
        strictVerification: true
      },
      create: {
        id: 'GLOBAL_SETTINGS',
        maintenanceMode: false,
        allowNewRegistrations: true,
        autoModeration: true,
        strictVerification: true
      }
    });
  });

  // ===========================================================================
  // PART 1: PERSISTENT PLATFORM SETTINGS
  // ===========================================================================
  describe('Part 1: Persistent Platform Settings', () => {
    it('1.1: should retrieve platform settings directly from database', async () => {
      const res = await request(app)
        .get('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.systemVersion).toBeDefined();

      // Verify the record exists in the SQLite database
      const dbRecord = await prisma.platformSettings.findUnique({
        where: { id: 'GLOBAL_SETTINGS' }
      });
      expect(dbRecord).not.toBeNull();
      expect(dbRecord?.systemVersion).toBe(res.body.data.systemVersion);
    });

    it('1.2: should update maintenanceMode and persist in database', async () => {
      const patchRes = await request(app)
        .patch('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ maintenanceMode: true });

      expect(patchRes.status).toBe(200);
      expect(patchRes.body.data.maintenanceMode).toBe(true);

      const dbRecord = await prisma.platformSettings.findUnique({
        where: { id: 'GLOBAL_SETTINGS' }
      });
      expect(dbRecord?.maintenanceMode).toBe(true);

      const getRes = await request(app)
        .get('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`);
      expect(getRes.body.data.maintenanceMode).toBe(true);
    });

    it('1.3: should update allowNewRegistrations and persist in database', async () => {
      const patchRes = await request(app)
        .patch('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ allowNewRegistrations: false });

      expect(patchRes.status).toBe(200);
      expect(patchRes.body.data.allowNewRegistrations).toBe(false);

      const dbRecord = await prisma.platformSettings.findUnique({
        where: { id: 'GLOBAL_SETTINGS' }
      });
      expect(dbRecord?.allowNewRegistrations).toBe(false);

      const getRes = await request(app)
        .get('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`);
      expect(getRes.body.data.allowNewRegistrations).toBe(false);
    });

    it('1.4: should update autoModeration and persist in database', async () => {
      const patchRes = await request(app)
        .patch('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ autoModeration: false });

      expect(patchRes.status).toBe(200);
      expect(patchRes.body.data.autoModeration).toBe(false);

      const dbRecord = await prisma.platformSettings.findUnique({
        where: { id: 'GLOBAL_SETTINGS' }
      });
      expect(dbRecord?.autoModeration).toBe(false);

      const getRes = await request(app)
        .get('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`);
      expect(getRes.body.data.autoModeration).toBe(false);
    });

    it('1.5: should update strictVerification and persist in database', async () => {
      const patchRes = await request(app)
        .patch('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ strictVerification: false });

      expect(patchRes.status).toBe(200);
      expect(patchRes.body.data.strictVerification).toBe(false);

      const dbRecord = await prisma.platformSettings.findUnique({
        where: { id: 'GLOBAL_SETTINGS' }
      });
      expect(dbRecord?.strictVerification).toBe(false);

      const getRes = await request(app)
        .get('/api/v1/admin/settings')
        .set('Authorization', `Bearer ${adminToken}`);
      expect(getRes.body.data.strictVerification).toBe(false);
    });
  });

  // ===========================================================================
  // PART 2 & 3: SECURE ADMIN PASSWORD RESET & SECURITY TESTS
  // ===========================================================================
  describe('Part 2 & 3: Secure Admin Password Reset & Security Validation', () => {
    let extractedResetToken: string;

    it('2.1: should reject non-admin password reset attempt (RBAC)', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/users/${testUserId}/reset-password`)
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
    });

    it('2.2: should reject unauthenticated password reset attempt', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/users/${testUserId}/reset-password`);

      expect(res.status).toBe(401);
    });

    it('2.3: should allow authorized Admin reset, invalidate old password, and emit no secrets in response', async () => {
      emailService.clearSentEmails();

      const res = await request(app)
        .post(`/api/v1/admin/users/${testUserId}/reset-password`)
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.resetInitiated).toBe(true);

      // CRITICAL SECURITY CHECKS: No password in API response!
      expect(res.body.data.tempPassword).toBeUndefined();
      expect(res.body.data.password).toBeUndefined();
      expect(res.body.data.token).toBeUndefined();
      expect(res.body.data.otp).toBeUndefined();

      // Check that old password was immediately invalidated
      const oldLogin = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: testUserEmail, password: initialPassword });
      expect(oldLogin.status).toBe(401);

      // Check that token is hashed in DB and not stored as plaintext
      const dbTokens = await prisma.otpToken.findMany({
        where: { email: testUserEmail, purpose: 'PASSWORD_RESET', isUsed: false }
      });
      expect(dbTokens.length).toBe(1);
      const dbToken = dbTokens[0];
      expect(dbToken.otpHash).toBeDefined();
      expect(dbToken.otpHash.length).toBe(64); // SHA-256 hex length
      expect(dbToken.expiresAt.getTime()).toBeGreaterThan(Date.now());
      expect(dbToken.isUsed).toBe(false);

      // Extract the sent token from mock email service
      const sentEmails = emailService.getSentEmails().filter(e => e.to === testUserEmail);
      expect(sentEmails.length).toBe(1);
      const match = sentEmails[0].text.match(/verification code:\s*(\d{6})/i);
      expect(match).not.toBeNull();
      extractedResetToken = match![1];

      // Audit log check: verify admin action was recorded
      const auditEntry = await prisma.auditLog.findFirst({
        where: { targetType: 'USER', targetId: testUserId, action: 'ADMIN_RESET_PASSWORD' }
      });
      expect(auditEntry).not.toBeNull();
    });

    it('2.4: should reject invalid reset token', async () => {
      const res = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          email: testUserEmail,
          otp: '000000', // incorrect token
          newPassword: 'NewValidPassword123!'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
    });

    it('2.5: should reject expired reset token', async () => {
      // Temporarily set expiry to the past
      await prisma.otpToken.updateMany({
        where: { email: testUserEmail, purpose: 'PASSWORD_RESET' },
        data: { expiresAt: new Date(Date.now() - 60 * 1000) }
      });

      const res = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          email: testUserEmail,
          otp: extractedResetToken,
          newPassword: 'NewValidPassword123!'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error?.message).toMatch(/expired/i);
    });

    it('2.6: should accept valid token, set new password, and invalidate token', async () => {
      // Restore valid expiry and reset attempt count
      await prisma.otpToken.updateMany({
        where: { email: testUserEmail, purpose: 'PASSWORD_RESET' },
        data: {
          expiresAt: new Date(Date.now() + 15 * 60 * 1000),
          isUsed: false,
          attemptCount: 0
        }
      });

      const res = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          email: testUserEmail,
          otp: extractedResetToken,
          newPassword: 'BrandNewSecurePassword456!'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // Verify token in DB is now marked used
      const dbToken = await prisma.otpToken.findFirst({
        where: { email: testUserEmail, purpose: 'PASSWORD_RESET' },
        orderBy: { createdAt: 'desc' }
      });
      expect(dbToken?.isUsed).toBe(true);
      expect(dbToken?.usedAt).not.toBeNull();
    });

    it('2.7: should reject token replay (single-use enforcement)', async () => {
      const res = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          email: testUserEmail,
          otp: extractedResetToken,
          newPassword: 'AnotherPassword789!'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
    });

    it('2.8: should reject old password on login and accept new password', async () => {
      // Old password rejected
      const oldLogin = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: testUserEmail, password: initialPassword });
      expect(oldLogin.status).toBe(401);

      // New password accepted
      const newLogin = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: testUserEmail, password: 'BrandNewSecurePassword456!' });
      expect(newLogin.status).toBe(200);
      expect(newLogin.body.data.token).toBeDefined();
    });
  });
});
