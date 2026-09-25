import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { getUserPermissions } from '../middleware/rbac.middleware';
import { revokeToken, revokeAllUserSessions } from '../utils/jwt';
import { sendSuccess, sendError } from '../utils/response';

export async function getAdminSecurityOverview(req: Request, res: Response): Promise<void> {
  try {
    const adminId = req.user!.userId;

    const user = await prisma.user.findUnique({
      where: { id: adminId },
      select: {
        id: true,
        email: true,
        role: true,
        isAdminAuthorized: true,
        createdAt: true,
        updatedAt: true,
        sessionVersion: true,
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
            adminProfile: {
              select: {
                accessLevel: true,
                department: true,
              },
            },
          },
        },
        securitySettings: {
          select: {
            twoFactorEnabled: true,
            loginAlertsEnabled: true,
            lastPasswordChange: true,
          },
        },
      },
    });

    if (!user) {
      sendError(res, 'Administrator record not found.', 404, 'NOT_FOUND');
      return;
    }

    const { roles, permissions } = await getUserPermissions(adminId);

    // Fetch recent security-related audit logs for this admin and system
    const recentAuditLogs = await prisma.auditLog.findMany({
      where: {
        OR: [
          { actorId: adminId },
          { targetType: 'USER', targetId: adminId },
          {
            action: {
              in: [
                'ADMIN_LOGIN',
                'ADMIN_LOGOUT',
                'ADMIN_SESSION_REVOKED',
                'ADMIN_ALL_SESSIONS_REVOKED',
                'ADMIN_ASSIGNED_ROLE',
                'ADMIN_REVOKED_ROLE',
                'ADMIN_UPDATED_ROLE_PERMISSIONS',
                'ADMIN_SUSPENDED_USER',
                'ADMIN_TOGGLED_FEATURE_FLAG',
              ],
            },
          },
        ],
      },
      orderBy: { timestamp: 'desc' },
      take: 10,
      include: {
        actor: {
          select: {
            email: true,
            role: true,
            profile: { select: { fullName: true } },
          },
        },
      },
    });

    // Capture active connection telemetry
    const forwarded = req.headers['x-forwarded-for'];
    const currentIp = typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : req.ip || '127.0.0.1';
    const userAgent = req.headers['user-agent'] || 'Unknown Browser';
    const currentJti = (req as any).jti;

    // Fetch real active sessions for this admin
    const dbSessions = await prisma.adminSession.findMany({
      where: {
        userId: adminId,
        revokedAt: null,
        expiresAt: { gt: new Date() },
      },
      orderBy: { lastActiveAt: 'desc' },
      take: 10,
    });

    const activeSessions =
      dbSessions.length > 0
        ? dbSessions.map((s) => ({
            id: s.id,
            tokenId: s.tokenId,
            isCurrent: Boolean(currentJti && s.tokenId === currentJti),
            deviceName: s.deviceName || 'Web Browser',
            ipAddress: s.ipAddress || currentIp,
            userAgent: s.userAgent || userAgent,
            lastActive: s.lastActiveAt,
            createdAt: s.createdAt,
            expiresAt: s.expiresAt,
          }))
        : [
            {
              id: 'current-session',
              tokenId: currentJti || 'current',
              isCurrent: true,
              deviceName: 'Current Web Client',
              ipAddress: currentIp,
              userAgent,
              lastActive: new Date(),
              createdAt: new Date(),
              expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
            },
          ];

    sendSuccess(res, {
      admin: {
        id: user.id,
        email: user.email,
        fullName: user.profile?.fullName || user.email,
        avatarUrl: user.profile?.avatarUrl,
        role: user.role,
        sessionVersion: user.sessionVersion,
        accessLevel: user.profile?.adminProfile?.accessLevel || 'STANDARD',
        department: user.profile?.adminProfile?.department || 'Executive',
        roles,
        permissions,
        isSuperAdmin: roles.includes('SUPER_ADMIN'),
      },
      securitySettings: user.securitySettings || {
        twoFactorEnabled: false,
        loginAlertsEnabled: true,
        lastPasswordChange: user.updatedAt,
      },
      activeSessions,
      recentSecurityEvents: recentAuditLogs.map((log) => ({
        id: log.id,
        action: log.action,
        targetType: log.targetType,
        targetId: log.targetId,
        actorEmail: log.actor.email,
        actorName: log.actor.profile?.fullName || log.actor.email,
        metadata: log.metadata ? JSON.parse(log.metadata) : null,
        timestamp: log.timestamp,
      })),
    });
  } catch (error: any) {
    sendError(res, error.message || 'Failed to fetch security overview', 500);
  }
}

