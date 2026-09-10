import { Router } from 'express';
import {
  getCommunities,
  getCommunityById,
  joinCommunity,
  leaveCommunity,
  createCommunityPost,
  updateCommunityPost,
  deleteCommunityPost,
  likeCommunityPost,
  createComment,
  createPostSchema,
  updatePostSchema,
  createCommentSchema
} from '../controllers/community.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

router.get('/communities', requireAuth, getCommunities);
router.get('/communities/:id', requireAuth, getCommunityById);
router.post('/communities/:id/join', requireAuth, joinCommunity);
router.post('/communities/:id/leave', requireAuth, leaveCommunity);
router.post('/communities/:id/posts', requireAuth, validateBody(createPostSchema), createCommunityPost);
router.patch('/posts/:id', requireAuth, validateBody(updatePostSchema), updateCommunityPost);
router.delete('/posts/:id', requireAuth, deleteCommunityPost);
router.post('/posts/:id/like', requireAuth, likeCommunityPost);
router.post('/posts/:id/comments', requireAuth, validateBody(createCommentSchema), createComment);

export default router;
