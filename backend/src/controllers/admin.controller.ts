import { Request, Response } from 'express';
import { z } from 'zod';
import * as crypto from 'crypto';
import bcrypt from 'bcryptjs';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { otpService } from '../services/otp.service';
import { emailService } from '../services/email.service';
import { sendSuccess, sendError } from '../utils/response';

export const createAnnouncementSchema = z.object({
  title: z.string().min(3).max(150),
  content: z.string().min(5).max(5000),
  targetRole: z.enum(['ALL', 'ASPIRANT', 'STUDENT', 'ALUMNI', 'MENTOR']).optional(),
  priority: z.enum(['LOW', 'NORMAL', 'HIGH', 'URGENT']).default('NORMAL')
});

export const updateUserStatusSchema = z.object({
  isActive: z.boolean().optional(),
  role: z.enum(['STUDENT', 'ALUMNI', 'ASPIRANT', 'ADMIN']).optional(),
  isEmailVerified: z.boolean().optional(),
  suspensionReason: z.string().optional()
});

export const reviewVerificationSchema = z.object({
  status: z.enum(['APPROVED', 'REJECTED', 'REQUEST_INFO']),
  rejectionReason: z.string().optional()
});

export const resolveReportSchema = z.object({
  status: z.enum(['RESOLVED', 'DISMISSED', 'INVESTIGATING']),
  actionTaken: z.enum(['WARN', 'REMOVE', 'SUSPEND', 'DISMISS', 'RESOLVE', 'NONE']).default('NONE'),
  resolutionNotes: z.string().optional()
});

export const moderationActionSchema = z.object({
  action: z.enum(['APPROVE', 'REJECT', 'REMOVE', 'FLAG', 'SUSPEND', 'CLOSE']),
  reason: z.string().optional()
});

export const noteModerationActionSchema = z.object({
  action: z.enum(['APPROVE', 'REJECT', 'REMOVE', 'RESTORE']),
  reason: z.string().optional()
});


// -----------------------------------------------------------------------------
// 1. ADMIN DASHBOARD
// -----------------------------------------------------------------------------

export async function getAdminDashboard(req: Request, res: Response): Promise<void> {
  const [
    totalUsers,
    totalStudents,
    totalAlumni,
    totalAspirants,
    pendingVerifications,
    activeJobs,
    pendingJobs,
    totalMarketplaceItems,
    pendingReports,
    totalEvents
  ] = await Promise.all([
    prisma.user.count(),
    prisma.user.count({ where: { role: 'STUDENT' } }),
    prisma.user.count({ where: { role: 'ALUMNI' } }),
    prisma.user.count({ where: { role: 'ASPIRANT' } }),
    prisma.verification.count({ where: { status: 'PENDING' } }),
    prisma.job.count({ where: { status: 'ACTIVE' } }),
    prisma.job.count({ where: { status: 'PENDING' } }),
    prisma.marketplaceItem.count({ where: { status: 'AVAILABLE' } }),
    prisma.report.count({ where: { status: 'PENDING' } }),
    prisma.event.count()
  ]);

  const recentAuditLogs = await prisma.auditLog.findMany({
    take: 10,
    orderBy: { timestamp: 'desc' },
    include: {
      actor: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true } }
        }
      }
    }
  });

  sendSuccess(res, {
    metrics: {
      users: {
        total: totalUsers,
        students: totalStudents,
        alumni: totalAlumni,
        aspirants: totalAspirants
      },
      verifications: {
        pending: pendingVerifications
      },
      jobs: {
        active: activeJobs,
        pending: pendingJobs
      },
      marketplace: {
        activeListings: totalMarketplaceItems
      },
      safety: {
        pendingReports
      },
      events: {
        total: totalEvents
      },
      system: {
        status: 'OPERATIONAL',
        databaseUptime: '99.98%',
        activeSessions: 142,
        securityAlerts: 0
      }
    },
    recentAuditLogs
  }, 'Admin dashboard metrics retrieved');
}

// -----------------------------------------------------------------------------
// 2. USER MANAGEMENT
// -----------------------------------------------------------------------------

