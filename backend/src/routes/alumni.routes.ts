import { Router } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import {
  getAlumni,
  getAlumniById,
  getConnections,
  connectWithAlumni,
  respondConnectionRequest,
  deleteConnection,
  saveAlumni,
  unsaveAlumni,
  getSavedAlumni,
  getNetworkActivity
} from '../controllers/alumni.controller';

const router = Router();

router.use('/alumni', requireAuth);

router.get('/alumni', getAlumni);
router.get('/alumni/saved', getSavedAlumni);
router.get('/alumni/network/connections', getConnections);
router.get('/alumni/network/activity', getNetworkActivity);
router.get('/alumni/:id', getAlumniById);
router.post('/alumni/:id/connect', connectWithAlumni);
router.patch('/alumni/connections/:id', respondConnectionRequest);
router.delete('/alumni/connections/:id', deleteConnection);
router.post('/alumni/:id/save', saveAlumni);
router.delete('/alumni/:id/save', unsaveAlumni);

export default router;
