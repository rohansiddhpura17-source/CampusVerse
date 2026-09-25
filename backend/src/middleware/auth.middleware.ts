import { Request, Response, NextFunction } from 'express';
import { verifyToken, isTokenRevoked } from '../utils/jwt';
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

  if (isTokenRevoked(token)) {
    sendError(res, 'Session token has been revoked.', 401, 'SESSION_REVOKED');
    return;
  }

  // Verify user still exists and is active in database, and verify session validity
  const user = await prisma.user.findUnique({
    where: { id: payload.userId },
    select: {
      id: true,
      email: true,
      role: true,
      isAdminAuthorized: true,
      isActive: true,
      sessionVersion: true,
    },
  });

  if (!user || !user.isActive) {
    sendError(res, 'User account is inactive or no longer exists.', 401, 'ACCOUNT_INACTIVE');
    return;
  }

  // Verify persistent sessionVersion (session invalidation)
  if (
    payload.sessionVersion !== undefined &&
    payload.sessionVersion !== user.sessionVersion
  ) {
    sendError(res, 'Session has been revoked or expired. Please sign in again.', 401, 'SESSION_REVOKED');
    return;
  }

  // Check specific persistent AdminSession status if tokenId (jti) is attached
  let sessionRecord = null;
  if (payload.jti) {
    sessionRecord = await prisma.adminSession.findUnique({
      where: { tokenId: payload.jti },
    });

    if (sessionRecord) {
      if (sessionRecord.revokedAt) {
        sendError(res, 'Session has been revoked. Please sign in again.', 401, 'SESSION_REVOKED');
        return;
      }
      if (sessionRecord.expiresAt && sessionRecord.expiresAt < new Date()) {
        sendError(res, 'Session has expired. Please sign in again.', 401, 'SESSION_REVOKED');
        return;
      }
    }
  }

  req.user = {
    userId: user.id,
    email: user.email,
    role: user.role,
    isAdminAuthorized: user.isAdminAuthorized,
    sessionVersion: user.sessionVersion,
    jti: payload.jti,
  };
  req.token = token;
  req.sessionId = sessionRecord?.id;
  req.jti = payload.jti;

  next();
}