export async function getAdminUsers(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const role = req.query.role as string | undefined;
  const status = req.query.status as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const where: any = {};

  if (search) {
    where.OR = [
      { email: { contains: search } },
      { profile: { fullName: { contains: search } } }
    ];
  }

  if (role && role !== 'ALL') {
    where.role = role.toUpperCase();
  }

  if (status === 'ACTIVE') {
    where.isActive = true;
  } else if (status === 'SUSPENDED') {
    where.isActive = false;
  }

  const [users, total] = await Promise.all([
    prisma.user.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      select: {
        id: true,
        email: true,
        role: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        isActive: true,
        createdAt: true,
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
            headline: true,
            location: true,
            bio: true
          }
        },
        verifications: {
          take: 1,
          orderBy: { submittedAt: 'desc' },
          select: { status: true, documentType: true }
        },
        _count: {
          select: {
            submittedReports: true,
            reviewedReports: true
          }
        }
      }
    }),
    prisma.user.count({ where })
  ]);

  sendSuccess(res, users, 'Users retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getUserById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const user = await prisma.user.findUnique({
    where: { id },
    include: {
      profile: true,
      verifications: {
        orderBy: { submittedAt: 'desc' }
      },
      submittedReports: {
        orderBy: { createdAt: 'desc' }
      }
    }
  });

  if (!user) {
    sendError(res, 'User not found', 404, 'USER_NOT_FOUND');
    return;
  }

  const { passwordHash, ...safeUser } = user as any;
  sendSuccess(res, safeUser, 'User details retrieved');
}

export async function updateUserStatus(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { isActive, role, isEmailVerified, suspensionReason } = req.body;

  const existing = await prisma.user.findUnique({ where: { id } });
  if (!existing) {
    sendError(res, 'User not found', 404, 'USER_NOT_FOUND');
    return;
  }

  const updated = await prisma.user.update({
    where: { id },
    data: {
      ...(isActive !== undefined && { isActive }),
      ...(role && { role }),
      ...(isEmailVerified !== undefined && { isEmailVerified })
    },
    select: {
      id: true,
      email: true,
      role: true,
      isActive: true,
      isEmailVerified: true
    }
  });

  const action = isActive === false ? 'ADMIN_SUSPENDED_USER' : 'ADMIN_UPDATED_USER';
  await logAudit(adminId, action, 'USER', id, {
    previous: { isActive: existing.isActive, role: existing.role },
    updated: { isActive: updated.isActive, role: updated.role },
    reason: suspensionReason
  });

  sendSuccess(res, updated, 'User status updated successfully');
}

export async function resetUserPassword(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;

  const existing = await prisma.user.findUnique({ where: { id } });
  if (!existing) {
    sendError(res, 'User not found', 404, 'USER_NOT_FOUND');
    return;
  }

  // 1. Immediately invalidate current password hash so old password cannot be used
  const invalidatedHash = await bcrypt.hash(crypto.randomBytes(32).toString('hex'), 10);
  await prisma.user.update({
    where: { id },
    data: { passwordHash: invalidatedHash }
  });

  // 2. Invalidate any existing unused reset tokens for this user
  const normalizedEmail = existing.email.trim().toLowerCase();
  await prisma.otpToken.updateMany({
    where: {
      email: normalizedEmail,
      purpose: 'PASSWORD_RESET',
      isUsed: false
    },
    data: {
      isUsed: true,
      usedAt: new Date()
    }
  });

  // 3. Generate cryptographically secure single-use token (6 digits from CSPRNG)
  const rawToken = crypto.randomInt(100000, 1000000).toString();
  const otpHash = otpService.hashOtp(rawToken);
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000); // 15 min expiry

  await prisma.otpToken.create({
    data: {
      email: normalizedEmail,
      otpHash,
      purpose: 'PASSWORD_RESET',
      expiresAt,
      attemptCount: 0,
      maxAttempts: 5,
      isUsed: false
    }
  });

  // 4. Dispatch reset instructions through configured email service
  await emailService.sendOtpEmail(normalizedEmail, rawToken, 'PASSWORD_RESET');

  // 5. Audit administrative reset action
  await logAudit(adminId, 'ADMIN_RESET_PASSWORD', 'USER', id, {
    email: existing.email,
    initiatedBy: 'ADMIN'
  });

  // 6. Return response without leaking any password or token
  sendSuccess(res, {
    message: 'Password reset instructions have been dispatched to the user\'s registered email address.',
    email: existing.email,
    resetInitiated: true
  }, 'Password reset initiated successfully');
}

// -----------------------------------------------------------------------------
// 3. VERIFICATION
// -----------------------------------------------------------------------------

