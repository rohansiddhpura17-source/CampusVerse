import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';
import { aiRateLimiter } from '../services/rate-limiter.service';
import {
  handleStudyAssistantQuery,
  handleCareerAssistantQuery,
  handleAspirantRecommendationsQuery,
  getChatSessions,
  getChatSessionById,
  createChatSession,
  sendChatMessage,
  deleteChatSession,
  aiStudyQuerySchema,
  aiCareerQuerySchema,
  aiAspirantQuerySchema,
  createChatSessionSchema,
  sendChatMessageSchema
} from '../controllers/ai.controller';

const router = Router();

router.use('/ai', requireAuth, aiRateLimiter);

router.post('/ai/study-assistant', validateBody(aiStudyQuerySchema), handleStudyAssistantQuery);
router.post('/ai/career-assistant', validateBody(aiCareerQuerySchema), handleCareerAssistantQuery);
router.post('/ai/aspirant-recommendations', validateBody(aiAspirantQuerySchema), handleAspirantRecommendationsQuery);

// AI Chat Sessions
router.get('/ai/sessions', getChatSessions);
router.post('/ai/sessions', validateBody(createChatSessionSchema), createChatSession);
router.get('/ai/sessions/:id', getChatSessionById);
router.post('/ai/sessions/:id/messages', validateBody(sendChatMessageSchema), sendChatMessage);
router.delete('/ai/sessions/:id', deleteChatSession);

export default router;
