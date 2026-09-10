import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Phase 6 — Aspirant Module API & Security Tests', () => {
  let aspirantToken: string;
  let aspirantUserId: string;
  let adminToken: string;
  let studentToken: string;
  let sampleCollegeId: string;
  let sampleCollegeId2: string;
  let sampleScholarshipId: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // Ensure aspirant user exists
    await prisma.user.upsert({
      where: { email: 'aspirant@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'aspirant@campusverse.edu',
        passwordHash,
        role: 'ASPIRANT',
        isActive: true,
        isEmailVerified: true,
        profile: {
          create: {
            fullName: 'Kavya Sharma'
          }
        }
      }
    });

    // Ensure admin user exists
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
        profile: {
          create: {
            fullName: 'Super Administrator'
          }
        }
      }
    });

    // Ensure student user exists
    await prisma.user.upsert({
      where: { email: 'student@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'student@campusverse.edu',
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: {
          create: {
            fullName: 'Rohan Mehta'
          }
        }
      }
    });

    // 1. Authenticate as Aspirant demo account
    const aspirantLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({
        email: 'aspirant@campusverse.edu',
        password: 'Password123'
      });

    expect(aspirantLogin.status).toBe(200);
    expect(aspirantLogin.body.data.token).toBeDefined();
    aspirantToken = aspirantLogin.body.data.token;
    aspirantUserId = aspirantLogin.body.data.user.userId;

    // 2. Authenticate as Admin
    const adminLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({
        email: 'admin@campusverse.edu',
        password: 'Password123'
      });
    adminToken = adminLogin.body.data.token;

    // 3. Authenticate as Student
    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({
        email: 'student@campusverse.edu',
        password: 'Password123'
      });
    studentToken = studentLogin.body.data.token;

    // Find institutions for testing
    let institutions = await prisma.institution.findMany({ take: 2 });
    if (institutions.length < 2) {
      const inst1 = await prisma.institution.create({
        data: { name: 'Test Tech Institute', code: 'TTI-1', city: 'Rajkot', state: 'Gujarat', country: 'India', ranking: 1, verified: true }
      });
      const inst2 = await prisma.institution.create({
        data: { name: 'Global Science University', code: 'GSU-1', city: 'Munich', state: 'Bavaria', country: 'Germany', ranking: 2, verified: true }
      });
      institutions = [inst1, inst2];
    }
    sampleCollegeId = institutions[0].id;
    sampleCollegeId2 = institutions[1].id;

    // Find scholarship
    let scholarship = await prisma.scholarship.findFirst();
    if (!scholarship) {
      scholarship = await prisma.scholarship.create({
        data: {
          name: 'National Merit Scholarship',
          provider: 'EduTrust',
          amount: '₹50,000 / year',
          category: 'MERIT',
          country: 'India',
          deadline: 'May 31, 2026',
          eligibility: 'Open to B.Tech undergraduates with >= 8.5 GPA',
          description: 'Institutional merit financial aid'
        }
      });
    }
    sampleScholarshipId = scholarship.id;
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  describe('1. Aspirant Home Dashboard', () => {
    it('should return complete aspirant dashboard summary', async () => {
      const res = await request(app)
        .get('/api/v1/aspirant/home')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.profile.fullName).toBe('Rohan Mehta');
      expect(res.body.data.savedCollegesCount).toBeGreaterThanOrEqual(1);
      expect(res.body.data.savedScholarshipsCount).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(res.body.data.recommendedColleges)).toBe(true);
      expect(res.body.data.recommendedColleges.length).toBeGreaterThan(0);
    });

    it('should reject unauthenticated access with 401', async () => {
      const res = await request(app).get('/api/v1/aspirant/home');
      expect(res.status).toBe(401);
    });
  });

  describe('2. College Explorer & Details', () => {
    it('should list colleges with pagination, ranking, and search', async () => {
      const res = await request(app)
        .get('/api/v1/colleges?search=Technology')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
      expect(res.body.data[0].programs).toBeDefined();
    });

    it('should filter colleges by country', async () => {
      const res = await request(app)
        .get('/api/v1/colleges?country=India')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.every((c: any) => c.country === 'India')).toBe(true);
    });

    it('should get detailed college information by ID', async () => {
      const res = await request(app)
        .get(`/api/v1/colleges/${sampleCollegeId}`)
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.id).toBe(sampleCollegeId);
      expect(res.body.data.name).toBeDefined();
      expect(res.body.data.programs.length).toBeGreaterThan(0);
    });

    it('should return 404 for non-existent college', async () => {
      const res = await request(app)
        .get('/api/v1/colleges/00000000-0000-0000-0000-000000000000')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(404);
    });
  });

  describe('3. Saved Colleges Workflow', () => {
    it('should allow saving and unsaving a college', async () => {
      // Save college
      const saveRes = await request(app)
        .post(`/api/v1/colleges/${sampleCollegeId}/save`)
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(saveRes.status).toBe(200);
      expect(saveRes.body.data.saved).toBe(true);

      // Verify in saved list
      const listRes = await request(app)
        .get('/api/v1/colleges/saved')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(listRes.status).toBe(200);
      expect(listRes.body.data.some((c: any) => c.id === sampleCollegeId)).toBe(true);

      // Unsave
      const unsaveRes = await request(app)
        .delete(`/api/v1/colleges/${sampleCollegeId}/save`)
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(unsaveRes.status).toBe(200);
      expect(unsaveRes.body.data.saved).toBe(false);
    });
  });

  describe('4. College Comparison', () => {
    it('should generate side-by-side comparison for 2 or more colleges', async () => {
      const res = await request(app)
        .post('/api/v1/colleges/compare')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          collegeIds: [sampleCollegeId, sampleCollegeId2]
        });

      expect(res.status).toBe(200);
      expect(res.body.data.colleges.length).toBe(2);
      expect(res.body.data.colleges[0].ranking).toBeDefined();
      expect(res.body.data.colleges[0].averageFees).toBeDefined();
    });

    it('should reject comparison with fewer than 2 colleges', async () => {
      const res = await request(app)
        .post('/api/v1/colleges/compare')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          collegeIds: [sampleCollegeId]
        });

      expect(res.status).toBe(400);
    });
  });

  describe('5. Admission Predictor', () => {
    it('should calculate weighted admission prediction and persist record', async () => {
      const res = await request(app)
        .post('/api/v1/predictions/predict')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          institutionId: sampleCollegeId,
          programName: 'B.Tech in Computer Science and Engineering',
          degree: 'B.TECH',
          gpa: 9.5,
          testType: 'JEE_MAIN',
          testScore: 99.2
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.predictionPercentage).toBeGreaterThan(0);
      expect(['STRONG_CANDIDATE', 'COMPETITIVE', 'REACH', 'UNLIKELY']).toContain(res.body.data.qualificationStatus);
      expect(res.body.data.feedback).toBeDefined();
      expect(Array.isArray(res.body.data.recommendations)).toBe(true);
    });

    it('should reject prediction with invalid GPA', async () => {
      const res = await request(app)
        .post('/api/v1/predictions/predict')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          institutionId: sampleCollegeId,
          programName: 'B.Tech in Computer Science',
          degree: 'B.TECH',
          gpa: 15.0, // Invalid: Max is 10.0
          testType: 'JEE_MAIN',
          testScore: 95.0
        });

      expect(res.status).toBe(400);
    });

    it('should retrieve prediction history', async () => {
      const res = await request(app)
        .get('/api/v1/predictions/history')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
    });
  });

  describe('6. Scholarships & Saved Scholarships', () => {
    it('should list scholarships and support filtering', async () => {
      const res = await request(app)
        .get('/api/v1/scholarships?category=MERIT')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
      expect(res.body.data[0].amount).toBeDefined();
    });

    it('should get scholarship details by ID', async () => {
      const res = await request(app)
        .get(`/api/v1/scholarships/${sampleScholarshipId}`)
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.id).toBe(sampleScholarshipId);
      expect(res.body.data.eligibility).toBeDefined();
    });

    it('should save and unsave a scholarship', async () => {
      // Save
      const saveRes = await request(app)
        .post(`/api/v1/scholarships/${sampleScholarshipId}/save`)
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(saveRes.status).toBe(200);
      expect(saveRes.body.data.saved).toBe(true);

      // Verify saved
      const savedRes = await request(app)
        .get('/api/v1/scholarships/saved')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(savedRes.status).toBe(200);
      expect(savedRes.body.data.some((s: any) => s.id === sampleScholarshipId)).toBe(true);

      // Unsave
      const unsaveRes = await request(app)
        .delete(`/api/v1/scholarships/${sampleScholarshipId}/save`)
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(unsaveRes.status).toBe(200);
      expect(unsaveRes.body.data.saved).toBe(false);
    });
  });

  describe('7. Aspirant Profile CRUD', () => {
    it('should retrieve aspirant profile details', async () => {
      const res = await request(app)
        .get('/api/v1/aspirant/profile')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.email).toBe('aspirant@campusverse.edu');
      expect(res.body.data.targetDegree).toBe('B.Tech');
    });

    it('should update aspirant target academic goals', async () => {
      const res = await request(app)
        .patch('/api/v1/aspirant/profile')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          targetMajor: 'Artificial Intelligence & Robotics',
          expectedGradYear: 2028,
          entranceExamScores: {
            IELTS: 8.5,
            JEE_MAIN: 99.1,
            SAT: 1520,
            GPA: 9.6
          }
        });

      expect(res.status).toBe(200);
      expect(res.body.data.targetMajor).toBe('Artificial Intelligence & Robotics');
      expect(res.body.data.expectedGradYear).toBe(2028);
      expect(res.body.data.entranceExamScores.IELTS).toBe(8.5);
      expect(res.body.data.entranceExamScores.SAT).toBe(1520);
    });
  });

  describe('8. AI Recommendations Proxy', () => {
    it('should handle AI recommendations query without client secrets', async () => {
      const res = await request(app)
        .post('/api/v1/ai/aspirant-recommendations')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          query: 'Which colleges are best for B.Tech AI & Data Science with 98.4% JEE Main score?',
          mode: 'COLLEGE_RECOMMENDATION'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.query).toBeDefined();
      expect(res.body.data.available !== undefined).toBe(true);
    });
  });

  describe('9. Strict RBAC & Negative Security Tests', () => {
    it('Aspirant CANNOT access Admin Dashboard (403 Forbidden)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(403);
    });

    it('Aspirant CANNOT approve verifications (403 Forbidden)', async () => {
      const res = await request(app)
        .post('/api/v1/admin/verifications/some-verif-id/review')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({ status: 'APPROVED' });

      expect(res.status).toBe(403);
    });

    it('Invalid JWT token is rejected with 401', async () => {
      const res = await request(app)
        .get('/api/v1/aspirant/home')
        .set('Authorization', 'Bearer invalid.token.payload');

      expect(res.status).toBe(401);
    });
  });
});
