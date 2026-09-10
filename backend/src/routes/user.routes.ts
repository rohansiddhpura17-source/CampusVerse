import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';
import {
  getUserById,
  updateUser,
  updateStudentProfile,
  updateAlumniProfile,
  getPrivacySettings,
  updatePrivacySettings,
  getSecuritySettings,
  updateSecuritySettings,
  createAccountRecovery,
  updateUserSchema,
  updateStudentProfileSchema,
  updateAlumniProfileSchema
} from '../controllers/user.controller';

const router = Router();

router.use(requireAuth);

router.patch('/profile/student', validateBody(updateStudentProfileSchema), updateStudentProfile);
router.patch('/profile/alumni', validateBody(updateAlumniProfileSchema), updateAlumniProfile);
router.get('/settings/privacy', getPrivacySettings);
router.patch('/settings/privacy', updatePrivacySettings);
router.get('/settings/security', getSecuritySettings);
router.patch('/settings/security', updateSecuritySettings);
router.post('/settings/account-recovery', createAccountRecovery);
router.get('/:id', getUserById);
router.patch('/:id', validateBody(updateUserSchema), updateUser);

export default router;
