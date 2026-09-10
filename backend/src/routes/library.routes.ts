import { Router } from 'express';
import { getLibraryItems, getLibraryItemById } from '../controllers/library.controller';
import { requireAuth } from '../middleware/auth.middleware';

const router = Router();

router.get('/library', requireAuth, getLibraryItems);
router.get('/library/:id', requireAuth, getLibraryItemById);

export default router;
