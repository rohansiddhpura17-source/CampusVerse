import { Router } from 'express';
import { getNotifications, markNotificationAsRead } from '../controllers/notification.controller';
import { requireAuth } from '../middleware/auth.middleware';

const router = Router();

router.get('/', requireAuth, getNotifications);
router.patch('/:id/read', requireAuth, markNotificationAsRead);

export default router;
