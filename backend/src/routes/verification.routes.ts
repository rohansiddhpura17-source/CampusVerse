import { Router } from 'express';
import {
  getVerifications,
  submitVerification,
  reviewVerification,
  submitVerificationSchema,
  reviewVerificationSchema
} from '../controllers/verification.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { requireAdmin, requireRole } from '../middleware/rbac.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/', requireAuth, requireAdmin, getVerifications);
router.post('/', requireAuth, requireRole('STUDENT', 'ALUMNI'), validateBody(submitVerificationSchema), submitVerification);
router.patch('/:id', requireAuth, requireAdmin, validateBody(reviewVerificationSchema), reviewVerification);

export default router;
