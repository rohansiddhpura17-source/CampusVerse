import express, { Application } from 'express';
import cors from 'cors';
import { env } from './config/env';
import apiRouter from './routes';
import { errorHandler } from './middleware/error.middleware';

const productionOrigins = [
  'https://campusverse.edu',
  'https://www.campusverse.edu',
  'https://app.campusverse.edu',
  'https://admin.campusverse.edu'
];

const customOrigins = env.CORS_ORIGIN
  ? env.CORS_ORIGIN.split(',').map(o => o.trim()).filter(o => o.length > 0 && o !== '*')
  : [];

const allowedOrigins = new Set([...productionOrigins, ...customOrigins]);

export function createApp(): Application {
  const app = express();

  // Core Middlewares — Strict CORS Configuration
  app.use(cors({
    origin: (origin, callback) => {
      // Allow requests with no origin (mobile apps, curl, server-to-server)
      if (!origin) {
        return callback(null, true);
      }

      // Allow localhost and local emulator network in development/test
      if (env.NODE_ENV !== 'production') {
        const isLocalhost = /^https?:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2)(:\d+)?$/.test(origin);
        if (isLocalhost) {
          return callback(null, true);
        }
      }

      // Enforce explicit production origins
      if (allowedOrigins.has(origin)) {
        return callback(null, true);
      }

      // Reject unexpected origins
      return callback(null, false);
    },
    credentials: true
  }));
  app.use(express.json({
    limit: '10mb',
    verify: (req: any, _res, buf) => {
      req.rawBody = buf;
    }
  }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // API Routes
  app.use('/api/v1', apiRouter);

  // 404 Handler
  app.use((req, res) => {
    res.status(404).json({
      success: false,
      error: {
        code: 'NOT_FOUND',
        message: `Resource not found at route: ${req.method} ${req.originalUrl}`
      }
    });
  });

  // Global Error Handler
  app.use(errorHandler);

  return app;
}

export const app = createApp();