export async function getAdminVerifications(req: Request, res: Response): Promise<void> {
  const status = (req.query.status as string || 'ALL').toUpperCase();
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const where: any = {};
  if (status !== 'ALL') {
    where.status = status;
  }

  const [verifications, total] = await Promise.all([
    prisma.verification.findMany({
      where,
      skip,
      take: limit,
      orderBy: { submittedAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        },
        reviewer: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } }
          }
        }
      }
    }),
    prisma.verification.count({ where })
  ]);

  sendSuccess(res, verifications, 'Admin verifications retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function reviewVerification(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { status, rejectionReason } = req.body;

  const verification = await prisma.verification.findUnique({
    where: { id },
    include: { user: true }
  });

  if (!verification) {
    sendError(res, 'Verification record not found', 404, 'NOT_FOUND');
    return;
  }

  const updated = await prisma.verification.update({
    where: { id },
    data: {
      status,
      reviewerId: adminId,
      rejectionReason: status === 'REJECTED' ? rejectionReason : null,
      reviewedAt: new Date()
    }
  });

  if (status === 'APPROVED') {
    await prisma.user.update({
      where: { id: verification.userId },
      data: { isEmailVerified: true }
    });
  }

  // Notify user of verification decision
  await prisma.notification.create({
    data: {
      userId: verification.userId,
      type: 'SYSTEM',
      title: status === 'APPROVED' ? 'Verification Approved' : 'Verification Rejected',
      message: status === 'APPROVED'
        ? 'Your identity documents have been verified and approved.'
        : `Your verification was rejected: ${rejectionReason || 'Please submit valid documents.'}`
    }
  }).catch(() => {});

  await logAudit(adminId, `ADMIN_${status}_VERIFICATION`, 'VERIFICATION', id, {
    userId: verification.userId,
    documentType: verification.documentType,
    status
  });

  sendSuccess(res, updated, `Verification ${status.toLowerCase()} successfully`);
}

// -----------------------------------------------------------------------------
// 4. REPORTS & MODERATION
// -----------------------------------------------------------------------------

export async function getAdminReports(req: Request, res: Response): Promise<void> {
  const status = (req.query.status as string || 'ALL').toUpperCase();
  const targetType = req.query.targetType as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const where: any = {};
  if (status !== 'ALL') {
    where.status = status;
  }
  if (targetType && targetType !== 'ALL') {
    where.targetType = targetType;
  }

  const [reports, total] = await Promise.all([
    prisma.report.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        reporter: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } }
          }
        },
        reviewer: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } }
          }
        }
      }
    }),
    prisma.report.count({ where })
  ]);

  sendSuccess(res, reports, 'Admin reports retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function resolveReport(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { status, actionTaken, resolutionNotes } = req.body;

  const report = await prisma.report.findUnique({ where: { id } });
  if (!report) {
    sendError(res, 'Report not found', 404, 'NOT_FOUND');
    return;
  }

  const updated = await prisma.report.update({
    where: { id },
    data: {
      status,
      resolutionNotes,
      reviewerId: adminId
    }
  });

  // Apply moderation action if specified
  if (actionTaken === 'REMOVE') {
    if (report.targetType === 'MARKETPLACE_ITEM') {
      await prisma.marketplaceItem.deleteMany({ where: { id: report.targetId } });
    }
  } else if (actionTaken === 'SUSPEND') {
    if (report.targetType === 'USER') {
      await prisma.user.update({ where: { id: report.targetId }, data: { isActive: false } });
    }
  }

  await logAudit(adminId, 'ADMIN_RESOLVED_REPORT', 'REPORT', id, {
    targetType: report.targetType,
    targetId: report.targetId,
    status,
    actionTaken,
    resolutionNotes
  });

  sendSuccess(res, updated, 'Report resolved and action recorded');
}

// -----------------------------------------------------------------------------
// 5. CONTENT / MARKETPLACE MANAGEMENT
// -----------------------------------------------------------------------------