export async function getAdminSessions(req: Request, res: Response): Promise<void> {
  try {
    const adminId = req.user!.userId;
    const currentJti = (req as any).jti;

    const sessions = await prisma.adminSession.findMany({
      where: { userId: adminId },
      orderBy: { createdAt: 'desc' },
      take: 50,
    });

    const formattedSessions = sessions.map((s) => {
      const isExpired = s.expiresAt < new Date();
      const isRevoked = Boolean(s.revokedAt);
      let status: 'ACTIVE' | 'REVOKED' | 'EXPIRED' = 'ACTIVE';
      if (isRevoked) status = 'REVOKED';
      else if (isExpired) status = 'EXPIRED';

      return {
        id: s.id,
        tokenId: s.tokenId,
        deviceName: s.deviceName || 'Web Browser',
        ipAddress: s.ipAddress || '127.0.0.1',
        userAgent: s.userAgent || 'Unknown',
        createdAt: s.createdAt,
        lastActiveAt: s.lastActiveAt,
        expiresAt: s.expiresAt,
        revokedAt: s.revokedAt,
        revokedReason: s.revokedReason,
        isCurrent: Boolean(currentJti && s.tokenId === currentJti),
        status,
      };
    });

    sendSuccess(res, { sessions: formattedSessions });
  } catch (error: any) {
    sendError(res, error.message || 'Failed to list admin sessions', 500);
  }
}

export async function revokeAdminSession(req: Request, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const adminId = req.user!.userId;

    const session = await prisma.adminSession.findFirst({
      where: {
        OR: [{ id }, { tokenId: id }],
      },
    });

    if (!session) {
      sendError(res, 'Session record not found.', 404, 'NOT_FOUND');
      return;
    }

    const { roles } = await getUserPermissions(adminId);
    const isSuperAdmin = roles.includes('SUPER_ADMIN');

    if (session.userId !== adminId && !isSuperAdmin) {
      sendError(res, 'Forbidden. You cannot revoke sessions belonging to another administrator.', 403, 'FORBIDDEN');
      return;
    }

    if (session.revokedAt) {
      sendSuccess(res, { sessionId: session.id, alreadyRevoked: true }, 'Session was already revoked.');
      return;
    }

    await prisma.adminSession.update({
      where: { id: session.id },
      data: {
        revokedAt: new Date(),
        revokedReason: 'ADMIN_REVOKED',
      },
    });

    revokeToken(session.tokenId);

    await logAudit(adminId, 'ADMIN_SESSION_REVOKED', 'USER', session.userId, {
      sessionId: session.id,
      revokedBy: adminId,
      ipAddress: req.ip,
      userAgent: req.headers['user-agent'],
    });

    sendSuccess(res, { sessionId: session.id, revoked: true }, 'Session successfully revoked.');
  } catch (error: any) {
    sendError(res, error.message || 'Failed to revoke admin session', 500);
  }
}

export async function revokeAllAdminSessions(req: Request, res: Response): Promise<void> {
  try {
    const adminId = req.user!.userId;
    const targetUserId = req.body?.userId || adminId;

    const { roles } = await getUserPermissions(adminId);
    const isSuperAdmin = roles.includes('SUPER_ADMIN');

    if (targetUserId !== adminId && !isSuperAdmin) {
      sendError(res, 'Forbidden. You cannot revoke sessions for other administrators.', 403, 'FORBIDDEN');
      return;
    }

    // Increment user sessionVersion
    await prisma.user.update({
      where: { id: targetUserId },
      data: { sessionVersion: { increment: 1 } },
    });

    // Mark all active admin sessions as revoked
    await prisma.adminSession.updateMany({
      where: {
        userId: targetUserId,
        revokedAt: null,
      },
      data: {
        revokedAt: new Date(),
        revokedReason: 'ADMIN_ALL_SESSIONS_REVOKED',
      },
    });

    revokeAllUserSessions(targetUserId);

    await logAudit(adminId, 'ADMIN_ALL_SESSIONS_REVOKED', 'USER', targetUserId, {
      revokedBy: adminId,
      targetUserId,
      reason: 'Administrator triggered full session invalidation',
      ipAddress: req.ip,
      userAgent: req.headers['user-agent'],
    });

    sendSuccess(res, { revokedAll: true }, 'All administrative sessions have been terminated.');
  } catch (error: any) {
    sendError(res, error.message || 'Failed to revoke all sessions', 500);
  }
}

export async function revokeCurrentSession(req: Request, res: Response): Promise<void> {
  try {
    const rawToken = (req as any).token;
    const adminId = req.user!.userId;
    const currentJti = (req as any).jti;

    if (rawToken) {
      revokeToken(rawToken);
    }

    if (currentJti) {
      await prisma.adminSession.updateMany({
        where: { tokenId: currentJti, revokedAt: null },
        data: {
          revokedAt: new Date(),
          revokedReason: 'ADMIN_REVOKED',
        },
      });
    }

    await logAudit(adminId, 'ADMIN_SESSION_REVOKED', 'USER', adminId, {
      jti: currentJti,
      ipAddress: req.ip,
      userAgent: req.headers['user-agent'],
    });

    sendSuccess(res, { revoked: true }, 'Current session revoked successfully.');
  } catch (error: any) {
    sendError(res, error.message || 'Failed to revoke session', 500);
  }
}

export async function revokeAllSessions(req: Request, res: Response): Promise<void> {
  return revokeAllAdminSessions(req, res);
}
