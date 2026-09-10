import request from 'supertest';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Auth API & Validation Suite', () => {
  beforeAll(async () => {
    // Ensure clean state for test emails
    await prisma.user.deleteMany({
      where: {
        email: {
          in: [
            'newuser@campusverse.edu',
            'duplicate@campusverse.edu',
            'student.test@campusverse.edu',
            'candidate_admin@campusverse.edu'
          ]
        }
      }
    });
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  describe('POST /api/v1/auth/register', () => {
    const testUserEmail = `newuser_${Date.now()}@campusverse.edu`;

    it('should successfully register a student and never grant admin privileges', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'New Student',
          email: testUserEmail,
          password: 'Password123',
          role: 'STUDENT'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.role).toBe('STUDENT');
      expect(res.body.data.user.isAdminAuthorized).toBe(false);
      expect(res.body.data.token).toBeDefined();

      // Verify in DB that password is NOT stored as plaintext
      const dbUser = await prisma.user.findUnique({
        where: { email: testUserEmail }
      });
      expect(dbUser).toBeDefined();
      expect(dbUser!.passwordHash).not.toBe('Password123');
      expect(dbUser!.passwordHash.startsWith('$2')).toBe(true); // Bcrypt hash
    });

    it('should reject registration with duplicate email', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Duplicate Student',
          email: testUserEmail,
          password: 'Password123',
          role: 'STUDENT'
        });

      expect(res.status).toBe(409);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('EMAIL_EXISTS');
    });

    it('should reject registration with weak password (< 8 chars)', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Weak Pass',
          email: 'weak@campusverse.edu',
          password: 'Pass1',
          role: 'STUDENT'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
    });

    it('should reject registration with letters-only password', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'No Digits',
          email: 'nodigits@campusverse.edu',
          password: 'PasswordOnly',
          role: 'STUDENT'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
    });

    it('should register an Admin with isAdminAuthorized = false (Zero Self-Granting)', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Self Admin Candidate',
          email: 'candidate_admin@campusverse.edu',
          password: 'Password123',
          role: 'ADMIN'
        });

      expect(res.status).toBe(201);
      expect(res.body.data.user.role).toBe('ADMIN');
      expect(res.body.data.user.isAdminAuthorized).toBe(false);
    });
  });

  describe('POST /api/v1/auth/login', () => {
    it('should authenticate valid credentials and return JWT token', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: 'student@campusverse.edu',
          password: 'Password123'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.token).toBeDefined();
      expect(res.body.data.user.email).toBe('student@campusverse.edu');
      expect(res.body.data.user.role).toBe('STUDENT');
    });

    it('should reject invalid password', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: 'student@campusverse.edu',
          password: 'WrongPassword999'
        });

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
    });

    it('should reject non-existent user email', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: 'nobody_exists@campusverse.edu',
          password: 'Password123'
        });

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
    });
  });

  describe('GET /api/v1/auth/me', () => {
    it('should return authenticated user profile when valid Bearer token provided', async () => {
      const loginRes = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: 'student@campusverse.edu',
          password: 'Password123'
        });

      const token = loginRes.body.data.token;

      const meRes = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', `Bearer ${token}`);

      expect(meRes.status).toBe(200);
      expect(meRes.body.success).toBe(true);
      expect(meRes.body.data.email).toBe('student@campusverse.edu');
      expect(meRes.body.data.role).toBe('STUDENT');
      expect(meRes.body.data.profile).toBeDefined();
    });

    it('should reject request when token is missing', async () => {
      const res = await request(app).get('/api/v1/auth/me');
      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
    });

    it('should reject request when token is malformed', async () => {
      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', 'Bearer invalid_token_xyz');

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
    });
  });
});
