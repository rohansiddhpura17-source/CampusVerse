import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const createPostSchema = z.object({
  title: z.string().min(3).max(150),
  content: z.string().min(5).max(5000),
  mediaUrls: z.array(z.string().url()).optional()
});

export const updatePostSchema = createPostSchema.partial();

export const createCommentSchema = z.object({
  content: z.string().min(1).max(1000),
  parentId: z.string().uuid().optional()
});

export async function getCommunities(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;

  const where: any = {};
  if (search) {
    where.OR = [
      { name: { contains: search } },
      { description: { contains: search } }
    ];
  }

  const communities = await prisma.community.findMany({
    where,
    orderBy: { memberCount: 'desc' },
    include: {
      creator: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      },
      _count: {
        select: { posts: true, members: true }
      }
    }
  });

  sendSuccess(res, communities, 'Communities retrieved');
}

export async function getCommunityById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const community = await prisma.community.findUnique({
    where: { id },
    include: {
      creator: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      },
      posts: {
        take: 30,
        orderBy: { createdAt: 'desc' },
        include: {
          author: {
            select: {
              id: true,
              email: true,
              role: true,
              profile: { select: { fullName: true, avatarUrl: true } }
            }
          },
          comments: {
            take: 20,
            orderBy: { createdAt: 'asc' },
            include: {
              author: {
                select: {
                  id: true,
                  profile: { select: { fullName: true, avatarUrl: true } }
                }
              }
            }
          }
        }
      },
      _count: {
        select: { members: true, posts: true }
      }
    }
  });

  if (!community) {
    sendError(res, 'Community not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, community);
}

export async function joinCommunity(req: Request, res: Response): Promise<void> {
  const { id: communityId } = req.params;
  const userId = req.user!.userId;

  const existingMember = await prisma.communityMember.findUnique({
    where: {
      communityId_userId: {
        communityId,
        userId
      }
    }
  });

  if (existingMember) {
    sendSuccess(res, existingMember, 'Already a member of this community.');
    return;
  }

  const [member] = await prisma.$transaction([
    prisma.communityMember.create({
      data: {
        communityId,
        userId,
        role: 'MEMBER'
      }
    }),
    prisma.community.update({
      where: { id: communityId },
      data: { memberCount: { increment: 1 } }
    })
  ]);

  sendSuccess(res, member, 'Joined community successfully.', 201);
}

export async function leaveCommunity(req: Request, res: Response): Promise<void> {
  const { id: communityId } = req.params;
  const userId = req.user!.userId;

  const existingMember = await prisma.communityMember.findUnique({
    where: {
      communityId_userId: {
        communityId,
        userId
      }
    }
  });

  if (!existingMember) {
    sendError(res, 'You are not a member of this community.', 400, 'NOT_A_MEMBER');
    return;
  }

  await prisma.$transaction([
    prisma.communityMember.delete({
      where: {
        communityId_userId: {
          communityId,
          userId
        }
      }
    }),
    prisma.community.update({
      where: { id: communityId },
      data: { memberCount: { decrement: 1 } }
    })
  ]);

  sendSuccess(res, { left: true }, 'Left community successfully.');
}

export async function createCommunityPost(req: Request, res: Response): Promise<void> {
  const { id: communityId } = req.params;
  const authorId = req.user!.userId;
  const { title, content, mediaUrls } = req.body;

  const community = await prisma.community.findUnique({
    where: { id: communityId }
  });

  if (!community) {
    sendError(res, 'Community not found.', 404, 'NOT_FOUND');
    return;
  }

  const post = await prisma.communityPost.create({
    data: {
      communityId,
      authorId,
      title,
      content,
      mediaUrls: mediaUrls ? JSON.stringify(mediaUrls) : null
    },
    include: {
      author: {
        select: {
          id: true,
          email: true,
          role: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      }
    }
  });

  sendSuccess(res, post, 'Post created successfully.', 201);
}

export async function updateCommunityPost(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const post = await prisma.communityPost.findUnique({
    where: { id }
  });

  if (!post) {
    sendError(res, 'Post not found.', 404, 'NOT_FOUND');
    return;
  }

  if (post.authorId !== userId && role !== 'ADMIN') {
    sendError(res, 'Unauthorized: You can only edit your own posts.', 403, 'FORBIDDEN');
    return;
  }

  const data: any = { ...req.body };
  if (data.mediaUrls) {
    data.mediaUrls = JSON.stringify(data.mediaUrls);
  }

  const updated = await prisma.communityPost.update({
    where: { id },
    data
  });

  sendSuccess(res, updated, 'Post updated successfully.');
}

export async function deleteCommunityPost(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const post = await prisma.communityPost.findUnique({
    where: { id }
  });

  if (!post) {
    sendError(res, 'Post not found.', 404, 'NOT_FOUND');
    return;
  }

  if (post.authorId !== userId && role !== 'ADMIN') {
    sendError(res, 'Unauthorized: You can only delete your own posts.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.communityPost.delete({
    where: { id }
  });

  sendSuccess(res, { deleted: true }, 'Post removed.');
}

export async function likeCommunityPost(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const post = await prisma.communityPost.findUnique({
    where: { id }
  });

  if (!post) {
    sendError(res, 'Post not found.', 404, 'NOT_FOUND');
    return;
  }

  const updated = await prisma.communityPost.update({
    where: { id },
    data: { likesCount: { increment: 1 } }
  });

  sendSuccess(res, { likesCount: updated.likesCount }, 'Post liked.');
}

export async function createComment(req: Request, res: Response): Promise<void> {
  const { id: postId } = req.params;
  const authorId = req.user!.userId;
  const { content, parentId } = req.body;

  const post = await prisma.communityPost.findUnique({
    where: { id: postId }
  });

  if (!post) {
    sendError(res, 'Post not found.', 404, 'NOT_FOUND');
    return;
  }

  const [comment] = await prisma.$transaction([
    prisma.communityComment.create({
      data: {
        postId,
        authorId,
        content,
        parentId
      },
      include: {
        author: {
          select: {
            id: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        }
      }
    }),
    prisma.communityPost.update({
      where: { id: postId },
      data: { commentsCount: { increment: 1 } }
    })
  ]);

  sendSuccess(res, comment, 'Comment added successfully.', 201);
}
