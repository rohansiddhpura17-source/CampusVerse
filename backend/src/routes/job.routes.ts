import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';
import {
  getJobs,
  getRecommendedJobs,
  getJobById,
  createJob,
  applyJob,
  withdrawApplication,
  updateApplicationStatus,
  getApplications,
  getApplicationById,
  saveJob,
  unsaveJob,
  getSavedJobs,
  getCompanies,
  getCompanyById,
  getCompanyJobs,
  getReferrals,
  requestReferral,
  updateReferralStatus,
  createJobSchema,
  applyJobSchema
} from '../controllers/job.controller';

const router = Router();

router.use(['/jobs', '/applications', '/companies', '/referrals'], requireAuth);

// Jobs
router.get('/jobs/recommended', getRecommendedJobs);
router.get('/jobs/saved', getSavedJobs);
router.get('/jobs', getJobs);
router.get('/jobs/:id', getJobById);
router.post('/jobs', validateBody(createJobSchema), createJob);
router.post('/jobs/:id/save', saveJob);
router.delete('/jobs/:id/save', unsaveJob);
router.post('/jobs/:id/apply', validateBody(applyJobSchema), applyJob);

// Applications
router.get('/applications', getApplications);
router.get('/applications/:id', getApplicationById);
router.patch('/applications/:id/status', updateApplicationStatus);
router.patch('/applications/:id/withdraw', withdrawApplication);

// Companies
router.get('/companies', getCompanies);
router.get('/companies/:id', getCompanyById);
router.get('/companies/:id/jobs', getCompanyJobs);

// Referrals
router.get('/referrals', getReferrals);
router.post('/referrals', requestReferral);
router.patch('/referrals/:id/status', updateReferralStatus);

export default router;
