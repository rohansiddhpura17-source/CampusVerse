import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';
import {
  getAspirantHomeSummary,
  getColleges,
  getCollegeById,
  saveCollege,
  unsaveCollege,
  getSavedColleges,
  compareColleges,
  compareCollegesSchema,
  predictAdmission,
  predictAdmissionSchema,
  getPredictionHistory,
  getScholarships,
  getScholarshipById,
  saveScholarship,
  unsaveScholarship,
  getSavedScholarships,
  getAspirantProfile,
  updateAspirantProfile,
  updateAspirantProfileSchema,
  getAspirantRecommendations,
  createAspirantRecommendation
} from '../controllers/aspirant.controller';

const router = Router();

// 1. Dashboard
router.get('/aspirant/home', requireAuth, getAspirantHomeSummary);

// 2. Colleges & Saved
router.get('/colleges/saved', requireAuth, getSavedColleges);
router.get('/colleges', requireAuth, getColleges);
router.get('/colleges/:id', requireAuth, getCollegeById);
router.post('/colleges/:id/save', requireAuth, saveCollege);
router.delete('/colleges/:id/save', requireAuth, unsaveCollege);
router.post('/colleges/compare', requireAuth, validateBody(compareCollegesSchema), compareColleges);

// 3. Admission Predictor
router.post('/predictions/predict', requireAuth, validateBody(predictAdmissionSchema), predictAdmission);
router.get('/predictions/history', requireAuth, getPredictionHistory);

// 4. Scholarships & Saved
router.get('/scholarships/saved', requireAuth, getSavedScholarships);
router.get('/scholarships', requireAuth, getScholarships);
router.get('/scholarships/:id', requireAuth, getScholarshipById);
router.post('/scholarships/:id/save', requireAuth, saveScholarship);
router.delete('/scholarships/:id/save', requireAuth, unsaveScholarship);

// 5. Aspirant Profile CRUD
router.get('/aspirant/profile', requireAuth, getAspirantProfile);
router.patch('/aspirant/profile', requireAuth, validateBody(updateAspirantProfileSchema), updateAspirantProfile);
router.get('/aspirants/me', requireAuth, getAspirantProfile);
router.get('/aspirants/recommendations', requireAuth, getAspirantRecommendations);
router.post('/aspirants/recommendations', requireAuth, createAspirantRecommendation);

export default router;
