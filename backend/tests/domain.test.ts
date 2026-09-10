import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Domain Operations Suite (Jobs, Mentorship, Marketplace, Safety)', () => {
  let studentToken: string;
  let alumniToken: string;
  let adminToken: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

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

    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    studentToken = studentLogin.body.data.token;

    const alumniLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'alumni@campusverse.edu', password: 'Password123' });
    alumniToken = alumniLogin.body.data.token;

    const adminLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });
    adminToken = adminLogin.body.data.token;
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  describe('Jobs & Applications', () => {
    it('should query jobs with pagination and filters', async () => {
      const res = await request(app)
        .get('/api/v1/jobs?roleType=FULL_TIME&page=1&limit=5')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.meta).toBeDefined();
      expect(res.body.meta.page).toBe(1);
    });

    it('student should submit job application', async () => {
      const jobs = await prisma.job.findMany({ take: 1 });
      expect(jobs.length).toBeGreaterThan(0);
      const jobId = jobs[0].id;

      // Clear existing application if any
      await prisma.jobApplication.deleteMany({
        where: { jobId, applicant: { email: 'student@campusverse.edu' } }
      });

      const res = await request(app)
        .post(`/api/v1/jobs/${jobId}/apply`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          resumeUrl: 'https://docs.campusverse.edu/resumes/aarav_resume_2026.pdf',
          coverLetter: 'Excited about the Android developer position at Google India.'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.status).toBe('APPLIED');
    });
  });

  describe('Mentorship Workflow', () => {
    it('should query active mentors with search', async () => {
      const res = await request(app)
        .get('/api/v1/mentors?expertise=Android')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
    });

    it('student should submit a mentorship request to an alumni mentor', async () => {
      const mentor = await prisma.mentorProfile.findFirst();
      expect(mentor).toBeDefined();

      await prisma.mentorshipRequest.deleteMany({
        where: { mentee: { email: 'student@campusverse.edu' }, mentorId: mentor!.id }
      });

      const res = await request(app)
        .post('/api/v1/mentorship/requests')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          mentorId: mentor!.id,
          goal: 'Android Architecture & Mock Interview',
          message: 'Hi Priya, I would love some feedback on Compose clean architecture.'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.status).toBe('PENDING');
    });
  });

  describe('Marketplace CRUD', () => {
    let createdItemId: string;

    it('student should list an item on the marketplace', async () => {
      const res = await request(app)
        .post('/api/v1/marketplace')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          title: 'Scientific Calculator Casio FX-991EX',
          description: 'ClassWiz series solar-powered scientific calculator. Great for engineering exams.',
          price: 650.0,
          category: 'ELECTRONICS',
          condition: 'LIKE_NEW'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.title).toBe('Scientific Calculator Casio FX-991EX');
      createdItemId = res.body.data.id;
    });

    it('should retrieve marketplace items filtered by category', async () => {
      const res = await request(app)
        .get('/api/v1/marketplace?category=ELECTRONICS')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.length).toBeGreaterThanOrEqual(1);
    });

    it('seller should update listing price and details', async () => {
      const res = await request(app)
        .patch(`/api/v1/marketplace/${createdItemId}`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          price: 550.0,
          status: 'AVAILABLE'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.price).toBe(550.0);
    });
  });

  describe('Safety Reports', () => {
    it('user should submit a safety report', async () => {
      const res = await request(app)
        .post('/api/v1/reports')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          targetType: 'POST',
          targetId: 'post_dummy_123',
          reason: 'Spam or irrelevant commercial advertising'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.status).toBe('PENDING');
    });
  });
});
