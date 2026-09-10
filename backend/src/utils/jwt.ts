import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { AuthUserPayload } from '../types/express';

export function signToken(payload: AuthUserPayload): string {
  return jwt.sign(payload, env.JWT_SECRET, {
    expiresIn: env.JWT_EXPIRES_IN
  } as jwt.SignOptions);
}

export function verifyToken(token: string): AuthUserPayload | null {
  try {
    return jwt.verify(token, env.JWT_SECRET) as AuthUserPayload;
  } catch (error) {
    return null;
  }
}
