import { Router } from 'express';
import {
  getConversations,
  getConversationById,
  getMessages,
  sendMessage,
  sendMessageSchema,
  createConversation,
  createConversationSchema
} from '../controllers/message.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/', requireAuth, getConversations);
router.post('/', requireAuth, validateBody(createConversationSchema), createConversation);
router.get('/:id', requireAuth, getConversationById);
router.get('/:id/messages', requireAuth, getMessages);
router.post('/:id/messages', requireAuth, validateBody(sendMessageSchema), sendMessage);

export default router;
