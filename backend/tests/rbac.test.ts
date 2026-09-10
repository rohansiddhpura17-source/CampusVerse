import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('RBAC & Admin Authorization Security Suite', () => {
  let studentToken: string;
  let alumniToken: string;
  let aspirantToken: string;
  let verifiedAdminToken: string;
  let unverifiedAdminToken: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // Ensure all demo users exist with standard password
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

    // 1. Get tokens for standard seed accounts
    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    studentToken = studentLogin.body.data.token;

    const alumniLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'alumni@campusverse.edu', password: 'Password123' });
    alumniToken = alumniLogin.body.data.token;

    const aspirantLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'aspirant@campusverse.edu', password: 'Password123' });
    aspirantToken = aspirantLogin.body.data.token;

    const adminLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });
    verifiedAdminToken = adminLogin.body.data.token;

    // 2. Register an unverified admin (fresh registration)
    await prisma.user.deleteMany({
      where: { email: 'unverified_admin@campusverse.edu' }
    });

    const unverifiedAdminReg = await request(app)
      .post('/api/v1/auth/register')
      .send({
        name: 'Unverified Admin',
        email: 'unverified_admin@campusverse.edu',
        password: 'Password123',
        role: 'ADMIN'
      });
    unverifiedAdminToken = unverifiedAdminReg.body.data.token;
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  describe('Non-Admin Role Rejection from Admin APIs (403 Forbidden)', () => {
    it('STUDENT role MUST be rejected from GET /api/v1/admin/dashboard with 403 Forbidden', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('ALUMNI role MUST be rejected from GET /api/v1/admin/dashboard with 403 Forbidden', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${alumniToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('ASPIRANT role MUST be rejected from GET /api/v1/admin/dashboard with 403 Forbidden', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('STUDENT role MUST be rejected from GET /api/v1/admin/users', async () => {
      const res = await request(app)
        .get('/api/v1/admin/users')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
    });

    it('STUDENT role MUST be rejected from GET /api/v1/admin/verifications', async () => {
      const res = await request(app)
        .get('/api/v1/admin/verifications')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
    });

    it('STUDENT role MUST be rejected from GET /api/v1/admin/reports', async () => {
      const res = await request(app)
        .get('/api/v1/admin/reports')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
    });
  });

  describe('Unverified Admin Protection', () => {
    it('Admin with isAdminAuthorized = false MUST be rejected with 403', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${unverifiedAdminToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('ADMIN_UNAUTHORIZED');
    });
  });

  describe('Verified Admin Access', () => {
    it('Verified Admin MUST successfully access GET /api/v1/admin/dashboard (200 OK)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${verifiedAdminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.metrics).toBeDefined();
      expect(res.body.data.metrics.users.total).toBeGreaterThanOrEqual(4);
    });

    it('Verified Admin MUST successfully access GET /api/v1/admin/users', async () => {
      const res = await request(app)
        .get('/api/v1/admin/users')
        .set('Authorization', `Bearer ${verifiedAdminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
    });

    it('Verified Admin MUST successfully post announcement and write audit log', async () => {
      const res = await request(app)
        .post('/api/v1/admin/announcements')
        .set('Authorization', `Bearer ${verifiedAdminToken}`)
        .send({
          title: 'Campus Hackathon 2026 Registration Open',
          content: 'All students and aspirants are invited to participate.',
          priority: 'HIGH'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.title).toBe('Campus Hackathon 2026 Registration Open');

      // Verify audit log
      const audit = await prisma.auditLog.findFirst({
        where: { action: 'ADMIN_CREATED_ANNOUNCEMENT' },
        orderBy: { timestamp: 'desc' }
      });
      expect(audit).toBeDefined();
      expect(audit!.targetType).toBe('ANNOUNCEMENT');
    });
  });
});
