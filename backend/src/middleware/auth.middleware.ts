import { Request, Response, NextFunction } from 'express';
import { verifyToken } from '../utils/jwt';
import { sendError } from '../utils/response';
import { prisma } from '../services/prisma.service';

export async function requireAuth(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    sendError(res, 'Authentication token missing or invalid format.', 401, 'UNAUTHORIZED');
    return;
  }

  const token = authHeader.substring(7).trim();
  const payload = verifyToken(token);

  if (!payload) {
    sendError(res, 'Authentication token is invalid or expired.', 401, 'INVALID_TOKEN');
    return;
  }

  // Verify user still exists and is active in database
  const user = await prisma.user.findUnique({
    where: { id: payload.userId },
    select: { id: true, email: true, role: true, isAdminAuthorized: true, isActive: true }
  });

  if (!user || !user.isActive) {
    sendError(res, 'User account is inactive or no longer exists.', 401, 'ACCOUNT_INACTIVE');
    return;
  }

  req.user = {
    userId: user.id,
    email: user.email,
    role: user.role,
    isAdminAuthorized: user.isAdminAuthorized
  };

  next();
}
