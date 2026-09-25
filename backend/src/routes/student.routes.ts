import { Router } from 'express';
import {
  getStudentMe,
  updateStudentMe,
  getStudentAcademic,
  getStudentProjects,
  updateStudentProfileSchema
} from '../controllers/student.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/me', requireAuth, getStudentMe);
router.patch('/me', requireAuth, validateBody(updateStudentProfileSchema), updateStudentMe);
router.get('/me/academic', requireAuth, getStudentAcademic);
router.get('/me/projects', requireAuth, getStudentProjects);

export default router;
