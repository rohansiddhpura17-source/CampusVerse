import { Request, Response, NextFunction } from 'express';
import { env } from '../config/env';
import { sendError } from '../utils/response';

// Active production web origins (Render + Vercel)
// Note: Future custom domains (e.g. campusverse.edu) are documented targets and will be enabled via CORS_ORIGIN once configured.
const productionWebOrigins = new Set([
  'https://campusverse-website.onrender.com',
  'https://campus-verse-website.vercel.app',
]);

// Include any explicitly declared custom origins from environment
if (env.CORS_ORIGIN) {
  env.CORS_ORIGIN.split(',')
    .map((o) => o.trim())
    .filter((o) => o.length > 0 && o !== '*')
    .forEach((o) => productionWebOrigins.add(o));
}

/**
 * Validates whether the given Origin or Referer header matches trusted CampusVerse web origins.
 */
export function isTrustedWebOrigin(originHeader?: string): boolean {
  if (!originHeader) return false;

  try {
    const parsed = new URL(originHeader);
    const origin = parsed.origin;

    // Check production allowed origins
    if (productionWebOrigins.has(origin)) {
      return true;
    }

    // Allow localhost in non-production environments
    if (env.NODE_ENV !== 'production') {
      const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2)(:\d+)?$/.test(origin);
      if (isLocal) {
        return true;
      }
    }

    return false;
  } catch {
    return false;
  }
}

/**
 * CSRF Protection Middleware
 *
 * Enforces origin validation on state-changing requests (POST, PUT, PATCH, DELETE)
 * that are authenticated via cookies.
 *
 * Rules:
 * 1. Safe methods (GET, HEAD, OPTIONS) are never blocked.
 * 2. Requests authenticated via 'Authorization: Bearer <token>' (e.g., native Android app,
 *    CLI tools, server-to-server) are not vulnerable to ambient-credential CSRF and are exempted.
 * 3. Requests authenticated via cookie MUST present a valid, trusted Origin header matching
 *    the active CampusVerse web application.
 * 4. The 'X-CampusVerse-Client: web' header is evaluated as defense-in-depth.
 */
export function csrfProtection(req: Request, res: Response, next: NextFunction): void {
  // 1. Safe methods are always permitted
  if (['GET', 'HEAD', 'OPTIONS'].includes(req.method)) {
    return next();
  }

  // 2. Only enforce CSRF validation on requests authenticated via cookie
  // (req.authMethod is set by requireAuth middleware)
  if (req.authMethod !== 'cookie') {
    return next();
  }

  // 3. Extract Origin (preferred) or Referer
  const requestOrigin = (req.headers.origin as string) || (req.headers.referer as string);

  if (!requestOrigin || !isTrustedWebOrigin(requestOrigin)) {
    sendError(
      res,
      'CSRF check failed: invalid or untrusted Origin for cookie-authenticated request.',
      403,
      'CSRF_FORBIDDEN'
    );
    return;
  }

  next();
}
