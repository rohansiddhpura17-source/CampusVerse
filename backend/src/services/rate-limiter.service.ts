import { Request, Response, NextFunction } from 'express';
import { sendError } from '../utils/response';

export interface RateLimitResult {
  allowed: boolean;
  current: number;
  limit: number;
  remaining: number;
  resetAt: number; // Unix epoch ms
  retryAfterSeconds: number;
}

export interface RateLimiterStore {
  consume(key: string, limit: number, windowMs: number): Promise<RateLimitResult>;
  reset(key: string): Promise<void>;
  clear(): Promise<void>;
}

/**
 * High-performance In-Memory Rate Limiter Store
 * Thread-safe for single-node instances, development, and unit test suites.
 */
export class MemoryRateLimiterStore implements RateLimiterStore {
  private hits: Map<string, { count: number; resetAt: number }> = new Map();
  private cleanupTimer: NodeJS.Timeout | null = null;

  constructor() {
    // Periodic cleanup of stale records every 60 seconds
    this.cleanupTimer = setInterval(() => {
      const now = Date.now();
      for (const [key, record] of this.hits.entries()) {
        if (record.resetAt <= now) {
          this.hits.delete(key);
        }
      }
    }, 60000);

    if (this.cleanupTimer.unref) {
      this.cleanupTimer.unref();
    }
  }

  public async consume(key: string, limit: number, windowMs: number): Promise<RateLimitResult> {
    const now = Date.now();
    const existing = this.hits.get(key);

    if (!existing || existing.resetAt <= now) {
      const resetAt = now + windowMs;
      this.hits.set(key, { count: 1, resetAt });
      return {
        allowed: true,
        current: 1,
        limit,
        remaining: Math.max(0, limit - 1),
        resetAt,
        retryAfterSeconds: 0
      };
    }

    existing.count += 1;
    const remaining = Math.max(0, limit - existing.count);
    const retryAfterSeconds = Math.max(1, Math.ceil((existing.resetAt - now) / 1000));
    const allowed = existing.count <= limit;

    return {
      allowed,
      current: existing.count,
      limit,
      remaining,
      resetAt: existing.resetAt,
      retryAfterSeconds: allowed ? 0 : retryAfterSeconds
    };
  }

  public async reset(key: string): Promise<void> {
    this.hits.delete(key);
  }

  public async clear(): Promise<void> {
    this.hits.clear();
  }

  public destroy(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
  }
}

/**
 * Pluggable Redis Rate Limiter Store Interface
 * Ready to connect when REDIS_URL is configured in distributed production deployment.
 */
export class RedisRateLimiterStore implements RateLimiterStore {
  private fallback: MemoryRateLimiterStore;

  constructor(private redisClient?: any) {
    this.fallback = new MemoryRateLimiterStore();
  }

  public async consume(key: string, limit: number, windowMs: number): Promise<RateLimitResult> {
    if (!this.redisClient) {
      return this.fallback.consume(key, limit, windowMs);
    }

    try {
      const windowSeconds = Math.ceil(windowMs / 1000);
      const multi = this.redisClient.multi();
      multi.incr(key);
      multi.ttl(key);
      const [countRes, ttlRes] = await multi.exec();

      const current = typeof countRes === 'number' ? countRes : countRes?.[1] || 1;
      let ttl = typeof ttlRes === 'number' ? ttlRes : ttlRes?.[1] || windowSeconds;

      if (ttl === -1 || ttl === -2) {
        await this.redisClient.expire(key, windowSeconds);
        ttl = windowSeconds;
      }

      const now = Date.now();
      const resetAt = now + ttl * 1000;
      const allowed = current <= limit;
      const retryAfterSeconds = allowed ? 0 : Math.max(1, ttl);

      return {
        allowed,
        current,
        limit,
        remaining: Math.max(0, limit - current),
        resetAt,
        retryAfterSeconds
      };
    } catch (err) {
      // Fallback to local memory store if Redis connectivity encounters errors
      console.warn('Redis rate limiter degraded to memory fallback:', err);
      return this.fallback.consume(key, limit, windowMs);
    }
  }

  public async reset(key: string): Promise<void> {
    if (this.redisClient) {
      await this.redisClient.del(key);
    }
    await this.fallback.reset(key);
  }

  public async clear(): Promise<void> {
    await this.fallback.clear();
  }
}

// Global default store instance
export const defaultRateLimiterStore: RateLimiterStore = new MemoryRateLimiterStore();

export interface RateLimiterOptions {
  windowMs: number;
  maxRequests: number;
  keyPrefix?: string;
  keyGenerator?: (req: Request) => string;
  errorMessage?: string;
  errorCode?: string;
  store?: RateLimiterStore;
}

/**
 * Creates an Express Rate Limiting middleware with standard RFC HTTP headers
 */
export function createRateLimiter(options: RateLimiterOptions) {
  const store = options.store || defaultRateLimiterStore;
  const keyPrefix = options.keyPrefix || 'rl';
  const errorCode = options.errorCode || 'RATE_LIMITED';
  const errorMessage =
    options.errorMessage || 'Too many requests. Please try again later.';

  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    // Generate key based on authenticated user ID or client IP
    let identity = 'anonymous';
    if (options.keyGenerator) {
      identity = options.keyGenerator(req);
    } else if ((req as any).user?.id) {
      identity = `user:${(req as any).user.id}`;
    } else {
      const forwarded = req.headers['x-forwarded-for'];
      const rawIp = typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : req.ip || req.socket.remoteAddress || 'unknown';
      identity = `ip:${rawIp}`;
    }

    const key = `${keyPrefix}:${identity}`;

    try {
      const result = await store.consume(key, options.maxRequests, options.windowMs);

      // Set standard rate limit headers
      res.setHeader('X-RateLimit-Limit', result.limit.toString());
      res.setHeader('X-RateLimit-Remaining', result.remaining.toString());
      res.setHeader('X-RateLimit-Reset', Math.ceil(result.resetAt / 1000).toString());

      if (!result.allowed) {
        res.setHeader('Retry-After', result.retryAfterSeconds.toString());
        sendError(res, errorMessage, 429, errorCode, {
          retryAfterSeconds: result.retryAfterSeconds
        });
        return;
      }

      next();
    } catch (error) {
      // Never block legitimate requests on rate limiter internal error
      console.error('Rate limiter middleware internal error:', error);
      next();
    }
  };
}

/**
 * Production AI Endpoint Rate Limiter:
 * 10 requests per minute per user
 */
export const aiRateLimiter = createRateLimiter({
  keyPrefix: 'ai',
  windowMs: 60 * 1000,
  maxRequests: 10,
  errorMessage: 'AI query rate limit exceeded. Please wait a moment before sending another question.',
  keyGenerator: (req) => {
    const userId = (req as any).user?.id;
    if (userId) {
      return `user:${userId}`;
    }
    const forwarded = req.headers['x-forwarded-for'];
    const ip = typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : req.ip || 'unknown';
    return `ip:${ip}`;
  }
});

/**
 * Production OTP Request Rate Limiter:
 * 5 requests per 10 minutes per IP/email
 */
export const otpRateLimiter = createRateLimiter({
  keyPrefix: 'otp',
  windowMs: 10 * 60 * 1000,
  maxRequests: 5,
  errorMessage: 'Too many verification code requests. Please wait a few minutes before trying again.',
  keyGenerator: (req) => {
    const email = req.body?.email?.trim().toLowerCase();
    const forwarded = req.headers['x-forwarded-for'];
    const ip = typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : req.ip || 'unknown';
    return email ? `email:${email}:ip:${ip}` : `ip:${ip}`;
  }
});
