import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';
import { aiRateLimiter } from '../services/rate-limiter.service';
import {
  handleStudyAssistantQuery,
  handleCareerAssistantQuery,
  handleAspirantRecommendationsQuery,
  aiStudyQuerySchema,
  aiCareerQuerySchema,
  aiAspirantQuerySchema
} from '../controllers/ai.controller';

const router = Router();

router.use('/ai', requireAuth, aiRateLimiter);

router.post('/ai/study-assistant', validateBody(aiStudyQuerySchema), handleStudyAssistantQuery);
router.post('/ai/career-assistant', validateBody(aiCareerQuerySchema), handleCareerAssistantQuery);
router.post('/ai/aspirant-recommendations', validateBody(aiAspirantQuerySchema), handleAspirantRecommendationsQuery);

export default router;
