import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { AuthUserPayload } from '../types/express';

// In-memory set of revoked token signatures with automated cleanup
const revokedTokens = new Set<string>();
const userRevocationTimestamps = new Map<string, number>();

export function signToken(payload: AuthUserPayload): string {
  const tokenPayload: AuthUserPayload = {
    ...payload,
    jti: payload.jti || crypto.randomUUID(),
    sessionVersion: payload.sessionVersion !== undefined ? payload.sessionVersion : 1,
  };
  return jwt.sign(tokenPayload, env.JWT_SECRET, {
    expiresIn: env.JWT_EXPIRES_IN,
  } as jwt.SignOptions);
}

export interface DecodedTokenPayload extends AuthUserPayload {
  iat?: number;
  exp?: number;
}

export function verifyToken(token: string): DecodedTokenPayload | null {
  try {
    const decoded = jwt.verify(token, env.JWT_SECRET) as DecodedTokenPayload;
    return decoded;
  } catch (error) {
    return null;
  }
}

export function revokeToken(token: string): void {
  revokedTokens.add(token);
}

export function isTokenRevoked(token: string): boolean {
  return revokedTokens.has(token);
}

export function revokeAllUserSessions(userId: string): void {
  userRevocationTimestamps.set(userId, Date.now());
}

export function isSessionRevoked(userId: string, iat?: number): boolean {
  if (!iat) return false;
  const revokedAt = userRevocationTimestamps.get(userId);
  if (!revokedAt) return false;
  return iat * 1000 < revokedAt;
}

