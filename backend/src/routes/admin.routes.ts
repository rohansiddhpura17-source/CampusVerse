import { Router } from 'express';
import {
  getAdminDashboard,
  getAdminUsers,
  getUserById,
  updateUserStatus,
  resetUserPassword,
  getAdminVerifications,
  reviewVerification,
  getAdminReports,
  resolveReport,
  getAdminMarketplace,
  moderateMarketplaceItem,
  getAdminEvents,
  moderateEvent,
  getAdminJobs,
  moderateJob,
  getAdminMentorship,
  moderateMentor,
  getAdminAnnouncements,
  createAnnouncement,
  getAdminSettings,
  updateAdminSettings,
  getAdminNotes,
  moderateNote,
  deleteAdminNote,
  createAnnouncementSchema,
  updateUserStatusSchema,
  reviewVerificationSchema,
  resolveReportSchema,
  moderationActionSchema,
  noteModerationActionSchema
} from '../controllers/admin.controller';
import {
  getFinanceOverview,
  getFinanceTransactions,
  processTransactionRefund,
  refundTransactionSchema
} from '../controllers/admin.finance.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { requireAdmin } from '../middleware/rbac.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

// Apply auth and admin check to all admin routes
router.use(requireAuth);
router.use(requireAdmin);

// Dashboard
router.get('/dashboard', getAdminDashboard);

// User Management
router.get('/users', getAdminUsers);
router.get('/users/:id', getUserById);
router.patch('/users/:id/status', validateBody(updateUserStatusSchema), updateUserStatus);
router.post('/users/:id/reset-password', resetUserPassword);

// Verification
router.get('/verifications', getAdminVerifications);
router.post('/verifications/:id/review', validateBody(reviewVerificationSchema), reviewVerification);

// Reports & Moderation
router.get('/reports', getAdminReports);
router.post('/reports/:id/resolve', validateBody(resolveReportSchema), resolveReport);

// Marketplace Moderation
router.get('/marketplace', getAdminMarketplace);
router.patch('/marketplace/:id/moderate', validateBody(moderationActionSchema), moderateMarketplaceItem);

// Events & Jobs Management
router.get('/events', getAdminEvents);
router.patch('/events/:id/moderate', validateBody(moderationActionSchema), moderateEvent);
router.get('/jobs', getAdminJobs);
router.patch('/jobs/:id/moderate', validateBody(moderationActionSchema), moderateJob);

// Mentorship Management
router.get('/mentorship', getAdminMentorship);
router.patch('/mentorship/:id/moderate', validateBody(moderationActionSchema), moderateMentor);

// Announcements
router.get('/announcements', getAdminAnnouncements);
router.post('/announcements', validateBody(createAnnouncementSchema), createAnnouncement);

// Notes Moderation
router.get('/notes', getAdminNotes);
router.patch('/notes/:id/moderate', validateBody(noteModerationActionSchema), moderateNote);
router.delete('/notes/:id', deleteAdminNote);

// Settings
router.get('/settings', getAdminSettings);
router.patch('/settings', updateAdminSettings);

// Financial Management
router.get('/finance/overview', getFinanceOverview);
router.get('/finance/transactions', getFinanceTransactions);
router.post('/finance/transactions/:id/refund', validateBody(refundTransactionSchema), processTransactionRefund);

export default router;

