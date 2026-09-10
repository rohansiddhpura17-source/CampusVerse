import { PrismaClient } from '@prisma/client';
import { env } from '../config/env';

/**
 * Prisma Service configured with connection pooling parameters for PostgreSQL.
 * In production, connection_limit and pool_timeout can be passed in DATABASE_URL
 * or defaulted through the datasource configuration.
 */
export const prisma = new PrismaClient({
  log: env.NODE_ENV === 'development' ? ['warn', 'error'] : ['error'],
  datasourceUrl: env.DATABASE_URL
});

// Graceful disconnection on application termination
process.on('beforeExit', async () => {
  await prisma.$disconnect();
});

