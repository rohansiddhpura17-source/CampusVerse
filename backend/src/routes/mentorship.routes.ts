import { Router } from 'express';
import {
  getMentors,
  getMentorById,
  requestMentorship,
  respondMentorshipRequest,
  getMentorshipSessions,
  updateMentorshipSession,
  createMentorshipRequestSchema,
  respondMentorshipRequestSchema,
  updateSessionSchema
} from '../controllers/mentorship.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/mentors', requireAuth, getMentors);
router.get('/mentors/:id', requireAuth, getMentorById);

router.post('/mentorship/requests', requireAuth, requireRole('ASPIRANT', 'STUDENT', 'ALUMNI'), validateBody(createMentorshipRequestSchema), requestMentorship);
router.patch('/mentorship/requests/:id', requireAuth, requireRole('ALUMNI', 'ADMIN'), validateBody(respondMentorshipRequestSchema), respondMentorshipRequest);

router.get('/mentorship/sessions', requireAuth, getMentorshipSessions);
router.patch('/mentorship/sessions/:id', requireAuth, validateBody(updateSessionSchema), updateMentorshipSession);

export default router;