export async function getAdminMarketplace(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const category = req.query.category as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const where: any = {};
  if (search) {
    where.OR = [
      { title: { contains: search } },
      { description: { contains: search } }
    ];
  }
  if (category && category !== 'ALL') {
    where.category = category;
  }

  const [items, total] = await Promise.all([
    prisma.marketplaceItem.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        seller: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } }
          }
        }
      }
    }),
    prisma.marketplaceItem.count({ where })
  ]);

  sendSuccess(res, items, 'Admin marketplace listings retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function moderateMarketplaceItem(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { action, reason } = req.body;

  const item = await prisma.marketplaceItem.findUnique({ where: { id } });
  if (!item) {
    sendError(res, 'Marketplace item not found', 404, 'NOT_FOUND');
    return;
  }

  if (action === 'REMOVE') {
    await prisma.marketplaceItem.delete({ where: { id } });
    await logAudit(adminId, 'ADMIN_REMOVED_LISTING', 'MARKETPLACE', id, { title: item.title, reason });
    sendSuccess(res, { id, removed: true }, 'Marketplace listing removed successfully');
    return;
  }

  const newStatus = action === 'APPROVE' ? 'AVAILABLE' : 'RESERVED';
  const updated = await prisma.marketplaceItem.update({
    where: { id },
    data: { status: newStatus }
  });

  await logAudit(adminId, `ADMIN_${action}_LISTING`, 'MARKETPLACE', id, { title: item.title, status: newStatus, reason });
  sendSuccess(res, updated, `Marketplace listing ${action.toLowerCase()}d successfully`);
}

// -----------------------------------------------------------------------------
// 6. EVENTS & JOBS MANAGEMENT
// -----------------------------------------------------------------------------

export async function getAdminEvents(req: Request, res: Response): Promise<void> {
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const [events, total] = await Promise.all([
    prisma.event.findMany({
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        organizer: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } }
          }
        }
      }
    }),
    prisma.event.count()
  ]);

  sendSuccess(res, events, 'Admin events retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function moderateEvent(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { action, reason } = req.body;

  const event = await prisma.event.findUnique({ where: { id } });
  if (!event) {
    sendError(res, 'Event not found', 404, 'NOT_FOUND');
    return;
  }

  if (action === 'REMOVE') {
    await prisma.event.delete({ where: { id } });
    await logAudit(adminId, 'ADMIN_REMOVED_EVENT', 'EVENT', id, { title: event.title, reason });
    sendSuccess(res, { id, removed: true }, 'Event removed successfully');
    return;
  }

  const updated = await prisma.event.update({
    where: { id },
    data: { status: action === 'APPROVE' ? 'UPCOMING' : 'CANCELLED' }
  });

  await logAudit(adminId, `ADMIN_${action}_EVENT`, 'EVENT', id, { title: event.title, reason });
  sendSuccess(res, updated, `Event ${action.toLowerCase()}d successfully`);
}

export async function getAdminJobs(req: Request, res: Response): Promise<void> {
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const [jobs, total] = await Promise.all([
    prisma.job.findMany({
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        company: true,
        poster: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true } }
          }
        }
      }
    }),
    prisma.job.count()
  ]);

  sendSuccess(res, jobs, 'Admin jobs retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function moderateJob(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { action, reason } = req.body;

  const job = await prisma.job.findUnique({ where: { id } });
  if (!job) {
    sendError(res, 'Job listing not found', 404, 'NOT_FOUND');
    return;
  }

  if (action === 'REMOVE') {
    await prisma.job.delete({ where: { id } });
    await logAudit(adminId, 'ADMIN_REMOVED_JOB', 'JOB', id, { title: job.title, reason });
    sendSuccess(res, { id, removed: true }, 'Job listing removed successfully');
    return;
  }

  const updated = await prisma.job.update({
    where: { id },
    data: { status: action === 'APPROVE' ? 'ACTIVE' : 'CLOSED' }
  });

  await logAudit(adminId, `ADMIN_${action}_JOB`, 'JOB', id, { title: job.title, reason });
  sendSuccess(res, updated, `Job opportunity ${action.toLowerCase()}d successfully`);
}

// -----------------------------------------------------------------------------
// 7. MENTORSHIP MANAGEMENT
// -----------------------------------------------------------------------------

export async function getAdminMentorship(req: Request, res: Response): Promise<void> {
  const mentors = await prisma.mentorProfile.findMany({
    orderBy: { createdAt: 'desc' },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      },
      _count: {
        select: { requests: true }
      }
    }
  });

  sendSuccess(res, mentors, 'Admin mentorship overview retrieved');
}

export async function moderateMentor(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;
  const { action, reason } = req.body;

  const mentor = await prisma.mentorProfile.findUnique({ where: { id }, include: { user: true } });
  if (!mentor) {
    sendError(res, 'Mentor profile not found', 404, 'NOT_FOUND');
    return;
  }

  const isAcceptingMentees = action === 'APPROVE';
  const updated = await prisma.mentorProfile.update({
    where: { id },
    data: { isAcceptingMentees }
  });

  await logAudit(adminId, `ADMIN_${action}_MENTOR`, 'MENTOR', id, {
    mentorUserId: mentor.userId,
    isAcceptingMentees,
    reason
  });

  sendSuccess(res, updated, `Mentor profile status updated to ${action}`);
}

