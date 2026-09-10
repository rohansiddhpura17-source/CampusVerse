import { Request, Response, NextFunction } from 'express';
import { sendError } from '../utils/response';

export function requireRole(...allowedRoles: string[]) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user) {
      sendError(res, 'Unauthenticated request. Please sign in.', 401, 'UNAUTHORIZED');
      return;
    }

    const userRole = req.user.role.toUpperCase();
    const normalizedAllowed = allowedRoles.map(r => r.toUpperCase());

    if (!normalizedAllowed.includes(userRole)) {
      sendError(
        res,
        `Access denied. Role '${req.user.role}' is not authorized for this resource.`,
        403,
        'FORBIDDEN'
      );
      return;
    }

    // Special check for ADMIN: if user role is ADMIN, verify admin status
    if (userRole === 'ADMIN' && !req.user.isAdminAuthorized) {
      sendError(
        res,
        'Admin account authorization is pending or unverified.',
        403,
        'ADMIN_UNAUTHORIZED'
      );
      return;
    }

    next();
  };
}

export const requireAdmin = requireRole('ADMIN');
