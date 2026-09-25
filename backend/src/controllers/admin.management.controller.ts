import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { getUserPermissions } from '../middleware/rbac.middleware';
import { sendSuccess, sendError } from '../utils/response';

// -----------------------------------------------------------------------------
// Validation Schemas
// -----------------------------------------------------------------------------

export const suspendUserSchema = z.object({
  reason: z.string().min(3, 'Reason must be at least 3 characters').max(500),
});

export const patchUserSchema = z.object({
  role: z.enum(['STUDENT', 'ASPIRANT', 'ALUMNI', 'ADMIN']).optional(),
  isActive: z.boolean().optional(),
  isEmailVerified: z.boolean().optional(),
  isAdminAuthorized: z.boolean().optional(),
  name: z.string().min(2).max(100).optional(),
});

export const collegeSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters').max(200),
  code: z.string().min(2).max(50).toUpperCase(),
  domain: z.string().optional().nullable(),
  address: z.string().optional().nullable(),
  city: z.string().optional().nullable(),
  state: z.string().optional().nullable(),
  country: z.string().default('India'),
  verified: z.boolean().default(false),
  ranking: z.number().int().optional().nullable(),
  acceptanceRate: z.number().min(0).max(100).optional().nullable(),
  averageFees: z.string().optional().nullable(),
  overview: z.string().optional().nullable(),
  campusSize: z.string().optional().nullable(),
  websiteUrl: z.string().url().optional().nullable(),
  logoUrl: z.string().url().optional().nullable(),
});

export const patchCollegeSchema = collegeSchema.partial();

export const patchReportSchema = z.object({
  status: z.enum(['OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED', 'ESCALATED', 'PENDING', 'INVESTIGATING']),
  resolutionNotes: z.string().max(1000).optional(),
  actionTaken: z.enum(['WARN', 'REMOVE', 'SUSPEND', 'DISMISS', 'RESOLVE', 'NONE']).default('NONE'),
});


export const resolveModerationSchema = z.object({
  action: z.enum(['APPROVED', 'REJECTED', 'FLAGGED', 'REMOVED', 'SUSPENDED']),
  targetType: z.string().default('CONTENT'),
  reason: z.string().optional(),
  notes: z.string().optional(),
});

// -----------------------------------------------------------------------------
// 1. User Management Extension
// -----------------------------------------------------------------------------

export async function suspendUser(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const { reason } = req.body;
  const adminId = req.user!.userId;

  // SAFEGUARD 1: Self-suspension guard
  if (id === adminId) {
    sendError(res, 'Privilege Guard: Administrators cannot suspend their own account.', 403, 'CANNOT_SUSPEND_SELF');
    return;
  }

  const user = await prisma.user.findUnique({
    where: { id },
    include: {
      userRoles: { include: { role: true } },
      profile: { include: { adminProfile: true } },
    },
  });

  if (!user) {
    sendError(res, 'User not found.', 404, 'NOT_FOUND');
    return;
  }

  const isTargetSuperAdmin =
    user.userRoles.some((ur) => ur.role.name === 'SUPER_ADMIN') ||
    user.profile?.adminProfile?.accessLevel === 'SUPERADMIN';

  // SAFEGUARD 2: Only SUPER_ADMIN can suspend an admin
  const isTargetAdmin = isTargetSuperAdmin || user.role === 'ADMIN' || user.userRoles.some((ur) => ur.role.name === 'ADMIN');
  const { roles: actorRoles } = await getUserPermissions(adminId);
  const isActorSuper = actorRoles.includes('SUPER_ADMIN');

  if (isTargetAdmin && !isActorSuper) {
    sendError(res, 'Unauthorized: Only SUPER_ADMIN can suspend an administrative user.', 403, 'UNAUTHORIZED_PRIVILEGE_CHANGE');
    return;
  }

  // SAFEGUARD 3: Prevent suspending the last active SUPER_ADMIN
  if (isTargetSuperAdmin) {
    const activeSuperAdmins = await prisma.userRole.count({
      where: {
        role: { name: 'SUPER_ADMIN' },
        user: { isActive: true },
      },
    });

    if (activeSuperAdmins <= 1) {
      sendError(res, 'Critical System Safeguard: Cannot suspend the last active SUPER_ADMIN.', 403, 'CANNOT_DELETE_LAST_SUPER_ADMIN');
      return;
    }
  }

  const updated = await prisma.user.update({
    where: { id },
    data: { isActive: false },
    select: { id: true, email: true, isActive: true, role: true, updatedAt: true },
  });

  await logAudit(adminId, 'ADMIN_SUSPENDED_USER', 'USER', id, {
    reason,
    oldState: { isActive: user.isActive },
    newState: { isActive: false },
  });

  sendSuccess(res, updated, `User account ${user.email} suspended successfully.`);
}

