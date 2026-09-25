import { Request, Response, NextFunction } from 'express';
import { prisma } from '../services/prisma.service';
import { sendError } from '../utils/response';

/**
 * Retrieves all permissions assigned to a user through their UserRole -> Role -> RolePermission -> Permission associations.
 */
export async function getUserPermissions(userId: string): Promise<{ roles: string[]; permissions: string[] }> {
  const userRoles = await prisma.userRole.findMany({
    where: { userId },
    include: {
      role: {
        include: {
          permissions: {
            include: {
              permission: true,
            },
          },
        },
      },
    },
  });

  const roles = new Set<string>();
  const permissions = new Set<string>();

  for (const ur of userRoles) {
    roles.add(ur.role.name.toUpperCase());
    for (const rp of ur.role.permissions) {
      permissions.add(rp.permission.name);
    }
  }

  return {
    roles: Array.from(roles),
    permissions: Array.from(permissions),
  };
}

export const ADMINISTRATIVE_ROLES = [
  'SUPER_ADMIN',
  'ADMIN',
  'MODERATOR',
  'CONTENT_MANAGER',
  'SUPPORT_ADMIN',
  'ANALYTICS_ADMIN',
];

/**
 * Middleware: Enforces that the authenticated user possesses ANY of the specified roles.
 * Supports legacy User.role ('ADMIN', 'STUDENT') and granular RBAC roles ('SUPER_ADMIN', 'MODERATOR', etc.).
 */
export function requireRole(...allowedRoles: string[]) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    if (!req.user) {
      sendError(res, 'Unauthenticated request. Please sign in.', 401, 'UNAUTHORIZED');
      return;
    }

    const legacyRole = req.user.role.toUpperCase();
    const normalizedAllowed = allowedRoles.map((r) => r.toUpperCase());

    // Load granular roles if not already populated
    if (!req.user.roles) {
      const { roles, permissions } = await getUserPermissions(req.user.userId);
      req.user.roles = roles;
      req.user.permissions = permissions;
    }

    const userRoles = [legacyRole, ...(req.user.roles || [])];
    const hasRole = userRoles.some((r) => normalizedAllowed.includes(r));

    if (!hasRole) {
      sendError(
        res,
        `Access denied. Role '${req.user.role}' is not authorized for this resource.`,
        403,
        'FORBIDDEN'
      );
      return;
    }

    // Special check for legacy ADMIN: verify admin authorization status if user has no granular admin role
    const hasGranularAdminRole = req.user.roles?.some((r: string) => ADMINISTRATIVE_ROLES.includes(r));
    if (legacyRole === 'ADMIN' && !req.user.isAdminAuthorized && !hasGranularAdminRole) {
      sendError(res, 'Admin account authorization is pending or unverified.', 403, 'ADMIN_UNAUTHORIZED');
      return;
    }

    next();
  };
}

/**
 * Middleware: Enforces that the user has ALL of the specified permissions.
 * Users with SUPER_ADMIN role automatically bypass granular permission checks.
 */
export function requirePermission(...requiredPermissions: string[]) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    if (!req.user) {
      sendError(res, 'Unauthenticated request. Please sign in.', 401, 'UNAUTHORIZED');
      return;
    }

    // Load permissions if not cached on request
    if (!req.user.permissions || !req.user.roles) {
      const { roles, permissions } = await getUserPermissions(req.user.userId);
      req.user.roles = roles;
      req.user.permissions = permissions;
    }

    // SUPER_ADMIN automatic elevation
    if (req.user.roles.includes('SUPER_ADMIN')) {
      next();
      return;
    }

    const userPermSet = new Set(req.user.permissions);
    const missingPermissions = requiredPermissions.filter((p) => !userPermSet.has(p));

    if (missingPermissions.length > 0) {
      sendError(
        res,
        `Insufficient permissions. Missing required permissions: ${missingPermissions.join(', ')}`,
        403,
        'PERMISSION_DENIED'
      );
      return;
    }

    next();
  };
}

/**
 * Middleware: Enforces that the user has AT LEAST ONE of the specified permissions.
 */
export function requireAnyPermission(...permissions: string[]) {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    if (!req.user) {
      sendError(res, 'Unauthenticated request. Please sign in.', 401, 'UNAUTHORIZED');
      return;
    }

    if (!req.user.permissions || !req.user.roles) {
      const { roles, permissions: userPerms } = await getUserPermissions(req.user.userId);
      req.user.roles = roles;
      req.user.permissions = userPerms;
    }

    if (req.user.roles.includes('SUPER_ADMIN')) {
      next();
      return;
    }

    const userPermSet = new Set(req.user.permissions);
    const hasAny = permissions.some((p) => userPermSet.has(p));

    if (!hasAny) {
      sendError(
        res,
        `Access denied. Requires at least one of: ${permissions.join(', ')}`,
        403,
        'PERMISSION_DENIED'
      );
      return;
    }

    next();
  };
}

/**
 * Middleware: Enforces that the user has administrative access to enter /admin routes.
 * Authoritative: Evaluates granular RBAC roles ('SUPER_ADMIN', 'ADMIN', 'MODERATOR', 'CONTENT_MANAGER', 'SUPPORT_ADMIN', 'ANALYTICS_ADMIN')
 * or administrative permissions.
 * Legacy compatibility: Supports legacy User.role === 'ADMIN' when isAdminAuthorized is true.
 */
export async function requireAdministrativeAccess(req: Request, res: Response, next: NextFunction): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated request. Please sign in.', 401, 'UNAUTHORIZED');
    return;
  }

  // Load granular roles and permissions if not already cached
  if (!req.user.roles || !req.user.permissions) {
    const { roles, permissions } = await getUserPermissions(req.user.userId);
    req.user.roles = roles;
    req.user.permissions = permissions;
  }

  const legacyRole = req.user.role?.toUpperCase();
  const userRoles = req.user.roles || [];
  const userPermissions = req.user.permissions || [];

  const hasAdminRole = userRoles.some((r) => ADMINISTRATIVE_ROLES.includes(r.toUpperCase()));
  const hasLegacyAdmin = legacyRole === 'ADMIN' && req.user.isAdminAuthorized;
  const hasAdminPerm = userPermissions.length > 0;

  // If user only has legacy ADMIN role without authorization and no granular roles
  if (legacyRole === 'ADMIN' && !req.user.isAdminAuthorized && !hasAdminRole) {
    sendError(res, 'Admin account authorization is pending or unverified.', 403, 'ADMIN_UNAUTHORIZED');
    return;
  }

  if (!hasAdminRole && !hasLegacyAdmin && !hasAdminPerm) {
    sendError(
      res,
      `Access denied. Role '${req.user.role}' is not authorized for administrative resources.`,
      403,
      'FORBIDDEN'
    );
    return;
  }

  next();
}

export const requireAdmin = requireAdministrativeAccess;

