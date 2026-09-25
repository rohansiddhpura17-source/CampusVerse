import request from 'supertest';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';
import { env } from '../src/config/env';
import { revokeAllUserSessions, revokeToken, signToken } from '../src/utils/jwt';
import { resolveRateLimiterStore, MemoryRateLimiterStore, RedisRateLimiterStore } from '../src/services/rate-limiter.service';

describe('Admin Security Hardening & RBAC Verification Suite', () => {
  let superAdminToken: string;
  let superAdminId: string;
  let normalAdminToken: string;
  let normalAdminId: string;
  let moderatorToken: string;
  let moderatorId: string;
  let contentManagerToken: string;
  let contentManagerId: string;
  let studentToken: string;
  let studentId: string;
  let targetUserId: string;

  const timestamp = Date.now();
  const superAdminEmail = `super_${timestamp}@campusverse.edu`;
  const normalAdminEmail = `analyst_${timestamp}@campusverse.edu`;
  const moderatorEmail = `moderator_${timestamp}@campusverse.edu`;
  const contentManagerEmail = `cm_${timestamp}@campusverse.edu`;
  const studentEmail = `student_${timestamp}@campusverse.edu`;
  const targetEmail = `target_${timestamp}@campusverse.edu`;
  const defaultPassword = 'TestPassword123!';

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash(defaultPassword, 10);

    // 1. Fetch system roles
    const superAdminRole = await prisma.role.findUnique({ where: { name: 'SUPER_ADMIN' } });
    const analyticsAdminRole = await prisma.role.findUnique({ where: { name: 'ANALYTICS_ADMIN' } });
    const moderatorRole = await prisma.role.findUnique({ where: { name: 'MODERATOR' } });
    const contentManagerRole = await prisma.role.findUnique({ where: { name: 'CONTENT_MANAGER' } });

    // 2. Create Super Admin
    const superAdmin = await prisma.user.create({
      data: {
        email: superAdminEmail,
        passwordHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: {
          create: {
            fullName: 'Super Admin Test',
            adminProfile: { create: { accessLevel: 'SUPERADMIN' } },
          },
        },
        userRoles: {
          create: { roleId: superAdminRole!.id },
        },
      },
    });
    superAdminId = superAdmin.id;

    // 3. Create Normal Admin (with only ANALYTICS_ADMIN role, lacks users.suspend / settings.manage)
    const normalAdmin = await prisma.user.create({
      data: {
        email: normalAdminEmail,
        passwordHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: {
          create: {
            fullName: 'Analytics Admin Test',
            adminProfile: { create: { accessLevel: 'STANDARD' } },
          },
        },
        userRoles: {
          create: { roleId: analyticsAdminRole!.id },
        },
      },
    });
    normalAdminId = normalAdmin.id;

    // 4. Create Moderator (has moderation.read & moderation.manage, lacks settings.manage)
    const moderator = await prisma.user.create({
      data: {
        email: moderatorEmail,
        passwordHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: {
          create: {
            fullName: 'Moderator Test',
            adminProfile: { create: { accessLevel: 'STANDARD' } },
          },
        },
        userRoles: {
          create: { roleId: moderatorRole!.id },
        },
      },
    });
    moderatorId = moderator.id;

    // 5. Create Content Manager (has content.manage, lacks users.suspend and settings.manage)
    const contentManager = await prisma.user.create({
      data: {
        email: contentManagerEmail,
        passwordHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: {
          create: {
            fullName: 'Content Manager Test',
            adminProfile: { create: { accessLevel: 'STANDARD' } },
          },
        },
        userRoles: {
          create: { roleId: contentManagerRole!.id },
        },
      },
    });
    contentManagerId = contentManager.id;

    // 6. Create Normal Student
    const student = await prisma.user.create({
      data: {
        email: studentEmail,
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Student Test' } },
      },
    });
    studentId = student.id;

    // 7. Create Target User for suspension/reactivation tests
    const target = await prisma.user.create({
      data: {
        email: targetEmail,
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Target User' } },
      },
    });
    targetUserId = target.id;

    // Generate tokens
    superAdminToken = signToken({
      userId: superAdminId,
      email: superAdminEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 1,
    });

    normalAdminToken = signToken({
      userId: normalAdminId,
      email: normalAdminEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 1,
    });

    moderatorToken = signToken({
      userId: moderatorId,
      email: moderatorEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 1,
    });

    contentManagerToken = signToken({
      userId: contentManagerId,
      email: contentManagerEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 1,
    });

    studentToken = signToken({
      userId: studentId,
      email: studentEmail,
      role: 'STUDENT',
      isAdminAuthorized: false,
      sessionVersion: 1,
    });
  });

  afterAll(async () => {
    // Cleanup disposable test users
    await prisma.user.deleteMany({
      where: {
        id: { in: [superAdminId, normalAdminId, moderatorId, contentManagerId, studentId, targetUserId] },
      },
    });
  });

  // ---------------------------------------------------------------------------
  // 1. Unauthenticated Admin API → 401
  // ---------------------------------------------------------------------------
  it('1. Unauthenticated request to admin dashboard returns 401 UNAUTHORIZED', async () => {
    const res = await request(app).get('/api/v1/admin/dashboard');
    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });

  // ---------------------------------------------------------------------------
  // 2. Normal Student → 403
  // ---------------------------------------------------------------------------
  it('2. Normal student accessing admin dashboard returns 403 FORBIDDEN', async () => {
    const res = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${studentToken}`);
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });

  // ---------------------------------------------------------------------------
  // 3. Normal Admin without Permission → 403
  // ---------------------------------------------------------------------------
  it('3. Admin without settings.manage permission is rejected from /admin/system/settings with 403', async () => {
    const res = await request(app)
      .get('/api/v1/admin/system/settings')
      .set('Authorization', `Bearer ${normalAdminToken}`);
    expect(res.status).toBe(403);
    expect(res.body.error?.code).toBe('PERMISSION_DENIED');
  });

  // ---------------------------------------------------------------------------
  // 4. Authorized Admin → 200
  // ---------------------------------------------------------------------------
  it('4. Authorized SUPER_ADMIN accessing admin dashboard returns 200 OK', async () => {
    const res = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // 5. SUPER_ADMIN Role Assignment
  // ---------------------------------------------------------------------------
  it('5. SUPER_ADMIN can assign a role to a user', async () => {
    const role = await prisma.role.findUnique({ where: { name: 'MODERATOR' } });
    const res = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/roles`)
      .set('Authorization', `Bearer ${superAdminToken}`)
      .send({ roleId: role!.id });
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);

    // Verify assignment in database
    const assignment = await prisma.userRole.findFirst({
      where: { userId: targetUserId, roleId: role!.id },
    });
    expect(assignment).not.toBeNull();
  });

  // ---------------------------------------------------------------------------
  // 6. Unauthorized Role Escalation → 403
  // ---------------------------------------------------------------------------
  it('6. Non-super admin cannot assign SUPER_ADMIN role (403 UNAUTHORIZED_ROLE_ESCALATION)', async () => {
    const superRole = await prisma.role.findUnique({ where: { name: 'SUPER_ADMIN' } });
    const res = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/roles`)
      .set('Authorization', `Bearer ${normalAdminToken}`)
      .send({ roleId: superRole!.id });
    expect(res.status).toBe(403);
  });

  // ---------------------------------------------------------------------------
  // 7. Self-Role Escalation → 403
  // ---------------------------------------------------------------------------
  it('7. Administrator cannot assign or escalate their own roles (403 CANNOT_MODIFY_OWN_ROLES)', async () => {
    const adminRole = await prisma.role.findUnique({ where: { name: 'ADMIN' } });
    const res = await request(app)
      .post(`/api/v1/admin/users/${superAdminId}/roles`)
      .set('Authorization', `Bearer ${superAdminToken}`)
      .send({ roleId: adminRole!.id });
    expect(res.status).toBe(403);
    expect(res.body.error?.code).toBe('CANNOT_MODIFY_OWN_ROLES');
  });

  // ---------------------------------------------------------------------------
  // 8. User Suspension
  // ---------------------------------------------------------------------------
  it('8. Super admin can suspend a user account and record audit log', async () => {
    const res = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/suspend`)
      .set('Authorization', `Bearer ${superAdminToken}`)
      .send({ reason: 'Violated platform academic integrity guidelines' });
    expect(res.status).toBe(200);
    expect(res.body.data.isActive).toBe(false);

    // Verify user in DB is inactive
    const updated = await prisma.user.findUnique({ where: { id: targetUserId } });
    expect(updated?.isActive).toBe(false);
  });

  // ---------------------------------------------------------------------------
  // 9. User Reactivation
  // ---------------------------------------------------------------------------
  it('9. Super admin can reactivate a suspended user account', async () => {
    const res = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/reactivate`)
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(res.status).toBe(200);
    expect(res.body.data.isActive).toBe(true);

    const updated = await prisma.user.findUnique({ where: { id: targetUserId } });
    expect(updated?.isActive).toBe(true);
  });

  // ---------------------------------------------------------------------------
  // 10. Audit Creation
  // ---------------------------------------------------------------------------
  it('10. Administrative actions automatically generate sanitized audit log entries', async () => {
    const auditLogs = await prisma.auditLog.findMany({
      where: { targetId: targetUserId },
      orderBy: { timestamp: 'desc' },
    });
    expect(auditLogs.length).toBeGreaterThanOrEqual(1);
    const actions = auditLogs.map((a) => a.action);
    expect(actions).toContain('ADMIN_SUSPENDED_USER');
  });

  // ---------------------------------------------------------------------------
  // 11. Audit Modification Attempt → Rejected (405)
  // ---------------------------------------------------------------------------
  it('11. Direct audit log modification (PUT/PATCH) is strictly rejected with 405 AUDIT_LOG_IMMUTABLE', async () => {
    const res = await request(app)
      .patch('/api/v1/admin/audit-logs/arbitrary-id')
      .set('Authorization', `Bearer ${superAdminToken}`)
      .send({ action: 'TAMPERED_ACTION' });
    expect(res.status).toBe(405);
    expect(res.body.error?.code).toBe('AUDIT_LOG_IMMUTABLE');
  });

  // ---------------------------------------------------------------------------
  // 12. Audit Deletion Attempt → Rejected (405)
  // ---------------------------------------------------------------------------
  it('12. Direct audit log deletion (DELETE) is strictly rejected with 405 AUDIT_LOG_IMMUTABLE', async () => {
    const res = await request(app)
      .delete('/api/v1/admin/audit-logs/arbitrary-id')
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(res.status).toBe(405);
    expect(res.body.error?.code).toBe('AUDIT_LOG_IMMUTABLE');
  });

  // ---------------------------------------------------------------------------
  // 13. Feature Flag Modification
  // ---------------------------------------------------------------------------
  it('13. Authorized administrator can toggle a feature flag', async () => {
    const res = await request(app)
      .patch('/api/v1/admin/system/feature-flags/enable_ai_career_readiness')
      .set('Authorization', `Bearer ${superAdminToken}`)
      .send({ isEnabled: true, targetRoles: 'STUDENT,ALUMNI' });
    expect(res.status).toBe(200);
    expect(res.body.data.isEnabled).toBe(true);
  });

  // ---------------------------------------------------------------------------
  // 14. Unauthorized Feature Flag Modification → 403
  // ---------------------------------------------------------------------------
  it('14. Unauthorized admin without settings.manage cannot toggle feature flags (403)', async () => {
    const res = await request(app)
      .patch('/api/v1/admin/system/feature-flags/enable_ai_career_readiness')
      .set('Authorization', `Bearer ${normalAdminToken}`)
      .send({ isEnabled: false });
    expect(res.status).toBe(403);
  });

  // ---------------------------------------------------------------------------
  // 15. Invalid Input → 400
  // ---------------------------------------------------------------------------
  it('15. Malformed payload to admin endpoints fails schema validation with 400', async () => {
    const res = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/suspend`)
      .set('Authorization', `Bearer ${superAdminToken}`)
      .send({ reason: '' }); // Invalid: min length 3
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  // ---------------------------------------------------------------------------
  // 16. Expired Session → 401
  // ---------------------------------------------------------------------------
  it('16. Expired JWT token returns 401 INVALID_TOKEN', async () => {
    const expiredToken = jwt.sign(
      { userId: superAdminId, email: superAdminEmail, role: 'ADMIN', isAdminAuthorized: true },
      env.JWT_SECRET,
      { expiresIn: '-10s' }
    );
    const res = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${expiredToken}`);
    expect(res.status).toBe(401);
    expect(res.body.error?.code).toBe('INVALID_TOKEN');
  });

  // ---------------------------------------------------------------------------
  // 17. Revoked Session → 401
  // ---------------------------------------------------------------------------
  it('17. Revoked session token returns 401', async () => {
    const tempToken = jwt.sign(
      { userId: superAdminId, email: superAdminEmail, role: 'ADMIN', isAdminAuthorized: true },
      env.JWT_SECRET,
      { expiresIn: '10m' }
    );

    // Verify token works before revocation
    const validRes = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${tempToken}`);
    expect(validRes.status).toBe(200);

    // Revoke the token
    revokeToken(tempToken);

    // Now verify request is rejected
    const revokedRes = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${tempToken}`);
    expect(revokedRes.status).toBe(401);
  });

  // ---------------------------------------------------------------------------
  // 18. Rate Limiting Headers
  // ---------------------------------------------------------------------------
  it('18. Admin API responses include standard rate limiting headers', async () => {
    const res = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(res.headers).toHaveProperty('x-ratelimit-limit');
    expect(res.headers).toHaveProperty('x-ratelimit-remaining');
    expect(res.headers).toHaveProperty('x-ratelimit-reset');
  });

  // ---------------------------------------------------------------------------
  // 19. RLS / Private Data Access Isolation
  // ---------------------------------------------------------------------------
  it('19. System Settings endpoint never leaks sensitive environment credentials', async () => {
    const res = await request(app)
      .get('/api/v1/admin/system/settings')
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(res.status).toBe(200);
    const jsonStr = JSON.stringify(res.body);
    expect(jsonStr).not.toContain(env.JWT_SECRET);
    expect(jsonStr).not.toContain(env.DATABASE_URL);
    expect(res.body.data.serverTelemetry).toBeDefined();
    expect(res.body.data.serverTelemetry.databaseEngine).toContain('PostgreSQL');
  });

  // ---------------------------------------------------------------------------
  // 20. Granular RBAC: MODERATOR Access Scope
  // ---------------------------------------------------------------------------
  it('20. MODERATOR can access safety reports queue but is forbidden from system settings', async () => {
    // Allowed: reports (moderation.read)
    const reportsRes = await request(app)
      .get('/api/v1/admin/reports')
      .set('Authorization', `Bearer ${moderatorToken}`);
    expect(reportsRes.status).toBe(200);

    // Forbidden: system settings (requires settings.manage)
    const settingsRes = await request(app)
      .get('/api/v1/admin/system/settings')
      .set('Authorization', `Bearer ${moderatorToken}`);
    expect(settingsRes.status).toBe(403);
    expect(settingsRes.body.error?.code).toBe('PERMISSION_DENIED');
  });

  // ---------------------------------------------------------------------------
  // 21. Granular RBAC: CONTENT_MANAGER Access Scope
  // ---------------------------------------------------------------------------
  it('21. CONTENT_MANAGER can access colleges but is forbidden from user suspensions', async () => {
    // Allowed: colleges (content.manage)
    const collegesRes = await request(app)
      .get('/api/v1/admin/colleges')
      .set('Authorization', `Bearer ${contentManagerToken}`);
    expect(collegesRes.status).toBe(200);

    // Forbidden: user suspension (requires users.update)
    const suspendRes = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/suspend`)
      .set('Authorization', `Bearer ${contentManagerToken}`)
      .send({ reason: 'Unauthorized suspension test' });
    expect(suspendRes.status).toBe(403);
    expect(suspendRes.body.error?.code).toBe('PERMISSION_DENIED');
  });

  // ---------------------------------------------------------------------------
  // 22. Granular RBAC: ANALYTICS_ADMIN Access Scope
  // ---------------------------------------------------------------------------
  it('22. ANALYTICS_ADMIN can access analytics but is forbidden from role assignments', async () => {
    // Allowed: analytics (analytics.read)
    const analyticsRes = await request(app)
      .get('/api/v1/admin/analytics')
      .set('Authorization', `Bearer ${normalAdminToken}`);
    expect(analyticsRes.status).toBe(200);

    // Forbidden: role assignment (requires users.update)
    const role = await prisma.role.findUnique({ where: { name: 'MODERATOR' } });
    const assignRes = await request(app)
      .post(`/api/v1/admin/users/${targetUserId}/roles`)
      .set('Authorization', `Bearer ${normalAdminToken}`)
      .send({ roleId: role!.id });
    expect(assignRes.status).toBe(403);
    expect(assignRes.body.error?.code).toBe('PERMISSION_DENIED');
  });

  // ---------------------------------------------------------------------------
  // 23. Admin Login creates persistent AdminSession in DB
  // ---------------------------------------------------------------------------
  it('23. Administrative login successfully creates persistent AdminSession record', async () => {
    const loginRes = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: superAdminEmail, password: defaultPassword });

    expect(loginRes.status).toBe(200);
    expect(loginRes.body.data.token).toBeDefined();
    expect(loginRes.body.data.user.roles).toContain('SUPER_ADMIN');

    const adminSession = await prisma.adminSession.findFirst({
      where: { userId: superAdminId, revokedAt: null },
      orderBy: { createdAt: 'desc' },
    });
    expect(adminSession).not.toBeNull();
    expect(adminSession?.deviceName).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // 24. Listing Admin Sessions Endpoint
  // ---------------------------------------------------------------------------
  it('24. GET /api/v1/admin/security/sessions lists active administrative sessions with telemetry', async () => {
    const res = await request(app)
      .get('/api/v1/admin/security/sessions')
      .set('Authorization', `Bearer ${superAdminToken}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.data.sessions)).toBe(true);
    expect(res.body.data.sessions.length).toBeGreaterThanOrEqual(1);

    const firstSession = res.body.data.sessions[0];
    expect(firstSession).toHaveProperty('id');
    expect(firstSession).toHaveProperty('deviceName');
    expect(firstSession).toHaveProperty('status');
    expect(firstSession).toHaveProperty('isCurrent');
  });

  // ---------------------------------------------------------------------------
  // 25. Single Session Revocation
  // ---------------------------------------------------------------------------
  it('25. POST /api/v1/admin/security/sessions/:id/revoke marks target session as revoked', async () => {
    const testJti = `test-session-${Date.now()}`;
    const disposableSession = await prisma.adminSession.create({
      data: {
        userId: superAdminId,
        tokenId: testJti,
        deviceName: 'Test Session Client',
        ipAddress: '127.0.0.1',
        userAgent: 'Jest Test Runner',
        expiresAt: new Date(Date.now() + 3600000),
      },
    });

    const revokeRes = await request(app)
      .post(`/api/v1/admin/security/sessions/${disposableSession.id}/revoke`)
      .set('Authorization', `Bearer ${superAdminToken}`);

    expect(revokeRes.status).toBe(200);
    expect(revokeRes.body.data.revoked).toBe(true);

    const updatedSession = await prisma.adminSession.findUnique({
      where: { id: disposableSession.id },
    });
    expect(updatedSession?.revokedAt).not.toBeNull();
    expect(updatedSession?.revokedReason).toBe('ADMIN_REVOKED');
  });

  // ---------------------------------------------------------------------------
  // 26. Revoked AdminSession Token Rejection
  // ---------------------------------------------------------------------------
  it('26. Token associated with a revoked AdminSession is rejected with 401 SESSION_REVOKED', async () => {
    const testJti = `revoked-session-${Date.now()}`;
    await prisma.adminSession.create({
      data: {
        userId: superAdminId,
        tokenId: testJti,
        deviceName: 'Revoked Test Client',
        expiresAt: new Date(Date.now() + 3600000),
        revokedAt: new Date(),
        revokedReason: 'ADMIN_REVOKED',
      },
    });

    const tokenForRevokedSession = signToken({
      userId: superAdminId,
      email: superAdminEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 1,
      jti: testJti,
    });

    const res = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${tokenForRevokedSession}`);

    expect(res.status).toBe(401);
    expect(res.body.error?.code).toBe('SESSION_REVOKED');
  });

  // ---------------------------------------------------------------------------
  // 27. Unauthorized Session Revocation → 403
  // ---------------------------------------------------------------------------
  it('27. Non-super admin cannot revoke sessions belonging to another administrator (403)', async () => {
    const testJti = `super-session-${Date.now()}`;
    const superSession = await prisma.adminSession.create({
      data: {
        userId: superAdminId,
        tokenId: testJti,
        deviceName: 'Super Admin Terminal',
        expiresAt: new Date(Date.now() + 3600000),
      },
    });

    const res = await request(app)
      .post(`/api/v1/admin/security/sessions/${superSession.id}/revoke`)
      .set('Authorization', `Bearer ${normalAdminToken}`);

    expect(res.status).toBe(403);
    expect(res.body.error?.code).toBe('FORBIDDEN');
  });

  // ---------------------------------------------------------------------------
  // 28. Persistent sessionVersion Invalidation
  // ---------------------------------------------------------------------------
  it('28. Old token is rejected with 401 SESSION_REVOKED after sessionVersion increment', async () => {
    // User has sessionVersion: 1 in token
    const tokenV1 = signToken({
      userId: superAdminId,
      email: superAdminEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 1,
    });

    // Token works initially
    const initRes = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${tokenV1}`);
    expect(initRes.status).toBe(200);

    // Bump sessionVersion in DB to 2
    await prisma.user.update({
      where: { id: superAdminId },
      data: { sessionVersion: 2 },
    });

    // Old token with sessionVersion 1 is now rejected
    const invalidatedRes = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${tokenV1}`);
    expect(invalidatedRes.status).toBe(401);
    expect(invalidatedRes.body.error?.code).toBe('SESSION_REVOKED');

    // New token with sessionVersion 2 works seamlessly
    const tokenV2 = signToken({
      userId: superAdminId,
      email: superAdminEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: 2,
    });
    const validRes = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${tokenV2}`);
    expect(validRes.status).toBe(200);

    // Reset back to version 2 for superAdminToken
    superAdminToken = tokenV2;
  });

  // ---------------------------------------------------------------------------
  // 29. Password Update Does NOT Invalidate Session without sessionVersion Change
  // ---------------------------------------------------------------------------
  it('29. Password update alone does not invalidate session when sessionVersion remains unchanged', async () => {
    // Update lastPasswordChange in securitySettings to now
    await prisma.securitySettings.upsert({
      where: { userId: superAdminId },
      update: { lastPasswordChange: new Date() },
      create: { userId: superAdminId, lastPasswordChange: new Date() },
    });

    // Request with superAdminToken still succeeds
    const res = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(res.status).toBe(200);
  });

  // ---------------------------------------------------------------------------
  // 30. Revoke All Sessions Endpoint
  // ---------------------------------------------------------------------------
  it('30. POST /api/v1/admin/security/sessions/revoke-all increments sessionVersion and revokes DB sessions', async () => {
    const userBefore = await prisma.user.findUnique({ where: { id: superAdminId } });
    const versionBefore = userBefore!.sessionVersion;

    const res = await request(app)
      .post('/api/v1/admin/security/sessions/revoke-all')
      .set('Authorization', `Bearer ${superAdminToken}`);

    expect(res.status).toBe(200);
    expect(res.body.data.revokedAll).toBe(true);

    const userAfter = await prisma.user.findUnique({ where: { id: superAdminId } });
    expect(userAfter!.sessionVersion).toBe(versionBefore + 1);

    // Old token is now rejected
    const checkRes = await request(app)
      .get('/api/v1/admin/dashboard')
      .set('Authorization', `Bearer ${superAdminToken}`);
    expect(checkRes.status).toBe(401);
    expect(checkRes.body.error?.code).toBe('SESSION_REVOKED');

    // Update superAdminToken to new sessionVersion for any remaining assertions
    superAdminToken = signToken({
      userId: superAdminId,
      email: superAdminEmail,
      role: 'ADMIN',
      isAdminAuthorized: true,
      sessionVersion: userAfter!.sessionVersion,
    });
  });

  // ---------------------------------------------------------------------------
  // 31. Distributed Rate Limiter Readiness
  // ---------------------------------------------------------------------------
  it('31. resolveRateLimiterStore selects RedisRateLimiterStore when URL provided, MemoryRateLimiterStore otherwise', () => {
    const memoryStore = resolveRateLimiterStore();
    expect(memoryStore).toBeInstanceOf(MemoryRateLimiterStore);

    const redisStore = resolveRateLimiterStore('redis://127.0.0.1:6379');
    expect(redisStore).toBeInstanceOf(RedisRateLimiterStore);
  });

  // ---------------------------------------------------------------------------
  // 32. Security Audit Trail Verification
  // ---------------------------------------------------------------------------
  it('32. Administrative security events create proper audit log records', async () => {
    const securityLogs = await prisma.auditLog.findMany({
      where: {
        actorId: superAdminId,
        action: {
          in: ['ADMIN_LOGIN', 'ADMIN_SESSION_REVOKED', 'ADMIN_ALL_SESSIONS_REVOKED'],
        },
      },
    });

    expect(securityLogs.length).toBeGreaterThanOrEqual(1);
    const actions = securityLogs.map((l) => l.action);
    expect(actions).toContain('ADMIN_ALL_SESSIONS_REVOKED');
  });

  // ---------------------------------------------------------------------------
  // 33. Last Active SUPER_ADMIN Safeguard
  // ---------------------------------------------------------------------------
  it('33. Revoking the last active SUPER_ADMIN is rejected with 403 CANNOT_DELETE_LAST_SUPER_ADMIN', async () => {
    const superRole = await prisma.role.findUnique({ where: { name: 'SUPER_ADMIN' } });
    expect(superRole).not.toBeNull();

    // Create a temporary target user with SUPER_ADMIN role but isActive: false
    // This ensures total active super admins in the system is exactly 1 (the test superAdminId)
    const tempTarget = await prisma.user.create({
      data: {
        email: `last_super_target_${Date.now()}@campusverse.edu`,
        passwordHash: await bcrypt.hash('Password123!', 10),
        role: 'ADMIN',
        isActive: false,
        isEmailVerified: true,
        isAdminAuthorized: true,
        userRoles: {
          create: { roleId: superRole!.id },
        },
      },
    });

    try {
      const res = await request(app)
        .delete(`/api/v1/admin/users/${tempTarget.id}/roles/${superRole!.id}`)
        .set('Authorization', `Bearer ${superAdminToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('CANNOT_DELETE_LAST_SUPER_ADMIN');
    } finally {
      // Clean up temporary target
      await prisma.userRole.deleteMany({ where: { userId: tempTarget.id } });
      await prisma.user.delete({ where: { id: tempTarget.id } }).catch(() => {});
    }
  });

  // ---------------------------------------------------------------------------
  // 34. Password Reset with OTP Increments sessionVersion
  // ---------------------------------------------------------------------------
  it('34. Password reset via OTP increments sessionVersion and invalidates old tokens with 401 SESSION_REVOKED', async () => {
    const resetTestEmail = `pwd_reset_${Date.now()}@campusverse.edu`;
    const pwdUser = await prisma.user.create({
      data: {
        email: resetTestEmail,
        passwordHash: await bcrypt.hash('OldPassword123!', 10),
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        sessionVersion: 1,
      },
    });

    const oldToken = signToken({
      userId: pwdUser.id,
      email: pwdUser.email,
      role: 'STUDENT',
      isAdminAuthorized: false,
      sessionVersion: 1,
    });

    const { otpService } = await import('../src/services/otp.service');
    const rawOtp = '777888';
    const otpHash = otpService.hashOtp(rawOtp);
    await prisma.otpToken.create({
      data: {
        email: resetTestEmail,
        otpHash,
        purpose: 'PASSWORD_RESET',
        expiresAt: new Date(Date.now() + 10 * 60 * 1000),
        isUsed: false,
        attemptCount: 0,
        maxAttempts: 5,
      },
    });

    const resetRes = await request(app)
      .post('/api/v1/auth/reset-password')
      .send({
        email: resetTestEmail,
        otp: rawOtp,
        newPassword: 'BrandNewSecurePassword123!',
      });

    expect(resetRes.status).toBe(200);
    expect(resetRes.body.success).toBe(true);

    const updatedUser = await prisma.user.findUnique({ where: { id: pwdUser.id } });
    expect(updatedUser!.sessionVersion).toBe(2);

    const testReq = await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', `Bearer ${oldToken}`);

    expect(testReq.status).toBe(401);
    expect(testReq.body.error.code).toBe('SESSION_REVOKED');

    await prisma.user.delete({ where: { id: pwdUser.id } }).catch(() => {});
  });

  // ---------------------------------------------------------------------------
  // 35. Multi-Role RBAC: Base STUDENT with ANALYTICS_ADMIN
  // ---------------------------------------------------------------------------
  it('35. User with base role STUDENT but assigned ANALYTICS_ADMIN can access analytics, but is forbidden (403) from settings', async () => {
    const analyticsEmail = `student_analyst_${Date.now()}@campusverse.edu`;
    const analyticsRole = await prisma.role.findUnique({ where: { name: 'ANALYTICS_ADMIN' } });
    expect(analyticsRole).not.toBeNull();

    const analystUser = await prisma.user.create({
      data: {
        email: analyticsEmail,
        passwordHash: await bcrypt.hash('Password123!', 10),
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: false,
        userRoles: {
          create: { roleId: analyticsRole!.id },
        },
      },
    });

    const analystToken = signToken({
      userId: analystUser.id,
      email: analystUser.email,
      role: 'STUDENT',
      isAdminAuthorized: false,
      sessionVersion: 1,
    });

    const analyticsRes = await request(app)
      .get('/api/v1/admin/analytics')
      .set('Authorization', `Bearer ${analystToken}`);

    expect(analyticsRes.status).toBe(200);
    expect(analyticsRes.body.success).toBe(true);

    const settingsRes = await request(app)
      .get('/api/v1/admin/system/settings')
      .set('Authorization', `Bearer ${analystToken}`);

    expect(settingsRes.status).toBe(403);
    expect(settingsRes.body.error.code).toBe('PERMISSION_DENIED');

    await prisma.user.delete({ where: { id: analystUser.id } }).catch(() => {});
  });

  // ---------------------------------------------------------------------------
  // 36. Multi-Role RBAC: Base STUDENT with MODERATOR
  // ---------------------------------------------------------------------------
  it('36. User with base role STUDENT but assigned MODERATOR can access reports, but is forbidden (403) from settings', async () => {
    const modEmail = `student_mod_${Date.now()}@campusverse.edu`;
    const modRole = await prisma.role.findUnique({ where: { name: 'MODERATOR' } });
    expect(modRole).not.toBeNull();

    const modUser = await prisma.user.create({
      data: {
        email: modEmail,
        passwordHash: await bcrypt.hash('Password123!', 10),
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: false,
        userRoles: {
          create: { roleId: modRole!.id },
        },
      },
    });

    const modToken = signToken({
      userId: modUser.id,
      email: modUser.email,
      role: 'STUDENT',
      isAdminAuthorized: false,
      sessionVersion: 1,
    });

    const reportsRes = await request(app)
      .get('/api/v1/admin/reports')
      .set('Authorization', `Bearer ${modToken}`);

    expect(reportsRes.status).toBe(200);
    expect(reportsRes.body.success).toBe(true);

    const settingsRes = await request(app)
      .get('/api/v1/admin/system/settings')
      .set('Authorization', `Bearer ${modToken}`);

    expect(settingsRes.status).toBe(403);
    expect(settingsRes.body.error.code).toBe('PERMISSION_DENIED');

    await prisma.user.delete({ where: { id: modUser.id } }).catch(() => {});
  });

  // ---------------------------------------------------------------------------
  // 37. Multi-Role RBAC: Base STUDENT with CONTENT_MANAGER
  // ---------------------------------------------------------------------------
  it('37. User with base role STUDENT but assigned CONTENT_MANAGER can access colleges, but is forbidden (403) from reports', async () => {
    const cmEmail = `student_cm_${Date.now()}@campusverse.edu`;
    const cmRole = await prisma.role.findUnique({ where: { name: 'CONTENT_MANAGER' } });
    expect(cmRole).not.toBeNull();

    const cmUser = await prisma.user.create({
      data: {
        email: cmEmail,
        passwordHash: await bcrypt.hash('Password123!', 10),
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: false,
        userRoles: {
          create: { roleId: cmRole!.id },
        },
      },
    });

    const cmToken = signToken({
      userId: cmUser.id,
      email: cmUser.email,
      role: 'STUDENT',
      isAdminAuthorized: false,
      sessionVersion: 1,
    });

    const collegesRes = await request(app)
      .get('/api/v1/admin/colleges')
      .set('Authorization', `Bearer ${cmToken}`);

    expect(collegesRes.status).toBe(200);
    expect(collegesRes.body.success).toBe(true);

    const reportsRes = await request(app)
      .get('/api/v1/admin/reports')
      .set('Authorization', `Bearer ${cmToken}`);

    expect(reportsRes.status).toBe(403);
    expect(reportsRes.body.error.code).toBe('PERMISSION_DENIED');

    await prisma.user.delete({ where: { id: cmUser.id } }).catch(() => {});
  });
});
