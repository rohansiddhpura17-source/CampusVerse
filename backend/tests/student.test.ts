import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Student Module Backend & RBAC Security Suite', () => {
  let studentToken: string;
  let studentUserId: string;
  let otherStudentToken: string;
  let otherStudentUserId: string;
  let adminToken: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

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
        profile: { create: { fullName: 'Super Administrator' } }
      }
    });

    // 1. Student User
    const studentLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    studentToken = studentLogin.body.data.token;
    studentUserId = studentLogin.body.data.user.userId;

    // 2. Second Student for ownership/authorization testing
    const student2Email = `student2_${Date.now()}@campusverse.edu`;
    const student2Reg = await request(app)
      .post('/api/v1/auth/register')
      .send({
        name: 'Second Student',
        email: student2Email,
        password: 'Password123',
        role: 'STUDENT'
      });
    otherStudentToken = student2Reg.body.data.token;
    otherStudentUserId = student2Reg.body.data.user.userId;

    // 3. Admin User
    const adminLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });
    adminToken = adminLogin.body.data.token;
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  describe('1. Student Academics & Courses', () => {
    it('should retrieve student academic summary and enrolled courses', async () => {
      const res = await request(app)
        .get('/api/v1/academics/me')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.degree).toBeDefined();
      expect(Array.isArray(res.body.data.courses)).toBe(true);
    });

    it('should retrieve course catalog with semester filter', async () => {
      const res = await request(app)
        .get('/api/v1/courses?semester=6')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
    });
  });

  describe('2. Notes Hub CRUD & Ownership Authorization', () => {
    let studentNoteId: string;

    it('should allow student to create a study note', async () => {
      const res = await request(app)
        .post('/api/v1/notes')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          title: 'Distributed Systems Raft Consensus Notes',
          description: 'Comprehensive breakdown of leader election and log replication in Raft.',
          fileUrl: 'https://docs.campusverse.edu/notes/raft_consensus.pdf',
          tags: 'distributed-systems,raft,consensus,cse'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.title).toBe('Distributed Systems Raft Consensus Notes');
      expect(res.body.data.status).toBe('PENDING_REVIEW');
      studentNoteId = res.body.data.id;
    });

    it('should retrieve notes in My Notes list and support search query', async () => {
      const res = await request(app)
        .get('/api/v1/notes/my')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.length).toBeGreaterThanOrEqual(1);
      const found = res.body.data.find((n: any) => n.id === studentNoteId);
      expect(found).toBeDefined();
    });

    it('should allow note author to update their own note while pending review', async () => {
      const res = await request(app)
        .patch(`/api/v1/notes/${studentNoteId}`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          title: 'Distributed Systems Raft Consensus Notes (v2 Updated)'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.title).toContain('(v2 Updated)');
    });

    it('NEGATIVE TEST: Other student CANNOT update another user note (403 Forbidden)', async () => {
      const res = await request(app)
        .patch(`/api/v1/notes/${studentNoteId}`)
        .set('Authorization', `Bearer ${otherStudentToken}`)
        .send({
          title: 'Hacked Note Title by Unauthorized User'
        });

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('NEGATIVE TEST: Other student CANNOT delete another user note (403 Forbidden)', async () => {
      const res = await request(app)
        .delete(`/api/v1/notes/${studentNoteId}`)
        .set('Authorization', `Bearer ${otherStudentToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
    });

    it('NEGATIVE TEST: Student author CANNOT delete own note directly (403 Forbidden)', async () => {
      const res = await request(app)
        .delete(`/api/v1/notes/${studentNoteId}`)
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
      expect(res.body.error.message).toContain('Students cannot delete notes directly');
    });

    it('should allow Admin to delete the note', async () => {
      const res = await request(app)
        .delete(`/api/v1/notes/${studentNoteId}`)
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.deleted).toBe(true);
    });
  });

  describe('3. Digital Library Hub', () => {
    it('should retrieve library items with search and category filtering', async () => {
      const res = await request(app)
        .get('/api/v1/library?category=ALL')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
    });
  });

  describe('4. Events Registration & Cancellation', () => {
    let testEventId: string;

    beforeAll(async () => {
      let event = await prisma.event.findFirst();
      if (!event) {
        const adminUser = await prisma.user.findUnique({ where: { email: 'admin@campusverse.edu' } });
        event = await prisma.event.create({
          data: {
            title: 'Student Orientation Event',
            description: 'Campus orientation',
            category: 'WORKSHOP',
            startTime: new Date(Date.now() + 86400000),
            endTime: new Date(Date.now() + 172800000),
            location: 'Auditorium 1',
            organizerId: adminUser ? adminUser.id : studentUserId,
            status: 'UPCOMING'
          }
        });
      }
      testEventId = event.id;
    });

    it('should register student for an event', async () => {
      // Clear if already registered
      await prisma.eventRegistration.deleteMany({
        where: { eventId: testEventId, userId: studentUserId }
      });

      const res = await request(app)
        .post(`/api/v1/events/${testEventId}/register`)
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
    });

    it('should unregister student from an event', async () => {
      const res = await request(app)
        .delete(`/api/v1/events/${testEventId}/register`)
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.unregistered).toBe(true);
    });
  });

  describe('5. Community Hub & Post Security', () => {
    let communityId: string;
    let createdPostId: string;

    beforeAll(async () => {
      let community = await prisma.community.findFirst();
      if (!community) {
        community = await prisma.community.create({
          data: {
            name: 'Computer Science & Engineering',
            slug: `cs-engineering-${Date.now()}`,
            description: 'Discussions on algorithms, systems, and career.',
            creatorId: studentUserId,
            memberCount: 1
          }
        });
      }
      communityId = community.id;
    });

    it('student should join community', async () => {
      const res = await request(app)
        .post(`/api/v1/communities/${communityId}/join`)
        .set('Authorization', `Bearer ${studentToken}`);

      expect([200, 201]).toContain(res.status);
    });

    it('student should create a discussion post', async () => {
      const res = await request(app)
        .post(`/api/v1/communities/${communityId}/posts`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          title: 'Tips for Google SWE Summer Internship 2026',
          content: 'Here are the key DSA topics and system design questions that were asked.'
        });

      expect(res.status).toBe(201);
      expect(res.body.data.title).toBe('Tips for Google SWE Summer Internship 2026');
      createdPostId = res.body.data.id;
    });

    it('student should add a comment to a post', async () => {
      const res = await request(app)
        .post(`/api/v1/posts/${createdPostId}/comments`)
        .set('Authorization', `Bearer ${otherStudentToken}`)
        .send({
          content: 'Thanks for sharing these insights! Very helpful.'
        });

      expect(res.status).toBe(201);
      expect(res.body.data.content).toBe('Thanks for sharing these insights! Very helpful.');
    });

    it('NEGATIVE TEST: Non-author student CANNOT delete another user post (403 Forbidden)', async () => {
      const res = await request(app)
        .delete(`/api/v1/posts/${createdPostId}`)
        .set('Authorization', `Bearer ${otherStudentToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('author student should successfully delete their own post', async () => {
      const res = await request(app)
        .delete(`/api/v1/posts/${createdPostId}`)
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.deleted).toBe(true);
    });
  });

  describe('6. AI Study Assistant Backend Endpoint', () => {
    it('should return valid structured response from study assistant endpoint', async () => {
      const res = await request(app)
        .post('/api/v1/ai/study-assistant')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          query: 'Explain Dijkstra shortest path algorithm with time complexity',
          mode: 'EXPLAIN'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.query).toBe('Explain Dijkstra shortest path algorithm with time complexity');
      // If API key is not configured, it returns available: false with an explanation message
      expect(typeof res.body.data.available).toBe('boolean');
    });
  });

  describe('7. Student Profile Updates & Skills Persistence', () => {
    it('student should update academic details and skills', async () => {
      const res = await request(app)
        .patch('/api/v1/users/profile/student')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          fullName: 'Aarav Sharma (Updated)',
          bio: 'Final year CS student passionate about distributed systems and Android development.',
          degree: 'B.Tech',
          branch: 'Computer Science and Engineering',
          semester: 7,
          cgpa: 9.15,
          skills: ['Kotlin', 'Jetpack Compose', 'TypeScript', 'Node.js', 'System Design']
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.profile.fullName).toBe('Aarav Sharma (Updated)');
      expect(res.body.data.profile.studentProfile.cgpa).toBe(9.15);
      expect(res.body.data.profile.studentProfile.semester).toBe(7);
      expect(res.body.data.skills.length).toBeGreaterThanOrEqual(3);
    });
  });
});
