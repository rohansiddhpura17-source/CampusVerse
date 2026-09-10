import { Router } from 'express';
import { getStudentAcademics, getCourses } from '../controllers/academic.controller';
import { requireAuth } from '../middleware/auth.middleware';

const router = Router();

router.get('/academics/me', requireAuth, getStudentAcademics);
router.get('/courses', requireAuth, getCourses);

export default router;
