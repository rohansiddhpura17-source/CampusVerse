import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';

describe('Admin Authority & Notes Moderation Lifecycle Tests', () => {
  let adminToken: string;
  let adminUserId: string;
  let studentAToken: string;
  let studentAId: string;
  let studentBToken: string;
  let studentBId: string;
  let aspirantToken: string;
  let alumniToken: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // Ensure Admin exists
    const adminUser = await prisma.user.upsert({
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
    adminUserId = adminUser.id;

    // Ensure Student A exists
    const studentA = await prisma.user.upsert({
      where: { email: 'student_a@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'student_a@campusverse.edu',
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Student Alice' } }
      }
    });
    studentAId = studentA.id;

    // Ensure Student B exists
    const studentB = await prisma.user.upsert({
      where: { email: 'student_b@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'student_b@campusverse.edu',
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Student Bob' } }
      }
    });
    studentBId = studentB.id;

    // Ensure Aspirant exists
    await prisma.user.upsert({
      where: { email: 'aspirant@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'aspirant@campusverse.edu',
        passwordHash,
        role: 'ASPIRANT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Aspirant User' } }
      }
    });

    // Ensure Alumni exists
    await prisma.user.upsert({
      where: { email: 'alumni@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true },
      create: {
        email: 'alumni@campusverse.edu',
        passwordHash,
        role: 'ALUMNI',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Alumni User' } }
      }
    });

    // Log in all actors
    const adminRes = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'admin@campusverse.edu', password: 'Password123' });
    adminToken = adminRes.body.data.token;

    const studentARes = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student_a@campusverse.edu', password: 'Password123' });
    studentAToken = studentARes.body.data.token;

    const studentBRes = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'student_b@campusverse.edu', password: 'Password123' });
    studentBToken = studentBRes.body.data.token;

    const aspirantRes = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'aspirant@campusverse.edu', password: 'Password123' });
    aspirantToken = aspirantRes.body.data.token;

    const alumniRes = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'alumni@campusverse.edu', password: 'Password123' });
    alumniToken = alumniRes.body.data.token;
  });

  describe('1. Student Note Upload & Initial Moderation State', () => {
    let createdNoteId: string;

    it('should create note in PENDING_REVIEW state when submitted by student', async () => {
      const res = await request(app)
        .post('/api/v1/notes')
        .set('Authorization', `Bearer ${studentAToken}`)
        .send({
          title: 'Distributed Systems Lecture 1',
          description: 'Overview of consensus and Paxos',
          fileUrl: 'https://campusverse.edu/notes/ds_lec1.pdf',
          tags: 'distributed-systems,cs602'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.status).toBe('PENDING_REVIEW');
      expect(res.body.data.isPublic).toBe(false);
      createdNoteId = res.body.data.id;
    });

    it('should NOT display PENDING_REVIEW notes in the public catalog', async () => {
      const res = await request(app)
        .get('/api/v1/notes?search=Distributed+Systems+Lecture+1')
        .set('Authorization', `Bearer ${studentBToken}`);

      expect(res.status).toBe(200);
      const found = res.body.data.find((n: any) => n.id === createdNoteId);
      expect(found).toBeUndefined();
    });

    it('should allow the student creator to view their own PENDING_REVIEW note via My Notes', async () => {
      const res = await request(app)
        .get('/api/v1/notes/my')
        .set('Authorization', `Bearer ${studentAToken}`);

      expect(res.status).toBe(200);
      const found = res.body.data.find((n: any) => n.id === createdNoteId);
      expect(found).toBeDefined();
      expect(found.status).toBe('PENDING_REVIEW');
    });

    it('should DENY another student from viewing the unpublished note directly by ID', async () => {
      const res = await request(app)
        .get(`/api/v1/notes/${createdNoteId}`)
        .set('Authorization', `Bearer ${studentBToken}`);

      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('should allow the author to view their unpublished note by ID', async () => {
      const res = await request(app)
        .get(`/api/v1/notes/${createdNoteId}`)
        .set('Authorization', `Bearer ${studentAToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.id).toBe(createdNoteId);
    });
  });

  describe('2. Student Direct Delete Restriction (Server-side Enforced)', () => {
    let noteToDeleteId: string;

    beforeAll(async () => {
      const note = await prisma.note.create({
        data: {
          userId: studentAId,
          title: 'Algorithms Midterm Notes',
          fileUrl: 'https://campusverse.edu/notes/algo.pdf',
          status: 'PENDING_REVIEW',
          isPublic: false
        }
      });
      noteToDeleteId = note.id;
    });

    it('should DENY student from deleting their own note directly (403 Forbidden)', async () => {
      const res = await request(app)
        .delete(`/api/v1/notes/${noteToDeleteId}`)
        .set('Authorization', `Bearer ${studentAToken}`);

      expect(res.status).toBe(403);
      expect(res.body.error.message).toContain('Students cannot delete notes directly');
      expect(res.body.error.code).toBe('FORBIDDEN');

      // Verify note still exists in database
      const noteInDb = await prisma.note.findUnique({ where: { id: noteToDeleteId } });
      expect(noteInDb).not.toBeNull();
    });
  });

  describe('3. Student Removal Request Workflow', () => {
    let publishedNoteId: string;

    beforeAll(async () => {
      const note = await prisma.note.create({
        data: {
          userId: studentAId,
          title: 'Compiler Design Notes',
          fileUrl: 'https://campusverse.edu/notes/compiler.pdf',
          status: 'PUBLISHED',
          isPublic: true
        }
      });
      publishedNoteId = note.id;
    });

    it('should DENY another student from requesting removal of someone else note', async () => {
      const res = await request(app)
        .post(`/api/v1/notes/${publishedNoteId}/request-removal`)
        .set('Authorization', `Bearer ${studentBToken}`)
        .send({ reason: 'I do not like this note' });

      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('should allow student creator to request removal with a valid reason', async () => {
      const res = await request(app)
        .post(`/api/v1/notes/${publishedNoteId}/request-removal`)
        .set('Authorization', `Bearer ${studentAToken}`)
        .send({ reason: 'Outdated syllabus content' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('REMOVAL_REQUESTED');
      expect(res.body.data.removalReason).toBe('Outdated syllabus content');

      // Verify in DB
      const inDb = await prisma.note.findUnique({ where: { id: publishedNoteId } });
      expect(inDb?.status).toBe('REMOVAL_REQUESTED');
      expect(inDb?.removalReason).toBe('Outdated syllabus content');
    });

    it('should reject duplicate removal request if already pending review', async () => {
      const res = await request(app)
        .post(`/api/v1/notes/${publishedNoteId}/request-removal`)
        .set('Authorization', `Bearer ${studentAToken}`)
        .send({ reason: 'Second request' });

      expect(res.status).toBe(400);
      expect(res.body.error.message).toContain('already pending review');
    });
  });

  describe('4. Server-Side Admin Authorization & Role Enforcement', () => {
    it('should REJECT unauthenticated access to admin notes endpoint (401)', async () => {
      const res = await request(app).get('/api/v1/admin/notes');
      expect(res.status).toBe(401);
    });

    it('should REJECT Student access to admin notes endpoint (403)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/notes')
        .set('Authorization', `Bearer ${studentAToken}`);
      expect(res.status).toBe(403);
    });

    it('should REJECT Aspirant access to admin notes endpoint (403)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/notes')
        .set('Authorization', `Bearer ${aspirantToken}`);
      expect(res.status).toBe(403);
    });

    it('should REJECT Alumni access to admin notes endpoint (403)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/notes')
        .set('Authorization', `Bearer ${alumniToken}`);
      expect(res.status).toBe(403);
    });

    it('should REJECT Student attempting admin note moderation (403)', async () => {
      const res = await request(app)
        .patch('/api/v1/admin/notes/fake-id/moderate')
        .set('Authorization', `Bearer ${studentAToken}`)
        .send({ action: 'APPROVE' });
      expect(res.status).toBe(403);
    });
  });

  describe('5. Admin Notes Moderation (Approve, Reject, Remove, Restore, Audit)', () => {
    let pendingNoteId: string;
    let removalNoteId: string;

    beforeEach(async () => {
      const note1 = await prisma.note.create({
        data: {
          userId: studentAId,
          title: 'Database Architecture Notes',
          fileUrl: 'https://campusverse.edu/notes/db.pdf',
          status: 'PENDING_REVIEW',
          isPublic: false
        }
      });
      pendingNoteId = note1.id;

      const note2 = await prisma.note.create({
        data: {
          userId: studentBId,
          title: 'Network Security Lab Notes',
          fileUrl: 'https://campusverse.edu/notes/sec.pdf',
          status: 'REMOVAL_REQUESTED',
          removalReason: 'Accidental duplicate upload',
          isPublic: true
        }
      });
      removalNoteId = note2.id;
    });

    it('should allow Admin to list notes with counts and filters', async () => {
      const res = await request(app)
        .get('/api/v1/admin/notes?status=PENDING_REVIEW')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.meta).toBeDefined();
      expect(res.body.meta.pendingReviewCount).toBeGreaterThanOrEqual(1);
    });

    it('should allow Admin to APPROVE pending note and write AuditLog', async () => {
      const res = await request(app)
        .patch(`/api/v1/admin/notes/${pendingNoteId}/moderate`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ action: 'APPROVE' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('PUBLISHED');
      expect(res.body.data.isPublic).toBe(true);
      expect(res.body.data.reviewedBy).toBe(adminUserId);

      // Verify AuditLog written
      const auditLog = await prisma.auditLog.findFirst({
        where: { targetId: pendingNoteId, action: 'ADMIN_APPROVE_NOTE' },
        orderBy: { timestamp: 'desc' }
      });
      expect(auditLog).not.toBeNull();
      expect(auditLog?.actorId).toBe(adminUserId);
      expect(auditLog?.targetType).toBe('NOTE');
    });

    it('should allow Admin to REJECT pending note with reason and write AuditLog', async () => {
      const res = await request(app)
        .patch(`/api/v1/admin/notes/${pendingNoteId}/moderate`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ action: 'REJECT', reason: 'Contains copyrighted textbook extracts' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('REJECTED');
      expect(res.body.data.rejectionReason).toBe('Contains copyrighted textbook extracts');

      // Verify in public catalog (must NOT appear)
      const catalogRes = await request(app)
        .get(`/api/v1/notes/${pendingNoteId}`)
        .set('Authorization', `Bearer ${studentBToken}`);
      expect(catalogRes.status).toBe(403);
    });

    it('should allow Admin to REMOVE a note (approving removal request) and write AuditLog', async () => {
      const res = await request(app)
        .patch(`/api/v1/admin/notes/${removalNoteId}/moderate`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ action: 'REMOVE', reason: 'Removal request approved' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('REMOVED');
      expect(res.body.data.isPublic).toBe(false);
      expect(res.body.data.removedBy).toBe(adminUserId);

      // Verify AuditLog written
      const auditLog = await prisma.auditLog.findFirst({
        where: { targetId: removalNoteId, action: 'ADMIN_REMOVE_NOTE' },
        orderBy: { timestamp: 'desc' }
      });
      expect(auditLog).not.toBeNull();
      expect(auditLog?.actorId).toBe(adminUserId);
    });

    it('should allow Admin to RESTORE a removed or rejected note to PUBLISHED', async () => {
      const res = await request(app)
        .patch(`/api/v1/admin/notes/${removalNoteId}/moderate`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ action: 'RESTORE' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('PUBLISHED');
      expect(res.body.data.isPublic).toBe(true);
    });

    it('should allow Admin to create note that publishes immediately', async () => {
      const res = await request(app)
        .post('/api/v1/notes')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          title: 'Campus Guidelines & Syllabus 2026',
          description: 'Official academic syllabus reference',
          fileUrl: 'https://campusverse.edu/notes/syllabus_2026.pdf',
          tags: 'official,syllabus'
        });

      expect(res.status).toBe(201);
      expect(res.body.data.status).toBe('PUBLISHED');
      expect(res.body.data.isPublic).toBe(true);

      // Verify AuditLog for admin creation
      const auditLog = await prisma.auditLog.findFirst({
        where: { targetId: res.body.data.id, action: 'ADMIN_CREATED_NOTE' }
      });
      expect(auditLog).not.toBeNull();
    });

    it('should allow Admin to permanently delete note via DELETE /api/v1/notes/:id', async () => {
      const res = await request(app)
        .delete(`/api/v1/notes/${pendingNoteId}`)
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      const inDb = await prisma.note.findUnique({ where: { id: pendingNoteId } });
      expect(inDb).toBeNull();
    });
  });

  describe('6. Student Editing Policy (Re-moderation on Published Edit)', () => {
    let editableNoteId: string;

    beforeEach(async () => {
      const note = await prisma.note.create({
        data: {
          userId: studentAId,
          title: 'Python for Data Science',
          description: 'Original text',
          fileUrl: 'https://campusverse.edu/notes/python.pdf',
          status: 'PUBLISHED',
          isPublic: true
        }
      });
      editableNoteId = note.id;
    });

    it('should reset PUBLISHED note to PENDING_REVIEW when student edits it', async () => {
      const res = await request(app)
        .patch(`/api/v1/notes/${editableNoteId}`)
        .set('Authorization', `Bearer ${studentAToken}`)
        .send({ description: 'Updated text with additional chapters' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('PENDING_REVIEW');
      expect(res.body.data.isPublic).toBe(false);

      // Verify no longer in public catalog
      const catalogRes = await request(app)
        .get(`/api/v1/notes?search=Python+for+Data+Science`)
        .set('Authorization', `Bearer ${studentBToken}`);
      const found = catalogRes.body.data.find((n: any) => n.id === editableNoteId);
      expect(found).toBeUndefined();
    });
  });
});