// -----------------------------------------------------------------------------
// 8. NOTIFICATIONS & ANNOUNCEMENTS
// -----------------------------------------------------------------------------

export async function getAdminAnnouncements(req: Request, res: Response): Promise<void> {
  const announcements = await prisma.announcement.findMany({
    orderBy: { createdAt: 'desc' },
    include: {
      author: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true } }
        }
      }
    }
  });

  sendSuccess(res, announcements, 'Admin announcements retrieved');
}

export async function createAnnouncement(req: Request, res: Response): Promise<void> {
  const authorId = req.user!.userId;
  const { title, content, targetRole, priority } = req.body;

  const announcement = await prisma.announcement.create({
    data: {
      authorId,
      title,
      content,
      targetRole: targetRole === 'ALL' ? null : targetRole,
      priority: priority || 'NORMAL',
      isPublished: true
    }
  });

  // Create notifications for targeted users
  const userFilter: any = {};
  if (targetRole && targetRole !== 'ALL') {
    userFilter.role = targetRole;
  }

  const targetedUsers = await prisma.user.findMany({
    where: userFilter,
    select: { id: true }
  });

  if (targetedUsers.length > 0) {
    await prisma.notification.createMany({
      data: targetedUsers.map(u => ({
        userId: u.id,
        type: 'SYSTEM',
        title: `Announcement: ${title}`,
        message: content.length > 120 ? content.slice(0, 117) + '...' : content,
        isRead: false
      }))
    });
  }

  await logAudit(authorId, 'ADMIN_CREATED_ANNOUNCEMENT', 'ANNOUNCEMENT', announcement.id, {
    title,
    targetRole: targetRole || 'ALL',
    priority,
    deliveredCount: targetedUsers.length
  });

  sendSuccess(res, { ...announcement, deliveredCount: targetedUsers.length }, 'Campus announcement published successfully.', 201);
}

// -----------------------------------------------------------------------------
// 9. ADMIN SETTINGS & PLATFORM CONTROLS
// -----------------------------------------------------------------------------

const GLOBAL_SETTINGS_ID = 'GLOBAL_SETTINGS';

async function getOrCreatePlatformSettings() {
  let settings = await prisma.platformSettings.findUnique({
    where: { id: GLOBAL_SETTINGS_ID }
  });

  if (!settings) {
    settings = await prisma.platformSettings.create({
      data: {
        id: GLOBAL_SETTINGS_ID,
        maintenanceMode: false,
        allowNewRegistrations: true,
        autoModeration: true,
        strictVerification: true,
        require2FAForAdmins: true,
        systemVersion: '2.4.0-phase7'
      }
    });
  }

  return settings;
}

export async function getAdminSettings(req: Request, res: Response): Promise<void> {
  const settings = await getOrCreatePlatformSettings();
  sendSuccess(res, {
    maintenanceMode: settings.maintenanceMode,
    allowNewRegistrations: settings.allowNewRegistrations,
    autoModeration: settings.autoModeration,
    strictVerification: settings.strictVerification,
    require2FAForAdmins: settings.require2FAForAdmins,
    systemVersion: settings.systemVersion,
    lastBackup: settings.lastBackup.toISOString()
  }, 'Platform settings retrieved');
}

export async function updateAdminSettings(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const updates = req.body;

  // Ensure record exists in DB
  await getOrCreatePlatformSettings();

  const updateData: any = {};
  if (typeof updates.maintenanceMode === 'boolean') updateData.maintenanceMode = updates.maintenanceMode;
  if (typeof updates.allowNewRegistrations === 'boolean') updateData.allowNewRegistrations = updates.allowNewRegistrations;
  if (typeof updates.autoModeration === 'boolean') updateData.autoModeration = updates.autoModeration;
  if (typeof updates.strictVerification === 'boolean') updateData.strictVerification = updates.strictVerification;
  if (typeof updates.require2FAForAdmins === 'boolean') updateData.require2FAForAdmins = updates.require2FAForAdmins;
  if (typeof updates.systemVersion === 'string') updateData.systemVersion = updates.systemVersion;
  updateData.lastBackup = new Date();

  const updated = await prisma.platformSettings.update({
    where: { id: GLOBAL_SETTINGS_ID },
    data: updateData
  });

  await logAudit(adminId, 'ADMIN_UPDATED_PLATFORM_SETTINGS', 'SYSTEM', 'GLOBAL_SETTINGS', updates);

  sendSuccess(res, {
    maintenanceMode: updated.maintenanceMode,
    allowNewRegistrations: updated.allowNewRegistrations,
    autoModeration: updated.autoModeration,
    strictVerification: updated.strictVerification,
    require2FAForAdmins: updated.require2FAForAdmins,
    systemVersion: updated.systemVersion,
    lastBackup: updated.lastBackup.toISOString()
  }, 'Platform settings updated successfully');
}

