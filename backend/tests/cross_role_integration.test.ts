import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('PHASE 8 — Cross-Role Integration & Complete User Journeys', () => {
  let aspirantToken: string;
  let aspirantUserId: string;

  let studentToken: string;
  let studentUserId: string;

  let alumniToken: string;
  let alumniUserId: string;
  let mentorProfileId: string;

  let adminToken: string;
  let adminUserId: string;

  let testJobId: string;
  let testEventId: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // Ensure all demo users exist
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

    // 1. Authenticate / Retrieve Tokens for all 4 roles
    // Aspirant
    const aspLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'aspirant@campusverse.edu', password: 'Password123' });
    expect(aspLogin.status).toBe(200);
    aspirantToken = aspLogin.body.data.token;
    aspirantUserId = aspLogin.body.data.user.userId || aspLogin.body.data.user.id;

    // Student
    const stuLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student@campusverse.edu', password: 'Password123' });
    expect(stuLogin.status).toBe(200);
    studentToken = stuLogin.body.data.token;
    studentUserId = stuLogin.body.data.user.userId || stuLogin.body.data.user.id;

    // Alumni
    const aluLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'alumni@campusverse.edu', password: 'Password123' });
    expect(aluLogin.status).toBe(200);
    alumniToken = aluLogin.body.data.token;
    alumniUserId = aluLogin.body.data.user.userId || aluLogin.body.data.user.id;

    // Admin
    const admLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });
    expect(admLogin.status).toBe(200);
    adminToken = admLogin.body.data.token;
    adminUserId = admLogin.body.data.user.userId || admLogin.body.data.user.id;

    // Retrieve or ensure a mentor profile for Alumni
    let mentor = await prisma.mentorProfile.findFirst({ where: { userId: alumniUserId } });
    if (!mentor) {
      mentor = await prisma.mentorProfile.create({
        data: {
          userId: alumniUserId,
          title: 'Senior Software Engineer',
          company: 'Google',
          expertise: 'Cloud Architecture & Backend Systems',
          isAcceptingMentees: true,
          bio: 'Mentoring campus students in distributed systems.'
        }
      });
    }
    mentorProfileId = mentor.id;

    // Retrieve or create active job & event for testing
    let job = await prisma.job.findFirst({ where: { status: 'ACTIVE' } });
    if (!job) {
      let company = await prisma.company.findFirst();
      if (!company) {
        company = await prisma.company.create({
          data: {
            name: 'Google India Cross',
            website: 'https://careers.google.com',
            industry: 'Technology',
            description: 'Global tech company'
          }
        });
      }
      job = await prisma.job.create({
        data: {
          title: 'Software Engineer Cross',
          companyId: company.id,
          posterId: alumniUserId,
          roleType: 'FULL_TIME',
          location: 'Bangalore, India',
          description: 'Android dev job',
          status: 'ACTIVE'
        }
      });
    }
    testJobId = job.id;

    let event = await prisma.event.findFirst({ where: { status: 'UPCOMING' } });
    if (!event) {
      event = await prisma.event.create({
        data: {
          title: 'Campus Hackathon 2026',
          description: 'Annual hackathon',
          category: 'HACKATHON',
          startTime: new Date(Date.now() + 86400000),
          endTime: new Date(Date.now() + 172800000),
          location: 'Main Auditorium',
          organizerId: adminUserId,
          status: 'UPCOMING'
        }
      });
    }
    testEventId = event.id;
  });

  afterAll(async () => {
    // Keep connection pool alive for in-band test runner
  });

  // ---------------------------------------------------------------------------
  // 1. Role Isolation & Authentication Context
  // ---------------------------------------------------------------------------
  describe('1. Authentication & Role Context Isolation', () => {
    it('verifies that each role receives isolated identity context and permissions', async () => {
      const aspRes = await request(app).get('/api/v1/auth/me').set('Authorization', `Bearer ${aspirantToken}`);
      expect(aspRes.status).toBe(200);
      expect(aspRes.body.data.role).toBe('ASPIRANT');

      const stuRes = await request(app).get('/api/v1/auth/me').set('Authorization', `Bearer ${studentToken}`);
      expect(stuRes.status).toBe(200);
      expect(stuRes.body.data.role).toBe('STUDENT');

      const aluRes = await request(app).get('/api/v1/auth/me').set('Authorization', `Bearer ${alumniToken}`);
      expect(aluRes.status).toBe(200);
      expect(aluRes.body.data.role).toBe('ALUMNI');

      const admRes = await request(app).get('/api/v1/auth/me').set('Authorization', `Bearer ${adminToken}`);
      expect(admRes.status).toBe(200);
      expect(admRes.body.data.role).toBe('ADMIN');
    });
  });

  // ---------------------------------------------------------------------------
  // 2. Student <-> Alumni Connection Journey
  // ---------------------------------------------------------------------------
  describe('2. Student <-> Alumni Connection & Real-Event Notifications', () => {
    let connectionId: string;

    it('Student discovers alumni in network directory', async () => {
      const res = await request(app)
        .get('/api/v1/alumni')
        .set('Authorization', `Bearer ${studentToken}`);
      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
    });

    it('Student sends connection request to Alumni and triggers notification', async () => {
      // Clean previous connection if exists
      await prisma.userConnection.deleteMany({
        where: {
          OR: [
            { requesterId: studentUserId, receiverId: alumniUserId },
            { requesterId: alumniUserId, receiverId: studentUserId }
          ]
        }
      });

      const res = await request(app)
        .post(`/api/v1/alumni/${alumniUserId}/connect`)
        .set('Authorization', `Bearer ${studentToken}`);
      expect([200, 201]).toContain(res.status);
      connectionId = res.body.data.id;
      expect(connectionId).toBeDefined();

      // Verify Alumni received real notification
      const notifRes = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${alumniToken}`);
      expect(notifRes.status).toBe(200);
      const connNotif = notifRes.body.data.find((n: any) => n.type === 'CONNECTION');
      expect(connNotif).toBeDefined();
    });

    it('Alumni accepts connection request and triggers accept notification for Student', async () => {
      const res = await request(app)
        .patch(`/api/v1/alumni/connections/${connectionId}`)
        .set('Authorization', `Bearer ${alumniToken}`)
        .send({ status: 'ACCEPTED' });
      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('ACCEPTED');

      // Verify Student received connection accepted notification
      const stuNotifs = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${studentToken}`);
      expect(stuNotifs.status).toBe(200);
      const acceptNotif = stuNotifs.body.data.find((n: any) => n.type === 'CONNECTION' && n.title.includes('Accepted'));
      expect(acceptNotif).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // 3. Mentorship Cross-Role Flow
  // ---------------------------------------------------------------------------
  describe('3. Mentorship Request, Scheduling & Session Lifecycle', () => {
    let mentorshipRequestId: string;

    it('Student requests mentorship from Alumni Mentor', async () => {
      const res = await request(app)
        .post('/api/v1/mentorship/requests')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          mentorId: mentorProfileId,
          goal: 'Distributed Systems & Microservices Guidance',
          message: 'Hello! I am seeking guidance on cloud architecture and career prep.'
        });
      expect(res.status).toBe(201);
      mentorshipRequestId = res.body.data.id;
      expect(mentorshipRequestId).toBeDefined();

      // Verify Mentor received notification
      const notifs = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${alumniToken}`);
      const mentorNotif = notifs.body.data.find((n: any) => n.type === 'MENTORSHIP');
      expect(mentorNotif).toBeDefined();
    });

    it('Mentor accepts request, creating scheduled session and notifying mentee', async () => {
      const res = await request(app)
        .patch(`/api/v1/mentorship/requests/${mentorshipRequestId}`)
        .set('Authorization', `Bearer ${alumniToken}`)
        .send({ status: 'ACCEPTED' });
      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('ACCEPTED');

      // Verify mentee sees scheduled mentorship session
      const sessionsRes = await request(app)
        .get('/api/v1/mentorship/sessions')
        .set('Authorization', `Bearer ${studentToken}`);
      expect(sessionsRes.status).toBe(200);
      expect(sessionsRes.body.data.length).toBeGreaterThan(0);
      const session = sessionsRes.body.data.find((s: any) => s.requestId === mentorshipRequestId);
      expect(session).toBeDefined();
      expect(session.status).toBe('SCHEDULED');
    });
  });

  // ---------------------------------------------------------------------------
  // 4. Cross-Role Messaging
  // ---------------------------------------------------------------------------
  describe('4. Direct Messaging & Notification Delivery', () => {
    let conversationId: string;

    it('Student initiates a direct conversation with Alumni', async () => {
      const res = await request(app)
        .post('/api/v1/conversations')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({ recipientId: alumniUserId });
      expect([200, 201]).toContain(res.status);
      conversationId = res.body.data.id;
      expect(conversationId).toBeDefined();
    });

    it('Student sends a direct message to Alumni', async () => {
      const res = await request(app)
        .post(`/api/v1/conversations/${conversationId}/messages`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({ content: 'Hi Dr. Aisha! Thank you for accepting my mentorship request.' });
      expect(res.status).toBe(201);
      expect(res.body.data.content).toBe('Hi Dr. Aisha! Thank you for accepting my mentorship request.');

      // Verify Alumni received notification
      const notifs = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${alumniToken}`);
      const msgNotif = notifs.body.data.find((n: any) => n.type === 'MESSAGE');
      expect(msgNotif).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // 5. Job Application & Status Transitions
  // ---------------------------------------------------------------------------
  describe('5. Job Application & Employer/Admin Lifecycle', () => {
    let applicationId: string;

    it('Student/Alumni applies to an open job opportunity', async () => {
      if (!testJobId) return;

      // Clean prior application
      await prisma.jobApplication.deleteMany({
        where: { jobId: testJobId, applicantId: studentUserId }
      });

      const res = await request(app)
        .post(`/api/v1/jobs/${testJobId}/apply`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          resumeUrl: 'https://campusverse.edu/resumes/rohan_mehta.pdf',
          coverLetter: 'I am excited to apply for this engineering role.'
        });
      expect(res.status).toBe(201);
      applicationId = res.body.data.id;
      expect(applicationId).toBeDefined();
    });

    it('Admin updates application status and applicant is notified', async () => {
      if (!applicationId) return;

      const res = await request(app)
        .patch(`/api/v1/applications/${applicationId}/status`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ status: 'SHORTLISTED' });
      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('SHORTLISTED');

      // Verify applicant received status update notification
      const notifs = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${studentToken}`);
      const appNotif = notifs.body.data.find((n: any) => n.type === 'APPLICATION' && n.title.includes('Status Updated'));
      expect(appNotif).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // 6. Referral Flow
  // ---------------------------------------------------------------------------
  describe('6. Job Referral Request & Alumni Resolution', () => {
    it('Student requests a referral from Alumni and Alumni responds', async () => {
      const res = await request(app)
        .post('/api/v1/referrals')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          alumniId: alumniUserId,
          companyName: 'Google',
          notes: 'Applying for SWE II backend position.'
        });
      expect(res.status).toBe(201);
      const referralId = res.body.data.id;

      // Alumni accepts referral
      const updateRes = await request(app)
        .patch(`/api/v1/referrals/${referralId}/status`)
        .set('Authorization', `Bearer ${alumniToken}`)
        .send({ status: 'ACCEPTED', notes: 'Referred on internal Google portal.' });
      expect(updateRes.status).toBe(200);
      expect(updateRes.body.data.status).toBe('ACCEPTED');
    });
  });

  // ---------------------------------------------------------------------------
  // 7. Events Registration & Duplicate Prevention
  // ---------------------------------------------------------------------------
  describe('7. Events Registration & Verification', () => {
    it('Student registers for an event and duplicate registration is prevented', async () => {
      if (!testEventId) return;

      // Clean prior registration
      await prisma.eventRegistration.deleteMany({
        where: { eventId: testEventId, userId: studentUserId }
      });

      const res = await request(app)
        .post(`/api/v1/events/${testEventId}/register`)
        .set('Authorization', `Bearer ${studentToken}`);
      expect(res.status).toBe(201);

      // Second attempt must fail with 409 Conflict
      const dupRes = await request(app)
        .post(`/api/v1/events/${testEventId}/register`)
        .set('Authorization', `Bearer ${studentToken}`);
      expect(dupRes.status).toBe(409);
    });
  });

  // ---------------------------------------------------------------------------
  // 8. Identity Document Verification & Admin Approval
  // ---------------------------------------------------------------------------
  describe('8. Document Verification Submission & Admin Approval', () => {
    let verificationId: string;

    it('Student submits student ID document for verification', async () => {
      const res = await request(app)
        .post('/api/v1/verifications')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          documentType: 'STUDENT_ID',
          documentUrl: 'https://storage.campusverse.edu/docs/student_id.png'
        });
      expect(res.status).toBe(201);
      verificationId = res.body.data.id;
      expect(verificationId).toBeDefined();
    });

    it('Admin reviews and approves verification document', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/verifications/${verificationId}/review`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ status: 'APPROVED' });
      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('APPROVED');

      // Verify Student received verification approval notification
      const notifs = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${studentToken}`);
      const verNotif = notifs.body.data.find((n: any) => n.title.includes('Verification Approved'));
      expect(verNotif).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // 9. Admin Broadcast Announcements
  // ---------------------------------------------------------------------------
  describe('9. Admin Broadcast Announcements Delivery', () => {
    it('Admin broadcasts an announcement to all students', async () => {
      const res = await request(app)
        .post('/api/v1/admin/announcements')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          title: 'Campus Hackathon Registration Open',
          content: 'Registration for the annual CampusVerse Hackathon 2026 is now live!',
          targetRole: 'STUDENT',
          priority: 'HIGH'
        });
      expect(res.status).toBe(201);
      expect(res.body.data.deliveredCount).toBeGreaterThan(0);

      // Verify Student received the broadcast notification
      const notifs = await request(app)
        .get('/api/v1/notifications')
        .set('Authorization', `Bearer ${studentToken}`);
      const hackNotif = notifs.body.data.find((n: any) => n.title.includes('Hackathon'));
      expect(hackNotif).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // 10. Security Abuse & Negative Authorization Matrix
  // ---------------------------------------------------------------------------
  describe('10. Security Abuse & Negative Authorization Matrix', () => {
    it('Student cannot access Admin Dashboard or moderation APIs (403)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${studentToken}`);
      expect(res.status).toBe(403);
    });

    it('Alumni cannot access Admin Dashboard or moderation APIs (403)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${alumniToken}`);
      expect(res.status).toBe(403);
    });

    it('Aspirant cannot access Admin Dashboard or moderation APIs (403)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/dashboard')
        .set('Authorization', `Bearer ${aspirantToken}`);
      expect(res.status).toBe(403);
    });

    it('Student cannot respond to another user’s connection requests (403)', async () => {
      // Clean previous connection if exists
      await prisma.userConnection.deleteMany({
        where: {
          OR: [
            { requesterId: aspirantUserId, receiverId: alumniUserId },
            { requesterId: alumniUserId, receiverId: aspirantUserId }
          ]
        }
      });

      // Create a dummy connection between Alumni and Aspirant
      const fakeConn = await prisma.userConnection.create({
        data: {
          requesterId: aspirantUserId,
          receiverId: alumniUserId,
          status: 'PENDING'
        }
      });

      // Student tries to respond
      const res = await request(app)
        .patch(`/api/v1/alumni/connections/${fakeConn.id}`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({ status: 'ACCEPTED' });
      expect(res.status).toBe(403);

      // Clean up
      await prisma.userConnection.delete({ where: { id: fakeConn.id } });
    });

    it('Student cannot modify another user’s mentorship session (403)', async () => {
      // Create a dummy session between Admin and Alumni
      const dummyReq = await prisma.mentorshipRequest.create({
        data: {
          mentorId: mentorProfileId,
          menteeId: adminUserId,
          goal: 'Executive Mentorship',
          message: 'Discussion on university systems.',
          status: 'ACCEPTED'
        }
      });
      const dummySession = await prisma.mentorshipSession.create({
        data: {
          requestId: dummyReq.id,
          scheduledAt: new Date(),
          status: 'SCHEDULED'
        }
      });

      // Student tries to update
      const res = await request(app)
        .patch(`/api/v1/mentorship/sessions/${dummySession.id}`)
        .set('Authorization', `Bearer ${studentToken}`)
        .send({ notes: 'Hacked Notes' });
      expect(res.status).toBe(403);

      // Clean up
      await prisma.mentorshipSession.delete({ where: { id: dummySession.id } });
      await prisma.mentorshipRequest.delete({ where: { id: dummyReq.id } });
    });
  });
});
