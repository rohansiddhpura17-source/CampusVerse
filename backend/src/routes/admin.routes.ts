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
  noteModerationActionSchema,
} from '../controllers/admin.controller';
import {
  suspendUser,
  reactivateUser,
  patchUser,
  getAdminStudents,
  getAdminAspirants,
  getAdminAlumni,
  getAdminColleges,
  createAdminCollege,
  patchAdminCollege,
  deleteAdminCollege,
  getAdminCourses,
  getAdminScholarships,
  getAdminProjects,
  patchAdminReport,
  resolveModeration,
  getAdminAuditLogs,
  getAdminAnalytics,
  suspendUserSchema,
  patchUserSchema,
  collegeSchema,
  patchCollegeSchema,
  patchReportSchema,
  resolveModerationSchema,
} from '../controllers/admin.management.controller';
import {
  getAdminRoles,
  createAdminRole,
  getAdminPermissions,
  assignUserRole,
  revokeUserRole,
  updateRolePermissions,
  getUserPermissionsDetails,
  createRoleSchema,
  assignRoleSchema,
  updateRolePermissionsSchema,
} from '../controllers/admin.rbac.controller';
import {
  getAdminSecurityOverview,
  getAdminSessions,
  revokeAdminSession,
  revokeAllAdminSessions,
  revokeCurrentSession,
  revokeAllSessions,
} from '../controllers/admin.security.controller';
import {
  getAdminSystemSettings,
  updateAdminSystemSettings,
  getAdminFeatureFlags,
  toggleAdminFeatureFlag,
  updateSystemSettingsSchema,
  updateFeatureFlagSchema,
} from '../controllers/admin.system.controller';
import {
  getFinanceOverview,
  getFinanceTransactions,
  processTransactionRefund,
  refundTransactionSchema,
} from '../controllers/admin.finance.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { requireAdministrativeAccess, requirePermission } from '../middleware/rbac.middleware';
import { validateBody } from '../middleware/validation.middleware';
import { adminRateLimiter, adminSensitiveRateLimiter } from '../services/rate-limiter.service';
import { sendError } from '../utils/response';

const router = Router();

// Apply auth, administrative RBAC check, and standard admin rate limiting
router.use(requireAuth);
router.use(requireAdministrativeAccess);
router.use(adminRateLimiter);

// -----------------------------------------------------------------------------
// 1. Dashboard & Telemetry
// -----------------------------------------------------------------------------
router.get('/dashboard', requirePermission('analytics.read'), getAdminDashboard);
router.get('/analytics', requirePermission('analytics.read'), getAdminAnalytics);

// -----------------------------------------------------------------------------
// 2. User Management & Cohorts
// -----------------------------------------------------------------------------
router.get('/users', requirePermission('users.read'), getAdminUsers);
router.get('/users/:id', requirePermission('users.read'), getUserById);
router.patch('/users/:id', requirePermission('users.update'), adminSensitiveRateLimiter, validateBody(patchUserSchema), patchUser);
router.post('/users/:id/suspend', requirePermission('users.suspend'), adminSensitiveRateLimiter, validateBody(suspendUserSchema), suspendUser);
router.post('/users/:id/reactivate', requirePermission('users.suspend'), adminSensitiveRateLimiter, reactivateUser);
router.patch('/users/:id/status', requirePermission('users.update'), adminSensitiveRateLimiter, validateBody(updateUserStatusSchema), updateUserStatus);
router.post('/users/:id/reset-password', requirePermission('users.update'), adminSensitiveRateLimiter, resetUserPassword);

router.get('/students', requirePermission('students.read'), getAdminStudents);
router.get('/aspirants', requirePermission('aspirants.read'), getAdminAspirants);
router.get('/alumni', requirePermission('alumni.read'), getAdminAlumni);

// -----------------------------------------------------------------------------
// 3. RBAC & Granular Permissions
// -----------------------------------------------------------------------------
router.get('/roles', requirePermission('settings.manage'), getAdminRoles);
router.post('/roles', requirePermission('settings.manage'), adminSensitiveRateLimiter, validateBody(createRoleSchema), createAdminRole);
router.patch('/roles/:id/permissions', requirePermission('settings.manage'), adminSensitiveRateLimiter, validateBody(updateRolePermissionsSchema), updateRolePermissions);
router.get('/permissions', requirePermission('settings.manage'), getAdminPermissions);
router.post('/users/:id/roles', requirePermission('users.update'), adminSensitiveRateLimiter, validateBody(assignRoleSchema), assignUserRole);
router.delete('/users/:id/roles/:roleId', requirePermission('users.update'), adminSensitiveRateLimiter, revokeUserRole);
router.get('/users/:id/permissions', requirePermission('users.read'), getUserPermissionsDetails);

// -----------------------------------------------------------------------------
// 4. Admin Security & Session Management
// -----------------------------------------------------------------------------
router.get('/security/overview', getAdminSecurityOverview);
router.get('/security/sessions', getAdminSessions);
router.post('/security/sessions/:id/revoke', adminSensitiveRateLimiter, revokeAdminSession);
router.post('/security/sessions/revoke-all', adminSensitiveRateLimiter, revokeAllAdminSessions);
router.post('/security/revoke-session', adminSensitiveRateLimiter, revokeCurrentSession);
router.post('/security/revoke-all-sessions', adminSensitiveRateLimiter, revokeAllSessions);