export async function reactivateUser(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;

  const user = await prisma.user.findUnique({
    where: { id },
    select: { id: true, email: true, isActive: true, role: true },
  });

  if (!user) {
    sendError(res, 'User not found.', 404, 'NOT_FOUND');
    return;
  }

  if (user.isActive) {
    sendError(res, 'User is already active.', 400, 'USER_ALREADY_ACTIVE');
    return;
  }

  const updated = await prisma.user.update({
    where: { id },
    data: { isActive: true },
    select: { id: true, email: true, isActive: true, role: true, updatedAt: true },
  });

  await logAudit(adminId, 'ADMIN_REACTIVATED_USER', 'USER', id, {
    oldState: { isActive: false },
    newState: { isActive: true },
  });

  sendSuccess(res, updated, `User account ${user.email} reactivated successfully.`);
}

export async function patchUser(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;
  const { role, isActive, isEmailVerified, isAdminAuthorized, name } = req.body;

  // SAFEGUARD 1: Self-role escalation / modification guard
  if (id === adminId && (role !== undefined || isAdminAuthorized !== undefined)) {
    sendError(res, 'Privilege Escalation Guard: Administrators cannot alter their own role or administrative privileges.', 403, 'CANNOT_MODIFY_OWN_ROLES');
    return;
  }

  const existingUser = await prisma.user.findUnique({
    where: { id },
    include: {
      profile: true,
      userRoles: { include: { role: true } },
    },
  });

  if (!existingUser) {
    sendError(res, 'User not found.', 404, 'NOT_FOUND');
    return;
  }

  // SAFEGUARD 2: Granting ADMIN or isAdminAuthorized requires SUPER_ADMIN
  const { roles: actorRoles } = await getUserPermissions(adminId);
  const isActorSuper = actorRoles.includes('SUPER_ADMIN');

  if ((role === 'ADMIN' || isAdminAuthorized === true) && !isActorSuper) {
    sendError(res, 'Unauthorized role escalation: Only SUPER_ADMIN can grant administrator status.', 403, 'UNAUTHORIZED_ROLE_ESCALATION');
    return;
  }

  // SAFEGUARD 3: Prevent demoting last active SUPER_ADMIN
  if (role && role !== 'ADMIN') {
    const isTargetSuper = existingUser.userRoles.some((ur) => ur.role.name === 'SUPER_ADMIN');
    if (isTargetSuper) {
      const activeSuperCount = await prisma.userRole.count({
        where: {
          role: { name: 'SUPER_ADMIN' },
          user: { isActive: true },
        },
      });
      if (activeSuperCount <= 1) {
        sendError(res, 'Critical System Safeguard: Cannot demote the last active SUPER_ADMIN.', 403, 'CANNOT_DELETE_LAST_SUPER_ADMIN');
        return;
      }
    }
  }

  const updateUserData: any = {};
  if (role !== undefined) updateUserData.role = role;
  if (isActive !== undefined) updateUserData.isActive = isActive;
  if (isEmailVerified !== undefined) updateUserData.isEmailVerified = isEmailVerified;
  if (isAdminAuthorized !== undefined) updateUserData.isAdminAuthorized = isAdminAuthorized;

  const updatedUser = await prisma.$transaction(async (tx) => {
    if (name && existingUser.profile) {
      await tx.profile.update({
        where: { userId: id },
        data: { fullName: name },
      });
    }

    return tx.user.update({
      where: { id },
      data: updateUserData,
      select: {
        id: true,
        email: true,
        role: true,
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: { select: { fullName: true } },
      },
    });
  });

  await logAudit(adminId, 'ADMIN_UPDATED_USER', 'USER', id, {
    modifiedFields: req.body,
    oldState: {
      role: existingUser.role,
      isActive: existingUser.isActive,
      isAdminAuthorized: existingUser.isAdminAuthorized,
    },
    newState: {
      role: updatedUser.role,
      isActive: updatedUser.isActive,
      isAdminAuthorized: updatedUser.isAdminAuthorized,
    },
  });

  sendSuccess(res, updatedUser, 'User updated successfully.');

}

// -----------------------------------------------------------------------------
// 2. Specialized Cohort Lists: Students, Aspirants, Alumni
// -----------------------------------------------------------------------------

