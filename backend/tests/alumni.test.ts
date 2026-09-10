import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Phase 5: Alumni Module Backend & RBAC Tests', () => {
  let alumniToken: string;
  let alumniUserId: string;
  let peerAlumniUserId: string;
  let studentToken: string;
  let studentUserId: string;
  let companyId: string;
  let jobId: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // Ensure alumni user exists
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
        profile: { create: { fullName: 'Rohan Mehta' } }
      }
    });

    // Login as Alumni
    const alumniLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'alumni@campusverse.edu', password: 'Password123' });
    expect(alumniLogin.status).toBe(200);
    alumniToken = alumniLogin.body.data.token;
    alumniUserId = alumniLogin.body.data.user.userId;

    // Login as Student
    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    expect(studentLogin.status).toBe(200);
    studentToken = studentLogin.body.data.token;
    studentUserId = studentLogin.body.data.user.userId;

    // Retrieve peer alumni
    let peer = await prisma.user.findFirst({
      where: { role: 'ALUMNI', NOT: { id: alumniUserId } }
    });
    if (!peer) {
      peer = await prisma.user.create({
        data: {
          email: `peer_alumni_${Date.now()}@campusverse.edu`,
          passwordHash: 'dummyHash',
          role: 'ALUMNI',
          isActive: true,
          isEmailVerified: true,
          profile: {
            create: {
              fullName: 'Peer Alumni User'
            }
          }
        }
      });
    }
    peerAlumniUserId = peer.id;

    // Retrieve or create company and job
    let company = await prisma.company.findFirst();
    if (!company) {
      company = await prisma.company.create({
        data: {
          name: 'Google India',
          website: 'https://careers.google.com',
          industry: 'Technology',
          description: 'Global tech company'
        }
      });
    }
    companyId = company.id;

    let job = await prisma.job.findFirst({ where: { companyId } });
    if (!job) {
      job = await prisma.job.create({
        data: {
          title: 'Software Engineer',
          companyId,
          posterId: alumniUserId,
          roleType: 'FULL_TIME',
          location: 'Bangalore, India',
          description: 'Android dev job',
          status: 'ACTIVE'
        }
      });
    }
    jobId = job.id;
  });

  // 1. Alumni Discovery & Profile
  test('GET /api/v1/alumni returns list of verified alumni with search and filter', async () => {
    const res = await request(app)
      .get('/api/v1/alumni?search=Google')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.data)).toBe(true);
    expect(res.body.data.length).toBeGreaterThan(0);
    expect(res.body.data[0].company).toContain('Google');
  });

  test('GET /api/v1/alumni/:id returns full alumni profile with mutual connections', async () => {
    const res = await request(app)
      .get(`/api/v1/alumni/${peerAlumniUserId}`)
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.userId).toBe(peerAlumniUserId);
    expect(res.body.data.company).toBeDefined();
    expect(res.body.data.mutualConnectionsCount).toBeDefined();
  });

  test('PATCH /api/v1/users/profile/alumni updates authenticated alumni profile', async () => {
    const res = await request(app)
      .patch('/api/v1/users/profile/alumni')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        headline: 'Staff Software Engineer @ Google | Tech Lead',
        yearsOfExperience: 6,
        company: 'Google India',
        designation: 'Staff Software Engineer',
        skills: ['Kotlin', 'Compose', 'Distributed Systems', 'System Design', 'Leadership']
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.profile.headline).toBe('Staff Software Engineer @ Google | Tech Lead');
    expect(res.body.data.profile.alumniProfile.currentDesignation).toBe('Staff Software Engineer');
  });

  // 2. Networking & Connections
  test('POST /api/v1/alumni/:id/save bookmarks alumni profile', async () => {
    const res = await request(app)
      .post(`/api/v1/alumni/${peerAlumniUserId}/save`)
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);

    const listRes = await request(app)
      .get('/api/v1/alumni/saved')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(listRes.status).toBe(200);
    expect(listRes.body.data.some((a: any) => a.userId === peerAlumniUserId)).toBe(true);
  });

  test('GET /api/v1/alumni/network/connections returns connections and pending requests', async () => {
    const res = await request(app)
      .get('/api/v1/alumni/network/connections')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.data.connections)).toBe(true);
    expect(Array.isArray(res.body.data.pendingRequests)).toBe(true);
  });

  test('GET /api/v1/alumni/network/activity returns recent network activity', async () => {
    const res = await request(app)
      .get('/api/v1/alumni/network/activity')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.data)).toBe(true);
  });

  // 3. Careers & Opportunities
  test('GET /api/v1/jobs/recommended returns recommended opportunities', async () => {
    const res = await request(app)
      .get('/api/v1/jobs/recommended')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.data)).toBe(true);
  });

  test('POST /api/v1/jobs/:id/save bookmarks a job', async () => {
    const res = await request(app)
      .post(`/api/v1/jobs/${jobId}/save`)
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);

    const savedRes = await request(app)
      .get('/api/v1/jobs/saved')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(savedRes.status).toBe(200);
    expect(savedRes.body.data.some((s: any) => s.jobId === jobId)).toBe(true);
  });

  test('GET /api/v1/companies and GET /api/v1/companies/:id returns company details and jobs', async () => {
    const res = await request(app)
      .get('/api/v1/companies')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(200);
    expect(res.body.data.length).toBeGreaterThan(0);

    const singleRes = await request(app)
      .get(`/api/v1/companies/${companyId}`)
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(singleRes.status).toBe(200);
    expect(singleRes.body.data.id).toBe(companyId);
    expect(Array.isArray(singleRes.body.data.jobs)).toBe(true);
  });

  test('POST /api/v1/referrals creates referral request', async () => {
    const res = await request(app)
      .post('/api/v1/referrals')
      .set('Authorization', `Bearer ${studentToken}`)
      .send({
        alumniId: alumniUserId,
        jobId,
        companyName: 'Google India',
        notes: 'Excited about the Android Platform team.'
      });

    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);
    expect(res.body.data.status).toBe('REQUESTED');
  });

  // 4. Mentorship Requests & Sessions
  test('GET /api/v1/mentors and GET /api/v1/mentorship/sessions returns active sessions', async () => {
    const mentorsRes = await request(app)
      .get('/api/v1/mentors')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(mentorsRes.status).toBe(200);
    expect(mentorsRes.body.data.length).toBeGreaterThan(0);

    const sessionsRes = await request(app)
      .get('/api/v1/mentorship/sessions')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(sessionsRes.status).toBe(200);
    expect(Array.isArray(sessionsRes.body.data)).toBe(true);
  });

  // 5. AI Career Assistant & Development
  test('POST /api/v1/ai/career-assistant handles career query gracefully without client secrets', async () => {
    const res = await request(app)
      .post('/api/v1/ai/career-assistant')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        query: 'How to prepare for Staff Engineer system design interviews?',
        mode: 'CAREER_GUIDANCE',
        topic: 'System Design & Leadership'
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.query).toBe('How to prepare for Staff Engineer system design interviews?');
  });

  test('CRUD /api/v1/career/roadmaps manages career roadmap', async () => {
    const createRes = await request(app)
      .post('/api/v1/career/roadmaps')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        title: 'Roadmap to Engineering Director',
        targetRole: 'Engineering Director',
        milestones: [
          { id: 'm1', title: 'Manage 3 Engineering Squads', completed: true },
          { id: 'm2', title: 'Drive Org-Wide Tech Strategy', completed: false }
        ]
      });

    expect(createRes.status).toBe(201);
    expect(createRes.body.data.progressPercentage).toBe(50.0);
    const roadmapId = createRes.body.data.id;

    const listRes = await request(app)
      .get('/api/v1/career/roadmaps')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(listRes.status).toBe(200);
    expect(listRes.body.data.some((r: any) => r.id === roadmapId)).toBe(true);

    const delRes = await request(app)
      .delete(`/api/v1/career/roadmaps/${roadmapId}`)
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(delRes.status).toBe(200);
  });

  test('CRUD /api/v1/career/interviews records mock interview results', async () => {
    const res = await request(app)
      .post('/api/v1/career/interviews')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        roleTarget: 'Staff Software Engineer',
        topic: 'Distributed Real-Time Sync',
        durationMinutes: 45,
        feedbackScore: 92.0,
        technicalScore: 94.0,
        behavioralScore: 90.0,
        systemDesignScore: 93.0,
        communicationScore: 91.0,
        transcript: 'Comprehensive discussion on CRDTs and Raft consensus.',
        strengths: ['Deep knowledge of distributed systems', 'Clear communication'],
        improvements: ['Include more metrics on memory pressure']
      });

    expect(res.status).toBe(201);
    expect(res.body.data.feedbackScore).toBe(92.0);
    expect(res.body.data.strengths.length).toBe(2);

    const getRes = await request(app)
      .get(`/api/v1/career/interviews/${res.body.data.id}`)
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(getRes.status).toBe(200);
    expect(getRes.body.data.feedbackScore).toBe(92.0);
  });

  test('GET and PATCH /api/v1/career/preferences updates career preferences', async () => {
    const res = await request(app)
      .patch('/api/v1/career/preferences')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        preferredRoles: ['Staff Software Engineer', 'Engineering Manager'],
        preferredLocations: ['Bengaluru', 'Hyderabad', 'Remote'],
        remotePreference: 'HYBRID',
        targetSalary: '₹50,00,000 - ₹70,00,000',
        jobAlerts: true
      });

    expect(res.status).toBe(200);
    expect(res.body.data.remotePreference).toBe('HYBRID');
    expect(res.body.data.preferredRoles).toContain('Staff Software Engineer');
  });

  // 6. Settings, Privacy & Security
  test('GET and PATCH /api/v1/users/settings/privacy updates privacy toggles', async () => {
    const res = await request(app)
      .patch('/api/v1/users/settings/privacy')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        showEmail: false,
        showPhone: true,
        allowMessagesFrom: 'ALL',
        allowMentorshipRequests: true
      });

    expect(res.status).toBe(200);
    expect(res.body.data.showPhone).toBe(true);
  });

  test('GET and PATCH /api/v1/users/settings/security updates security preferences', async () => {
    const res = await request(app)
      .patch('/api/v1/users/settings/security')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({
        twoFactorEnabled: true,
        loginAlertsEnabled: true
      });

    expect(res.status).toBe(200);
    expect(res.body.data.twoFactorEnabled).toBe(true);
  });

  test('POST /api/v1/users/settings/account-recovery sets recovery email', async () => {
    const res = await request(app)
      .post('/api/v1/users/settings/account-recovery')
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({ recoveryEmail: 'priya.backup@gmail.com' });

    expect(res.status).toBe(201);
    expect(res.body.data.recoveryEmail).toBe('priya.backup@gmail.com');
  });

  // 7. Critical Negative Security Tests
  test('SECURITY NEGATIVE: Alumni cannot modify another user\'s profile', async () => {
    const res = await request(app)
      .patch(`/api/v1/users/${peerAlumniUserId}`)
      .set('Authorization', `Bearer ${alumniToken}`)
      .send({ fullName: 'Hacked Name' });

    expect(res.status).toBe(403);
    expect(res.body.error?.code || res.body.code).toBe('FORBIDDEN');
  });

  test('SECURITY NEGATIVE: Alumni cannot modify another user\'s marketplace item', async () => {
    const item = await prisma.marketplaceItem.findFirst({
      where: { sellerId: studentUserId }
    });

    if (item) {
      const res = await request(app)
        .patch(`/api/v1/marketplace/${item.id}`)
        .set('Authorization', `Bearer ${alumniToken}`)
        .send({ price: 1.0 });

      expect(res.status).toBe(403);
      expect(res.body.error?.code || res.body.code).toBe('FORBIDDEN');
    }
  });

  test('SECURITY NEGATIVE: Alumni cannot access Admin APIs', async () => {
    const res = await request(app)
      .get('/api/v1/admin/verifications')
      .set('Authorization', `Bearer ${alumniToken}`);

    expect(res.status).toBe(403);
    expect(res.body.error?.code || res.body.code).toBe('FORBIDDEN');
  });
});
