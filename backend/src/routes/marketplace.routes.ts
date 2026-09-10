import { Router } from 'express';
import {
  getMarketplaceItems,
  getMarketplaceItemById,
  createMarketplaceItem,
  updateMarketplaceItem,
  deleteMarketplaceItem,
  createMarketplaceItemSchema,
  updateMarketplaceItemSchema
} from '../controllers/marketplace.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { requireRole } from '../middleware/rbac.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/', requireAuth, getMarketplaceItems);
router.get('/:id', requireAuth, getMarketplaceItemById);
router.post('/', requireAuth, requireRole('STUDENT', 'ALUMNI', 'ADMIN'), validateBody(createMarketplaceItemSchema), createMarketplaceItem);
router.patch('/:id', requireAuth, validateBody(updateMarketplaceItemSchema), updateMarketplaceItem);
router.delete('/:id', requireAuth, deleteMarketplaceItem);

export default router;
