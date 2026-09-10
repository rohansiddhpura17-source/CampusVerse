import { Router } from 'express';
import {
  getNotes,
  getMyNotes,
  getNoteById,
  createNote,
  requestNoteRemoval,
  updateNote,
  deleteNote,
  createNoteSchema,
  requestRemovalSchema,
  updateNoteSchema
} from '../controllers/note.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/notes', requireAuth, getNotes);
router.get('/notes/my', requireAuth, getMyNotes);
router.get('/notes/:id', requireAuth, getNoteById);
router.post('/notes', requireAuth, validateBody(createNoteSchema), createNote);
router.post('/notes/:id/request-removal', requireAuth, validateBody(requestRemovalSchema), requestNoteRemoval);
router.patch('/notes/:id', requireAuth, validateBody(updateNoteSchema), updateNote);
router.delete('/notes/:id', requireAuth, deleteNote);

export default router;

