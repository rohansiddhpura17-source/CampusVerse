import { Router } from 'express';
import {
  listProjects,
  getProjectById,
  createProject,
  updateProject,
  deleteProject,
  createProjectSchema,
  updateProjectSchema
} from '../controllers/project.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/', listProjects);
router.get('/:id', getProjectById);
router.post('/', requireAuth, validateBody(createProjectSchema), createProject);
router.patch('/:id', requireAuth, validateBody(updateProjectSchema), updateProject);
router.delete('/:id', requireAuth, deleteProject);

export default router;