export async function getAdminStudents(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const institutionId = req.query.institutionId as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (institutionId) where.institutionId = institutionId;

  if (search) {
    where.OR = [
      { studentIdNumber: { contains: search, mode: 'insensitive' } },
      { major: { contains: search, mode: 'insensitive' } },
      { profile: { fullName: { contains: search, mode: 'insensitive' } } },
      { profile: { user: { email: { contains: search, mode: 'insensitive' } } } },
    ];
  }

  const [students, total] = await Promise.all([
    prisma.studentProfile.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        institution: { select: { id: true, name: true, code: true } },
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
            user: { select: { id: true, email: true, isActive: true, isEmailVerified: true, createdAt: true } },
          },
        },
      },
    }),
    prisma.studentProfile.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: students,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Students retrieved'
  );
}

export async function getAdminAspirants(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (search) {
    where.OR = [
      { targetDegree: { contains: search, mode: 'insensitive' } },
      { targetMajor: { contains: search, mode: 'insensitive' } },
      { highSchool: { contains: search, mode: 'insensitive' } },
      { profile: { fullName: { contains: search, mode: 'insensitive' } } },
      { profile: { user: { email: { contains: search, mode: 'insensitive' } } } },
    ];
  }

  const [aspirants, total] = await Promise.all([
    prisma.aspirantProfile.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
            user: { select: { id: true, email: true, isActive: true, isEmailVerified: true, createdAt: true } },
          },
        },
      },
    }),
    prisma.aspirantProfile.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: aspirants,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Aspirants retrieved'
  );
}

export async function getAdminAlumni(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const company = req.query.company as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (company) where.currentCompany = { contains: company, mode: 'insensitive' };
  if (search) {
    where.OR = [
      { currentCompany: { contains: search, mode: 'insensitive' } },
      { currentDesignation: { contains: search, mode: 'insensitive' } },
      { industry: { contains: search, mode: 'insensitive' } },
      { profile: { fullName: { contains: search, mode: 'insensitive' } } },
      { profile: { user: { email: { contains: search, mode: 'insensitive' } } } },
    ];
  }

  const [alumni, total] = await Promise.all([
    prisma.alumniProfile.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        institution: { select: { id: true, name: true, code: true } },
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
            user: { select: { id: true, email: true, isActive: true, isEmailVerified: true, createdAt: true } },
          },
        },
      },
    }),
    prisma.alumniProfile.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: alumni,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Alumni retrieved'
  );
}

// -----------------------------------------------------------------------------
// 3. Colleges / Institutions CRUD
// -----------------------------------------------------------------------------

