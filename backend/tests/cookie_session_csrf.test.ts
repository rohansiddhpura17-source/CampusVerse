import request from 'supertest';
import { Request, Response, NextFunction } from 'express';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';
import {
  SESSION_COOKIE_NAME,
  DEV_SESSION_COOKIE_NAME,
  PROD_SESSION_COOKIE_NAME,
  getSessionCookieName,
  getSessionCookieOptions
} from '../src/config/cookie';
import { isTrustedWebOrigin } from '../src/middleware/csrf.middleware';
import { requireAuth } from '../src/middleware/auth.middleware';
import { signToken } from '../src/utils/jwt';

describe('Phase 3A — Secure Web Session Cookie & CSRF Suite', () => {
  const webUserEmail = `web_test_${Date.now()}@campusverse.edu`;
  const legacyUserEmail = `legacy_test_${Date.now()}@campusverse.edu`;
  const testPassword = 'Password123';
  let webSessionCookie: string;
  let legacyBearerToken: string;
  let testUserId: string;

  beforeAll(async () => {
    // Clean up test emails
    await prisma.user.deleteMany({
      where: {
        email: { in: [webUserEmail, legacyUserEmail] }
      }
    });
  });

  afterAll(async () => {
    await prisma.user.deleteMany({
      where: {
        email: { in: [webUserEmail, legacyUserEmail] }
      }
    });
  });

  describe('A & B: Web Client Authentication (X-CampusVerse-Client: web)', () => {
    it('B: POST /auth/register with X-CampusVerse-Client: web sets HttpOnly cookie and OMITS token in JSON', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .set('X-CampusVerse-Client', 'web')
        .send({
          name: 'Web Registered User',
          email: webUserEmail,
          password: testPassword,
          role: 'STUDENT'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);

      // CRITICAL: Web client JSON response MUST NOT expose JWT token
      expect(res.body.data.token).toBeUndefined();
      expect(res.body.data.user).toBeDefined();
      expect(res.body.data.user.email).toBe(webUserEmail);
      testUserId = res.body.data.user.userId;

      // Cookie verification: Set-Cookie header must be present
      const setCookieHeader = res.headers['set-cookie'];
      expect(setCookieHeader).toBeDefined();
      const cookieStr = Array.isArray(setCookieHeader) ? setCookieHeader.join('; ') : setCookieHeader;

      // Must be HttpOnly and Path=/
      expect(cookieStr.toLowerCase()).toContain('httponly');
      expect(cookieStr.toLowerCase()).toContain('path=/');
      expect(cookieStr).toContain(SESSION_COOKIE_NAME);

      const sessionMatch = cookieStr.match(new RegExp(`(${SESSION_COOKIE_NAME}=[^;]+)`));
      expect(sessionMatch).toBeTruthy();
      webSessionCookie = sessionMatch![1];
    });

    it('A: POST /auth/login with X-CampusVerse-Client: web sets HttpOnly cookie and OMITS token in JSON', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .set('X-CampusVerse-Client', 'web')
        .send({
          email: webUserEmail,
          password: testPassword
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // CRITICAL: Web client JSON response MUST NOT expose JWT token
      expect(res.body.data.token).toBeUndefined();
      expect(res.body.data.user).toBeDefined();
      expect(res.body.data.user.email).toBe(webUserEmail);

      const setCookieHeader = res.headers['set-cookie'];
      expect(setCookieHeader).toBeDefined();
      const cookieStr = Array.isArray(setCookieHeader) ? setCookieHeader.join('; ') : setCookieHeader;
      expect(cookieStr.toLowerCase()).toContain('httponly');
      expect(cookieStr.toLowerCase()).toContain('path=/');

      const sessionMatch = cookieStr.match(new RegExp(`(${SESSION_COOKIE_NAME}=[^;]+)`));
      expect(sessionMatch).toBeTruthy();
      webSessionCookie = sessionMatch![1];
    });
  });

  describe('C & D: Android / Legacy Client Backward Compatibility', () => {
    it('D: POST /auth/register without web header returns JWT token in JSON for Android / legacy clients', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Legacy Android User',
          email: legacyUserEmail,
          password: testPassword,
          role: 'STUDENT'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);

      // Backward compatibility: Token MUST be returned in JSON for Android / CLI clients
      expect(res.body.data.token).toBeDefined();
      expect(typeof res.body.data.token).toBe('string');
      expect(res.body.data.token.length).toBeGreaterThan(20);
      expect(res.body.data.user).toBeDefined();
      expect(res.body.data.user.email).toBe(legacyUserEmail);

      legacyBearerToken = res.body.data.token;
    });

    it('C: POST /auth/login without web header returns JWT token in JSON for Android / legacy clients', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: legacyUserEmail,
          password: testPassword
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // Backward compatibility: Token MUST be returned in JSON for Android
      expect(res.body.data.token).toBeDefined();
      expect(typeof res.body.data.token).toBe('string');
      expect(res.body.data.token.length).toBeGreaterThan(20);
      expect(res.body.data.user).toBeDefined();

      legacyBearerToken = res.body.data.token;
    });
  });

  describe('Session Restoration & Dual Authentication (Cookie vs Bearer)', () => {
    it('GET /auth/me succeeds with cookie authentication without Bearer header', async () => {
      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Cookie', webSessionCookie);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.email).toBe(webUserEmail);
    });

    it('GET /auth/me succeeds with Authorization: Bearer token without cookie (Android)', async () => {
      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', `Bearer ${legacyBearerToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.email).toBe(legacyUserEmail);
    });

    it('GET /auth/me returns 401 when both cookie and Bearer token are absent', async () => {
      const res = await request(app)
        .get('/api/v1/auth/me');

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
    });
  });

  describe('Production vs Development Cookie-Name Enforcement', () => {
    let validToken: string;

    beforeAll(() => {
      validToken = signToken({
        userId: testUserId,
        email: webUserEmail,
        role: 'STUDENT',
        isAdminAuthorized: false
      });
    });

    it('Production mode: accepts ONLY __Host-campusverse_session and rejects campusverse_session', async () => {
      const origNodeEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';

      try {
        // 1. Request with ONLY development cookie name ('campusverse_session') in production MUST be rejected
        const mockReqDev = {
          cookies: { [DEV_SESSION_COOKIE_NAME]: validToken },
          headers: {},
          method: 'GET'
        } as unknown as Request;
        let devAuthError: any = null;
        let devAuthCalledNext = false;
        const mockResDev = {
          status: (code: number) => ({
            json: (body: any) => { devAuthError = { code, body }; }
          })
        } as unknown as Response;

        await requireAuth(mockReqDev, mockResDev, (() => { devAuthCalledNext = true; }) as NextFunction);
        expect(devAuthCalledNext).toBe(false);
        expect(devAuthError).toBeDefined();
        expect(devAuthError.code).toBe(401);

        // 2. Request with production cookie name ('__Host-campusverse_session') MUST be accepted
        const mockReqProd = {
          cookies: { [PROD_SESSION_COOKIE_NAME]: validToken },
          headers: {},
          method: 'GET'
        } as unknown as Request;
        let prodAuthCalledNext = false;
        const mockResProd = {
          status: () => ({ json: () => {} })
        } as unknown as Response;

        await requireAuth(mockReqProd, mockResProd, (() => { prodAuthCalledNext = true; }) as NextFunction);
        expect(prodAuthCalledNext).toBe(true);
        expect((mockReqProd as any).authMethod).toBe('cookie');

        // 3. Android Bearer fallback in production MUST be accepted
        const mockReqBearer = {
          cookies: {},
          headers: { authorization: `Bearer ${validToken}` },
          method: 'GET'
        } as unknown as Request;
        let bearerAuthCalledNext = false;
        const mockResBearer = {
          status: () => ({ json: () => {} })
        } as unknown as Response;

        await requireAuth(mockReqBearer, mockResBearer, (() => { bearerAuthCalledNext = true; }) as NextFunction);
        expect(bearerAuthCalledNext).toBe(true);
        expect((mockReqBearer as any).authMethod).toBe('bearer');
      } finally {
        process.env.NODE_ENV = origNodeEnv;
      }
    });

    it('Development mode: accepts campusverse_session', async () => {
      const origNodeEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'development';

      try {
        const mockReqDev = {
          cookies: { [DEV_SESSION_COOKIE_NAME]: validToken },
          headers: {},
          method: 'GET'
        } as unknown as Request;
        let devAuthCalledNext = false;
        const mockResDev = {
          status: () => ({ json: () => {} })
        } as unknown as Response;

        await requireAuth(mockReqDev, mockResDev, (() => { devAuthCalledNext = true; }) as NextFunction);
        expect(devAuthCalledNext).toBe(true);
        expect((mockReqDev as any).authMethod).toBe('cookie');
      } finally {
        process.env.NODE_ENV = origNodeEnv;
      }
    });
  });

  describe('Cookie Configuration & Security Attributes Verification', () => {
    it('Production cookie options enforce __Host- prefix, HttpOnly, Secure, SameSite=None, Path=/, and NO Domain', () => {
      const prodName = getSessionCookieName(true);
      expect(prodName).toBe('__Host-campusverse_session');

      const prodOptions = getSessionCookieOptions(true);
      expect(prodOptions.httpOnly).toBe(true);
      expect(prodOptions.secure).toBe(true);
      expect(prodOptions.sameSite).toBe('none');
      expect(prodOptions.path).toBe('/');
      expect(prodOptions.maxAge).toBe(7 * 24 * 60 * 60 * 1000);
      // Strictly host-only: domain MUST be undefined
      expect(prodOptions.domain).toBeUndefined();
    });

    it('Development cookie options enforce campusverse_session, HttpOnly, Secure=false, SameSite=Lax, Path=/, and NO Domain', () => {
      const devName = getSessionCookieName(false);
      expect(devName).toBe('campusverse_session');

      const devOptions = getSessionCookieOptions(false);
      expect(devOptions.httpOnly).toBe(true);
      expect(devOptions.secure).toBe(false);
      expect(devOptions.sameSite).toBe('lax');
      expect(devOptions.path).toBe('/');
      expect(devOptions.maxAge).toBe(7 * 24 * 60 * 60 * 1000);
      // Strictly host-only: domain MUST be undefined
      expect(devOptions.domain).toBeUndefined();
    });
  });

  describe('Exact Origin CSRF Security Tests', () => {
    it('Unit: isTrustedWebOrigin rejects prefix attacker origin https://campusverse-website.onrender.com.attacker.com', () => {
      expect(isTrustedWebOrigin('https://campusverse-website.onrender.com.attacker.com')).toBe(false);
    });

    it('Unit: isTrustedWebOrigin rejects query-string attacker origin https://attacker.com/?origin=https://campusverse-website.onrender.com', () => {
      expect(isTrustedWebOrigin('https://attacker.com/?origin=https://campusverse-website.onrender.com')).toBe(false);
    });

    it('Unit: isTrustedWebOrigin approves exact trusted production origin https://campusverse-website.onrender.com', () => {
      expect(isTrustedWebOrigin('https://campusverse-website.onrender.com')).toBe(true);
    });

    it('Integration: Origin https://campusverse-website.onrender.com.attacker.com returns 403 CSRF_FORBIDDEN', async () => {
      const res = await request(app)
        .post('/api/v1/auth/logout')
        .set('Cookie', webSessionCookie)
        .set('Origin', 'https://campusverse-website.onrender.com.attacker.com')
        .send({});

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('CSRF_FORBIDDEN');
    });

    it('Integration: Origin https://attacker.com/?origin=https://campusverse-website.onrender.com returns 403 CSRF_FORBIDDEN', async () => {
      const res = await request(app)
        .post('/api/v1/auth/logout')
        .set('Cookie', webSessionCookie)
        .set('Origin', 'https://attacker.com/?origin=https://campusverse-website.onrender.com')
        .send({});

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('CSRF_FORBIDDEN');
    });

    it('Integration: Missing Origin and Referer returns 403 CSRF_FORBIDDEN', async () => {
      const res = await request(app)
        .post('/api/v1/auth/logout')
        .set('Cookie', webSessionCookie)
        .send({});

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('CSRF_FORBIDDEN');
    });

    it('Integration: Trusted exact origin https://campusverse-website.onrender.com returns 200 OK', async () => {
      const res = await request(app)
        .post('/api/v1/auth/logout')
        .set('Cookie', webSessionCookie)
        .set('Origin', 'https://campusverse-website.onrender.com')
        .set('X-CampusVerse-Client', 'web')
        .send({});

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // Verify logout cleared the session cookie
      const clearedCookieHeader = res.headers['set-cookie'];
      expect(clearedCookieHeader).toBeDefined();
      const clearedStr = Array.isArray(clearedCookieHeader) ? clearedCookieHeader.join('; ') : clearedCookieHeader;
      expect(clearedStr).toMatch(/(expires=Thu, 01 Jan 1970|Max-Age=0)/i);
    });

    it('Android Bearer mutation without Origin header succeeds (exempted from CSRF)', async () => {
      const res = await request(app)
        .post('/api/v1/auth/logout')
        .set('Authorization', `Bearer ${legacyBearerToken}`)
        .send({});

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
    });

    it('Safe methods (GET) with cookie are unaffected by Origin header', async () => {
      // Re-login to get fresh web session cookie
      const loginRes = await request(app)
        .post('/api/v1/auth/login')
        .set('X-CampusVerse-Client', 'web')
        .send({ email: webUserEmail, password: testPassword });

      const setCookieHeader = loginRes.headers['set-cookie'];
      const cookieStr = Array.isArray(setCookieHeader) ? setCookieHeader.join('; ') : setCookieHeader;
      const match = cookieStr.match(new RegExp(`(${SESSION_COOKIE_NAME}=[^;]+)`));
      const freshCookie = match![1];

      // Send GET with untrusted Origin - should NOT be blocked by CSRF
      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Cookie', freshCookie)
        .set('Origin', 'https://any-arbitrary-origin.com');

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.email).toBe(webUserEmail);
    });
  });

  describe('Authoritative RBAC', () => {
    it('Protected admin route rejects student cookie session with 401 or 403', async () => {
      const loginRes = await request(app)
        .post('/api/v1/auth/login')
        .set('X-CampusVerse-Client', 'web')
        .send({ email: webUserEmail, password: testPassword });

      const cookieHeader = loginRes.headers['set-cookie'];
      const cookieStr = Array.isArray(cookieHeader) ? cookieHeader.join('; ') : cookieHeader;
      const match = cookieStr.match(new RegExp(`(${SESSION_COOKIE_NAME}=[^;]+)`));

      const res = await request(app)
        .get('/api/v1/admin/users')
        .set('Cookie', match![1]);

      expect([401, 403]).toContain(res.status);
      expect(res.body.success).toBe(false);
    });
  });
});
