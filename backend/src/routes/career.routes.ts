import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import {
  getRoadmaps,
  createRoadmap,
  updateRoadmap,
  deleteRoadmap,
  getSkills,
  upsertSkill,
  deleteSkill,
  getInterviews,
  getInterviewById,
  createInterview,
  getCareerPreferences,
  updateCareerPreferences
} from '../controllers/career.controller';

const router = Router();

router.use('/career', requireAuth);

// Career Roadmaps
router.get('/career/roadmaps', getRoadmaps);
router.post('/career/roadmaps', createRoadmap);
router.patch('/career/roadmaps/:id', updateRoadmap);
router.delete('/career/roadmaps/:id', deleteRoadmap);

// Skill Development
router.get('/career/skills', getSkills);
router.post('/career/skills', upsertSkill);
router.delete('/career/skills/:id', deleteSkill);

// Mock Interviews
router.get('/career/interviews', getInterviews);
router.get('/career/interviews/:id', getInterviewById);
router.post('/career/interviews', createInterview);

// Career Preferences
router.get('/career/preferences', getCareerPreferences);
router.patch('/career/preferences', updateCareerPreferences);

export default router;
