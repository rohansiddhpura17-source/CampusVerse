import { Router } from 'express';
import { createReport, getReportById, createReportSchema } from '../controllers/report.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.post('/', requireAuth, validateBody(createReportSchema), createReport);
router.get('/:id', requireAuth, getReportById);

export default router;
