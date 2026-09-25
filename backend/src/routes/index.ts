import { Router } from 'express';
import authRoutes from './auth.routes';
import userRoutes from './user.routes';
import verificationRoutes from './verification.routes';
import jobRoutes from './job.routes';
import mentorshipRoutes from './mentorship.routes';
import eventRoutes from './event.routes';
import marketplaceRoutes from './marketplace.routes';
import communityRoutes from './community.routes';
import messageRoutes from './message.routes';
import notificationRoutes from './notification.routes';
import reportRoutes from './report.routes';
import adminRoutes from './admin.routes';
import academicRoutes from './academic.routes';
import noteRoutes from './note.routes';
import libraryRoutes from './library.routes';
import aiRoutes from './ai.routes';
import alumniRoutes from './alumni.routes';
import careerRoutes from './career.routes';
import aspirantRoutes from './aspirant.routes';
import paymentRoutes from './payment.routes';
import profileRoutes from './profile.routes';
import projectRoutes from './project.routes';
import studentRoutes from './student.routes';
import { checkDatabaseHealth } from '../services/prisma.service';

const router = Router();

// Health check endpoint (Public)
router.get('/health', async (req, res) => {
  const dbStatus = await checkDatabaseHealth();
  res.status(200).json({
    status: 'HEALTHY',
    database: dbStatus,
    timestamp: new Date().toISOString()
  });
});

router.use('/auth', authRoutes);
router.use('/users', userRoutes);
router.use('/verifications', verificationRoutes);
router.use('/', jobRoutes);
router.use('/', mentorshipRoutes);
router.use('/events', eventRoutes);
router.use('/marketplace', marketplaceRoutes);
router.use('/', communityRoutes);
router.use('/conversations', messageRoutes);
router.use('/notifications', notificationRoutes);
router.use('/reports', reportRoutes);
router.use('/admin', adminRoutes);
router.use('/', academicRoutes);
router.use('/', noteRoutes);
router.use('/', libraryRoutes);
router.use('/', aiRoutes);
router.use('/', alumniRoutes);
router.use('/', careerRoutes);
router.use('/', aspirantRoutes);
router.use('/payments', paymentRoutes);
router.use('/profile', profileRoutes);
router.use('/projects', projectRoutes);
router.use('/students', studentRoutes);

export default router;