// -----------------------------------------------------------------------------
// 5. System Settings & Feature Flags (Separated Public / Server Telemetry)
// -----------------------------------------------------------------------------
router.get('/system/settings', requirePermission('settings.manage'), getAdminSystemSettings);
router.patch('/system/settings', requirePermission('settings.manage'), adminSensitiveRateLimiter, validateBody(updateSystemSettingsSchema), updateAdminSystemSettings);
router.get('/system/feature-flags', requirePermission('settings.manage'), getAdminFeatureFlags);
router.patch('/system/feature-flags/:key', requirePermission('settings.manage'), adminSensitiveRateLimiter, validateBody(updateFeatureFlagSchema), toggleAdminFeatureFlag);

// Legacy Settings routes (backwards compatibility)
router.get('/settings', requirePermission('settings.manage'), getAdminSettings);
router.patch('/settings', requirePermission('settings.manage'), updateAdminSettings);

// -----------------------------------------------------------------------------
// 6. Colleges & Academics
// -----------------------------------------------------------------------------
router.get('/colleges', requirePermission('colleges.read'), getAdminColleges);
router.post('/colleges', requirePermission('colleges.create'), validateBody(collegeSchema), createAdminCollege);
router.patch('/colleges/:id', requirePermission('colleges.update'), validateBody(patchCollegeSchema), patchAdminCollege);
router.delete('/colleges/:id', requirePermission('colleges.delete'), deleteAdminCollege);

router.get('/courses', requirePermission('courses.read'), getAdminCourses);
router.get('/scholarships', requirePermission('scholarships.read'), getAdminScholarships);
router.get('/projects', requirePermission('projects.read'), getAdminProjects);

// -----------------------------------------------------------------------------
// 7. Verifications
// -----------------------------------------------------------------------------
router.get('/verifications', requirePermission('users.update'), getAdminVerifications);
router.post('/verifications/:id/review', requirePermission('users.update'), validateBody(reviewVerificationSchema), reviewVerification);

// -----------------------------------------------------------------------------
// 8. Reports & Moderation
// -----------------------------------------------------------------------------
router.get('/reports', requirePermission('moderation.read'), getAdminReports);
router.patch('/reports/:id', requirePermission('moderation.resolve'), validateBody(patchReportSchema), patchAdminReport);
router.post('/reports/:id/resolve', requirePermission('moderation.resolve'), validateBody(resolveReportSchema), resolveReport);
router.post('/moderation/:id/resolve', requirePermission('moderation.resolve'), validateBody(resolveModerationSchema), resolveModeration);

router.get('/marketplace', requirePermission('moderation.read'), getAdminMarketplace);
router.patch('/marketplace/:id/moderate', requirePermission('moderation.resolve'), validateBody(moderationActionSchema), moderateMarketplaceItem);

router.get('/events', requirePermission('events.read'), getAdminEvents);
router.patch('/events/:id/moderate', requirePermission('events.update'), validateBody(moderationActionSchema), moderateEvent);

router.get('/jobs', requirePermission('moderation.read'), getAdminJobs);
router.patch('/jobs/:id/moderate', requirePermission('moderation.resolve'), validateBody(moderationActionSchema), moderateJob);

router.get('/mentorship', requirePermission('mentorship.read'), getAdminMentorship);
router.patch('/mentorship/:id/moderate', requirePermission('mentorship.moderate'), validateBody(moderationActionSchema), moderateMentor);

router.get('/notes', requirePermission('moderation.read'), getAdminNotes);
router.patch('/notes/:id/moderate', requirePermission('moderation.resolve'), validateBody(noteModerationActionSchema), moderateNote);
router.delete('/notes/:id', requirePermission('moderation.resolve'), deleteAdminNote);

// -----------------------------------------------------------------------------
// 9. Announcements & Communications
// -----------------------------------------------------------------------------
router.get('/announcements', requirePermission('events.read'), getAdminAnnouncements);
router.post('/announcements', requirePermission('events.create'), validateBody(createAnnouncementSchema), createAnnouncement);

// -----------------------------------------------------------------------------
// 10. Audit Trail (Strictly Append-Only)
// -----------------------------------------------------------------------------
// Explicitly reject any mutation attempts on audit entries
router.all('/audit-logs/:id?', (req, res, next) => {
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method)) {
    sendError(res, 'Audit logs are strictly append-only and cannot be modified or deleted.', 405, 'AUDIT_LOG_IMMUTABLE');
    return;
  }
  next();
});
router.get('/audit-logs', requirePermission('audit.read'), getAdminAuditLogs);

// -----------------------------------------------------------------------------
// 11. Financial Management
// -----------------------------------------------------------------------------
router.get('/finance/overview', requirePermission('settings.manage'), getFinanceOverview);
router.get('/finance/transactions', requirePermission('settings.manage'), getFinanceTransactions);
router.post('/finance/transactions/:id/refund', requirePermission('settings.manage'), validateBody(refundTransactionSchema), processTransactionRefund);

export default router;
