import { CookieOptions, Response } from 'express';
import { env } from './env';

const isProduction = env.NODE_ENV === 'production';

/**
 * Standard session cookie names:
 * In production: '__Host-campusverse_session' enforces RFC 6265bis prefix requirements:
 * 1. Must be set with 'Secure' flag.
 * 2. Must not contain a 'Domain' attribute (strictly host-only).
 * 3. Must specify 'Path=/'.
 *
 * In development: 'campusverse_session' without '__Host-' prefix allows testing over
 * plain HTTP on localhost without browser rejection.
 */
export const PROD_SESSION_COOKIE_NAME = '__Host-campusverse_session';
export const DEV_SESSION_COOKIE_NAME = 'campusverse_session';

export function getSessionCookieName(isProd: boolean = env.NODE_ENV === 'production'): string {
  return isProd ? PROD_SESSION_COOKIE_NAME : DEV_SESSION_COOKIE_NAME;
}

export const SESSION_COOKIE_NAME = getSessionCookieName();

export function getSessionCookieOptions(isProd: boolean = env.NODE_ENV === 'production'): CookieOptions {
  return {
    httpOnly: true,
    secure: isProd, // Enforces HTTPS in production; false on local development
    sameSite: isProd ? 'none' : 'lax', // 'none' required for cross-site Render deployment; 'lax' for local dev
    path: '/',
    maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days (aligns with JWT_EXPIRES_IN)
    // NOTE: 'domain' is intentionally omitted to enforce a strictly host-only cookie.
    // Under no circumstances should Domain=.onrender.com or broad domains be specified.
  };
}

/**
 * Sets the HttpOnly session cookie on the outgoing response.
 */
export function setSessionCookie(res: Response, token: string): void {
  const options = getSessionCookieOptions();
  res.cookie(SESSION_COOKIE_NAME, token, options);
}

/**
 * Clears the session cookie matching the exact path and security attributes.
 */
export function clearSessionCookie(res: Response): void {
  const options = getSessionCookieOptions();
  res.clearCookie(SESSION_COOKIE_NAME, {
    httpOnly: options.httpOnly,
    secure: options.secure,
    sameSite: options.sameSite,
    path: options.path,
  });

  // Defensively clear the development/legacy cookie name as well
  if (SESSION_COOKIE_NAME !== DEV_SESSION_COOKIE_NAME) {
    res.clearCookie(DEV_SESSION_COOKIE_NAME, {
      httpOnly: options.httpOnly,
      secure: options.secure,
      sameSite: options.sameSite,
      path: options.path,
    });
  }
}