export async function getAdminColleges(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const verified = req.query.verified !== undefined ? req.query.verified === 'true' : undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (verified !== undefined) where.verified = verified;
  if (search) {
    where.OR = [
      { name: { contains: search, mode: 'insensitive' } },
      { code: { contains: search, mode: 'insensitive' } },
      { city: { contains: search, mode: 'insensitive' } },
      { state: { contains: search, mode: 'insensitive' } },
    ];
  }

  const [colleges, total] = await Promise.all([
    prisma.institution.findMany({
      where,
      skip,
      take: limit,
      orderBy: { name: 'asc' },
      include: {
        _count: {
          select: {
            studentProfiles: true,
            alumniProfiles: true,
            courses: true,
          },
        },
      },
    }),
    prisma.institution.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: colleges,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Colleges retrieved'
  );
}

export async function createAdminCollege(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const data = req.body;

  const existing = await prisma.institution.findUnique({
    where: { code: data.code.toUpperCase() },
  });

  if (existing) {
    sendError(res, `College with code '${data.code}' already exists.`, 409, 'CONFLICT');
    return;
  }

  const college = await prisma.institution.create({
    data: {
      ...data,
      code: data.code.toUpperCase(),
    },
  });

  await logAudit(adminId, 'ADMIN_CREATED_COLLEGE', 'INSTITUTION', college.id, {
    name: college.name,
    code: college.code,
  });

  sendSuccess(res, college, 'College created successfully.', 201);
}

export async function patchAdminCollege(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;
  const updates = req.body;

  const existing = await prisma.institution.findUnique({ where: { id } });
  if (!existing) {
    sendError(res, 'College not found.', 404, 'NOT_FOUND');
    return;
  }

  const college = await prisma.institution.update({
    where: { id },
    data: updates,
  });

  await logAudit(adminId, 'ADMIN_UPDATED_COLLEGE', 'INSTITUTION', id, {
    changes: updates,
  });

  sendSuccess(res, college, 'College updated successfully.');
}

export async function deleteAdminCollege(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;

  const existing = await prisma.institution.findUnique({
    where: { id },
    include: {
      _count: { select: { studentProfiles: true, alumniProfiles: true } },
    },
  });

  if (!existing) {
    sendError(res, 'College not found.', 404, 'NOT_FOUND');
    return;
  }

  if (existing._count.studentProfiles > 0 || existing._count.alumniProfiles > 0) {
    sendError(
      res,
      `Cannot delete college with active students (${existing._count.studentProfiles}) or alumni (${existing._count.alumniProfiles}).`,
      400,
      'HAS_ACTIVE_DEPENDENTS'
    );
    return;
  }

  await prisma.institution.delete({ where: { id } });

  await logAudit(adminId, 'ADMIN_DELETED_COLLEGE', 'INSTITUTION', id, {
    name: existing.name,
    code: existing.code,
  });

  sendSuccess(res, { deleted: true }, 'College removed successfully.');
}

// -----------------------------------------------------------------------------
// 4. Courses & Scholarships
// -----------------------------------------------------------------------------

export async function getAdminCourses(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (search) {
    where.OR = [
      { title: { contains: search, mode: 'insensitive' } },
      { code: { contains: search, mode: 'insensitive' } },
      { description: { contains: search, mode: 'insensitive' } },
    ];
  }

  const [courses, total] = await Promise.all([
    prisma.course.findMany({
      where,
      skip,
      take: limit,
      orderBy: { code: 'asc' },
      include: {
        institution: { select: { id: true, name: true, code: true } },
      },
    }),
    prisma.course.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: courses,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Courses retrieved'
  );
}

export async function getAdminScholarships(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (search) {
    where.OR = [
      { title: { contains: search, mode: 'insensitive' } },
      { provider: { contains: search, mode: 'insensitive' } },
      { description: { contains: search, mode: 'insensitive' } },
    ];
  }

  const [scholarships, total] = await Promise.all([
    prisma.scholarship.findMany({
      where,
      skip,
      take: limit,
      orderBy: { deadline: 'asc' },
      include: {
        _count: { select: { applications: true } },
      },
    }),
    prisma.scholarship.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: scholarships,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Scholarships retrieved'
  );
}

// -----------------------------------------------------------------------------
// 5. Projects Showcase Moderation
// -----------------------------------------------------------------------------

export async function getAdminProjects(req: Request, res: Response): Promise<void> {
  const page = parseInt((req.query.page as string) || '1', 10);
  const limit = parseInt((req.query.limit as string) || '20', 10);
  const search = req.query.search as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (search) {
    where.OR = [
      { title: { contains: search, mode: 'insensitive' } },
      { tagLine: { contains: search, mode: 'insensitive' } },
    ];
  }

  const [projects, total] = await Promise.all([
    prisma.project.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } },
          },
        },
        technologies: true,
        _count: { select: { members: true } },
      },
    }),
    prisma.project.count({ where }),
  ]);

  sendSuccess(
    res,
    {
      data: projects,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Projects retrieved'
  );
}

// -----------------------------------------------------------------------------
// 6. Reports & Unified Moderation
// -----------------------------------------------------------------------------

export async function patchAdminReport(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;
  const { status, resolutionNotes, actionTaken } = req.body;

  const report = await prisma.report.findUnique({ where: { id } });
  if (!report) {
    sendError(res, 'Report not found.', 404, 'NOT_FOUND');
    return;
  }

  const updated = await prisma.report.update({
    where: { id },
    data: {
      status,
      resolutionNotes,
      reviewerId: adminId,
    },
  });

  await logAudit(adminId, 'ADMIN_RESOLVED_REPORT', 'REPORT', id, {
    actionTaken,
    status,
    notes: resolutionNotes,
  });

  sendSuccess(res, updated, 'Report updated successfully.');
}

export async function resolveModeration(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;
  const { action, targetType, reason, notes } = req.body;

  const record = await prisma.moderationRecord.create({
    data: {
      targetId: id,
      targetType,
      moderatorId: adminId,
      action,
      reason,
      notes,
    },
  });

  await logAudit(adminId, `ADMIN_MODERATION_${action}`, targetType, id, {
    reason,
    notes,
  });

  sendSuccess(res, record, 'Moderation action recorded successfully.');
}

// -----------------------------------------------------------------------------
// 7. Audit Logs (Paginated & Filterable)
// -----------------------------------------------------------------------------

