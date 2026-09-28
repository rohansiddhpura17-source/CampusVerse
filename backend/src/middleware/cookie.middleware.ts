import { Request, Response, NextFunction } from 'express';

/**
 * Cookie Parser Middleware
 * Parses incoming 'Cookie' request headers into `req.cookies`.
 * Works with or without the external cookie-parser package.
 */
export function cookieParserMiddleware(req: Request, _res: Response, next: NextFunction): void {
  if (!req.cookies) {
    req.cookies = {};
    const cookieHeader = req.headers.cookie;
    if (typeof cookieHeader === 'string') {
      const pairs = cookieHeader.split(';');
      for (const pair of pairs) {
        const idx = pair.indexOf('=');
        if (idx !== -1) {
          const key = pair.substring(0, idx).trim();
          const val = pair.substring(idx + 1).trim();
          try {
            req.cookies[key] = decodeURIComponent(val);
          } catch {
            req.cookies[key] = val;
          }
        }
      }
    }
  }
  next();
}
