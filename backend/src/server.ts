import { app } from './app';
import { env } from './config/env';
import { prisma } from './services/prisma.service';

const PORT = env.PORT;

async function startServer() {
  try {
    try {
      await prisma.$connect();
      console.log('✅ Database connected successfully via Prisma');
    } catch (dbErr: any) {
      console.warn('⚠️ Database initial connection failed or database is currently paused:', dbErr?.message || dbErr);
      console.warn('📡 Server will continue to start and serve requests once database resumes.');
    }

    const server = app.listen(PORT, () => {
      console.log(`🚀 CampusVerse Backend API listening on port ${PORT}`);
      console.log(`📡 Health Check: http://localhost:${PORT}/api/v1/health`);
    });

    const shutdown = async () => {
      console.log('Shutting down server gracefully...');
      server.close(async () => {
        await prisma.$disconnect().catch(() => {});
        console.log('Database disconnected. Process exited.');
        process.exit(0);
      });
    };

    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  } catch (error) {
    console.error('Failed to start server:', error);
    process.exit(1);
  }
}

// Global process safeguards against unhandled errors
process.on('unhandledRejection', (reason: any) => {
  console.error('⚠️ [Server Safeguard] Unhandled Rejection:', reason?.message || reason);
});

process.on('uncaughtException', (err: any) => {
  console.error('⚠️ [Server Safeguard] Uncaught Exception:', err?.message || err);
});

if (require.main === module) {
  startServer();
}

export default app;
