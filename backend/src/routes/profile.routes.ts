import { Router } from 'express';
import { getProfile, updateProfile, updateProfileSchema } from '../controllers/profile.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/', requireAuth, getProfile);
router.patch('/', requireAuth, validateBody(updateProfileSchema), updateProfile);

export default router;