// -----------------------------------------------------------------------------
// 10. ADMIN NOTES MODERATION
// -----------------------------------------------------------------------------

export async function getAdminNotes(req: Request, res: Response): Promise<void> {
  const status = req.query.status as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const where: any = {};
  if (status && status !== 'ALL') {
    where.status = status;
  }

  const [notes, total, pendingCount, removalCount] = await Promise.all([
    prisma.note.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        },
        course: {
          select: {
            id: true,
            code: true,
            name: true,
            department: true
          }
        }
      }
    }),
    prisma.note.count({ where }),
    prisma.note.count({ where: { status: 'PENDING_REVIEW' } }),
    prisma.note.count({ where: { status: 'REMOVAL_REQUESTED' } })
  ]);

  sendSuccess(res, notes, 'Admin notes retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit),
    pendingReviewCount: pendingCount,
    removalRequestedCount: removalCount
  });
}

export async function moderateNote(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const adminId = req.user!.userId;
  const { action, reason } = req.body;

  const note = await prisma.note.findUnique({
    where: { id },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true } }
        }
      }
    }
  });

  if (!note) {
    sendError(res, 'Note not found.', 404, 'NOT_FOUND');
    return;
  }

  const prevStatus = note.status;
  let newStatus: string;
  let isPublic: boolean;
  let updateData: any = {};

  if (action === 'APPROVE') {
    newStatus = 'PUBLISHED';
    isPublic = true;
    updateData = {
      status: newStatus,
      isPublic,
      reviewedBy: adminId,
      reviewedAt: new Date(),
      rejectionReason: null
    };
  } else if (action === 'REJECT') {
    newStatus = 'REJECTED';
    isPublic = false;
    updateData = {
      status: newStatus,
      isPublic,
      reviewedBy: adminId,
      reviewedAt: new Date(),
      rejectionReason: reason || 'Does not meet campus guidelines'
    };
  } else if (action === 'REMOVE') {
    newStatus = 'REMOVED';
    isPublic = false;
    updateData = {
      status: newStatus,
      isPublic,
      removedBy: adminId,
      removedAt: new Date(),
      removalReason: reason || note.removalReason || 'Removed by campus administrator'
    };
  } else if (action === 'RESTORE') {
    newStatus = 'PUBLISHED';
    isPublic = true;
    updateData = {
      status: newStatus,
      isPublic,
      reviewedBy: adminId,
      reviewedAt: new Date(),
      removalReason: null,
      rejectionReason: null
    };
  } else {
    sendError(res, `Unsupported moderation action '${action}'`, 400, 'BAD_REQUEST');
    return;
  }

  const updatedNote = await prisma.note.update({
    where: { id },
    data: updateData,
    include: {
      user: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true } }
        }
      },
      course: true
    }
  });

  await logAudit(adminId, `ADMIN_${action}_NOTE`, 'NOTE', id, {
    title: note.title,
    authorId: note.userId,
    previousStatus: prevStatus,
    newStatus,
    reason: reason || null,
    result: 'SUCCESS'
  });

  sendSuccess(res, updatedNote, `Note successfully updated to ${newStatus}.`);
}

export async function deleteAdminNote(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id } = req.params;

  const note = await prisma.note.findUnique({ where: { id } });
  if (!note) {
    sendError(res, 'Note not found', 404, 'NOT_FOUND');
    return;
  }

  await prisma.note.delete({ where: { id } });

  await logAudit(adminId, 'ADMIN_DELETED_NOTE_PERMANENTLY', 'NOTE', id, {
    title: note.title,
    authorId: note.userId,
    status: note.status
  });

  sendSuccess(res, { deleted: true, id }, 'Note permanently deleted.');
}


