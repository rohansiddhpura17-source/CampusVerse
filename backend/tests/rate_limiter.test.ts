import express from 'express';
import request from 'supertest';
import {
  MemoryRateLimiterStore,
  createRateLimiter
} from '../src/services/rate-limiter.service';

describe('Rate Limiter & Abuse Prevention Suite', () => {
  describe('1. MemoryRateLimiterStore Unit Tests', () => {
    it('should accurately count requests and enforce thresholds', async () => {
      const store = new MemoryRateLimiterStore();
      const key = 'test:user:123';
      const limit = 3;
      const windowMs = 5000;

      // Request 1
      const res1 = await store.consume(key, limit, windowMs);
      expect(res1.allowed).toBe(true);
      expect(res1.current).toBe(1);
      expect(res1.remaining).toBe(2);

      // Request 2
      const res2 = await store.consume(key, limit, windowMs);
      expect(res2.allowed).toBe(true);
      expect(res2.current).toBe(2);
      expect(res2.remaining).toBe(1);

      // Request 3
      const res3 = await store.consume(key, limit, windowMs);
      expect(res3.allowed).toBe(true);
      expect(res3.current).toBe(3);
      expect(res3.remaining).toBe(0);

      // Request 4 (Exceeded)
      const res4 = await store.consume(key, limit, windowMs);
      expect(res4.allowed).toBe(false);
      expect(res4.current).toBe(4);
      expect(res4.remaining).toBe(0);
      expect(res4.retryAfterSeconds).toBeGreaterThan(0);

      store.destroy();
    });

    it('should isolate distinct users and distinct keys', async () => {
      const store = new MemoryRateLimiterStore();
      const userA = 'test:user:alice';
      const userB = 'test:user:bob';

      // Exhaust Alice's quota
      await store.consume(userA, 1, 10000);
      const blockedAlice = await store.consume(userA, 1, 10000);
      expect(blockedAlice.allowed).toBe(false);

      // Bob should still have full quota
      const allowedBob = await store.consume(userB, 1, 10000);
      expect(allowedBob.allowed).toBe(true);

      store.destroy();
    });

    it('should allow manual reset of keys', async () => {
      const store = new MemoryRateLimiterStore();
      const key = 'test:reset:key';

      await store.consume(key, 1, 10000);
      const blocked = await store.consume(key, 1, 10000);
      expect(blocked.allowed).toBe(false);

      await store.reset(key);
      const allowedAfterReset = await store.consume(key, 1, 10000);
      expect(allowedAfterReset.allowed).toBe(true);

      store.destroy();
    });
  });

  describe('2. Express Middleware Integration & HTTP 429 Verification', () => {
    it('should return HTTP 429 with RFC headers when rate limit is exceeded', async () => {
      const testStore = new MemoryRateLimiterStore();
      const app = express();
      app.use(express.json());

      const limiter = createRateLimiter({
        windowMs: 10000,
        maxRequests: 2,
        keyPrefix: 'test_mw',
        errorMessage: 'Too many requests for test route.',
        store: testStore
      });

      app.get('/test-limit', limiter, (req, res) => {
        res.status(200).json({ success: true, message: 'OK' });
      });

      // Request 1: 200 OK
      const res1 = await request(app).get('/test-limit');
      expect(res1.status).toBe(200);
      expect(res1.headers['x-ratelimit-limit']).toBe('2');
      expect(res1.headers['x-ratelimit-remaining']).toBe('1');

      // Request 2: 200 OK
      const res2 = await request(app).get('/test-limit');
      expect(res2.status).toBe(200);
      expect(res2.headers['x-ratelimit-remaining']).toBe('0');

      // Request 3: 429 Too Many Requests
      const res3 = await request(app).get('/test-limit');
      expect(res3.status).toBe(429);
      expect(res3.body.success).toBe(false);
      expect(res3.body.error.code).toBe('RATE_LIMITED');
      expect(res3.body.error.message).toBe('Too many requests for test route.');
      expect(res3.headers['retry-after']).toBeDefined();
      expect(parseInt(res3.headers['retry-after'], 10)).toBeGreaterThan(0);

      testStore.destroy();
    });

    it('should rate limit based on authenticated user ID when present', async () => {
      const testStore = new MemoryRateLimiterStore();
      const app = express();
      app.use(express.json());

      // Mock auth middleware
      app.use((req, res, next) => {
        const auth = req.headers.authorization;
        if (auth === 'Bearer user-1') {
          (req as any).user = { id: 'user-1' };
        } else if (auth === 'Bearer user-2') {
          (req as any).user = { id: 'user-2' };
        }
        next();
      });

      const limiter = createRateLimiter({
        windowMs: 10000,
        maxRequests: 1,
        keyPrefix: 'auth_test',
        store: testStore
      });

      app.get('/protected-action', limiter, (req, res) => {
        res.status(200).json({ success: true });
      });

      // User 1 first request -> PASS
      const r1 = await request(app)
        .get('/protected-action')
        .set('Authorization', 'Bearer user-1');
      expect(r1.status).toBe(200);

      // User 1 second request -> 429
      const r2 = await request(app)
        .get('/protected-action')
        .set('Authorization', 'Bearer user-1');
      expect(r2.status).toBe(429);

      // User 2 first request -> PASS (isolated from User 1)
      const r3 = await request(app)
        .get('/protected-action')
        .set('Authorization', 'Bearer user-2');
      expect(r3.status).toBe(200);

      testStore.destroy();
    });
  });
});