export async function getAdminAuditLogs(req: Request, res: Response): Promise<void> {
  const page = Math.max(1, parseInt((req.query.page as string) || '1', 10));
  const rawLimit = parseInt((req.query.limit as string) || '25', 10);
  const limit = Math.min(Math.max(1, isNaN(rawLimit) ? 25 : rawLimit), 100);
  const action = req.query.action as string | undefined;
  const targetType = req.query.targetType as string | undefined;
  const targetId = req.query.targetId as string | undefined;
  const actorId = req.query.actorId as string | undefined;
  const search = req.query.search as string | undefined;
  const moduleName = req.query.module as string | undefined;
  const startDate = req.query.startDate as string | undefined;
  const endDate = req.query.endDate as string | undefined;
  const skip = (page - 1) * limit;

  const where: any = {};
  if (action && action !== 'ALL') where.action = { contains: action, mode: 'insensitive' };
  if (targetType && targetType !== 'ALL') where.targetType = targetType;
  if (targetId) where.targetId = targetId;
  if (actorId) where.actorId = actorId;
  if (moduleName) where.action = { contains: moduleName, mode: 'insensitive' };

  if (startDate || endDate) {
    where.timestamp = {};
    if (startDate) where.timestamp.gte = new Date(startDate);
    if (endDate) where.timestamp.lte = new Date(endDate);
  }

  if (search) {
    where.OR = [
      { action: { contains: search, mode: 'insensitive' } },
      { targetType: { contains: search, mode: 'insensitive' } },
      { targetId: { contains: search, mode: 'insensitive' } },
      { actor: { email: { contains: search, mode: 'insensitive' } } },
      { actor: { profile: { fullName: { contains: search, mode: 'insensitive' } } } },
    ];
  }

  const [logs, total] = await Promise.all([
    prisma.auditLog.findMany({
      where,
      skip,
      take: limit,
      orderBy: { timestamp: 'desc' },
      include: {
        actor: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } },
          },
        },
      },
    }),
    prisma.auditLog.count({ where }),
  ]);


  sendSuccess(
    res,
    {
      data: logs,
      pagination: { total, page, limit, totalPages: Math.ceil(total / limit) },
    },
    'Audit logs retrieved'
  );
}

// -----------------------------------------------------------------------------
// 8. Real-Time Telemetry & Analytics
// -----------------------------------------------------------------------------

export async function getAdminAnalytics(req: Request, res: Response): Promise<void> {
  const [
    totalUsers,
    activeUsers,
    studentCount,
    aspirantCount,
    alumniCount,
    adminCount,
    totalEvents,
    eventRegistrations,
    totalProjects,
    totalMentors,
    mentorshipRequests,
    totalReports,
    resolvedReports,
    aiChatSessions,
    mockInterviews,
    aiRecommendations,
  ] = await Promise.all([
    prisma.user.count(),
    prisma.user.count({ where: { isActive: true } }),
    prisma.user.count({ where: { role: 'STUDENT' } }),
    prisma.user.count({ where: { role: 'ASPIRANT' } }),
    prisma.user.count({ where: { role: 'ALUMNI' } }),
    prisma.user.count({ where: { role: 'ADMIN' } }),
    prisma.event.count(),
    prisma.eventRegistration.count(),
    prisma.project.count(),
    prisma.mentorProfile.count(),
    prisma.mentorshipRequest.count(),
    prisma.report.count(),
    prisma.report.count({ where: { status: 'RESOLVED' } }),
    prisma.aIChatSession.count().catch(() => 0),
    prisma.interviewSession.count(),
    prisma.aIRecommendation.count(),
  ]);

  // Aggregate user growth by date (last 7 days)
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

  const recentUsers = await prisma.user.findMany({
    where: { createdAt: { gte: sevenDaysAgo } },
    select: { createdAt: true, role: true },
  });

  const dailyTrendMap: Record<string, { date: string; count: number }> = {};
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = d.toISOString().split('T')[0];
    dailyTrendMap[dateStr] = { date: dateStr, count: 0 };
  }

  for (const u of recentUsers) {
    const dateStr = u.createdAt.toISOString().split('T')[0];
    if (dailyTrendMap[dateStr]) {
      dailyTrendMap[dateStr].count++;
    }
  }

  sendSuccess(
    res,
    {
      overview: {
        totalUsers,
        activeUsers,
        totalEvents,
        totalProjects,
        totalReports,
        resolvedReports,
      },
      roleDistribution: {
        students: studentCount,
        aspirants: aspirantCount,
        alumni: alumniCount,
        admins: adminCount,
      },
      aiTelemetry: {
        totalAiInteractions: aiChatSessions + mockInterviews + aiRecommendations,
        chatSessions: aiChatSessions,
        mockInterviews,
        recommendations: aiRecommendations,
      },
      communityEngagement: {
        eventRegistrations,
        mentors: totalMentors,
        mentorshipRequests,
      },
      registrationTrend: Object.values(dailyTrendMap),
    },
    'Admin analytics retrieved'
  );
}
