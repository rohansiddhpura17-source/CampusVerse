import { Router } from 'express';
import { getEvents, getEventById, registerForEvent, unregisterFromEvent } from '../controllers/event.controller';
import { requireAuth } from '../middleware/auth.middleware';

const router = Router();

router.get('/', requireAuth, getEvents);
router.get('/:id', requireAuth, getEventById);
router.post('/:id/register', requireAuth, registerForEvent);
router.delete('/:id/register', requireAuth, unregisterFromEvent);

export default router;
