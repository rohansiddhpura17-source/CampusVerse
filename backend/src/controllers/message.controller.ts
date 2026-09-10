import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const sendMessageSchema = z.object({
  content: z.string().min(1).max(3000),
  mediaUrl: z.string().url().optional()
});

export async function getConversations(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const participations = await prisma.conversationParticipant.findMany({
    where: { userId },
    include: {
      conversation: {
        include: {
          participants: {
            include: {
              user: {
                select: {
                  id: true,
                  email: true,
                  role: true,
                  profile: { select: { fullName: true, avatarUrl: true } }
                }
              }
            }
          },
          messages: {
            take: 1,
            orderBy: { createdAt: 'desc' }
          }
        }
      }
    }
  });

  const conversations = participations.map(p => p.conversation);
  sendSuccess(res, conversations, 'Conversations retrieved');
}

export async function getConversationById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;

  const conversation = await prisma.conversation.findUnique({
    where: { id },
    include: {
      participants: {
        include: {
          user: {
            select: {
              id: true,
              email: true,
              role: true,
              profile: { select: { fullName: true, avatarUrl: true } }
            }
          }
        }
      }
    }
  });

  if (!conversation) {
    sendError(res, 'Conversation not found.', 404, 'NOT_FOUND');
    return;
  }

  const isParticipant = conversation.participants.some(p => p.userId === userId);
  if (!isParticipant && req.user!.role !== 'ADMIN') {
    sendError(res, 'You are not a participant in this conversation.', 403, 'FORBIDDEN');
    return;
  }

  sendSuccess(res, conversation);
}

export async function getMessages(req: Request, res: Response): Promise<void> {
  const { id: conversationId } = req.params;
  const userId = req.user!.userId;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '30', 10);
  const skip = (page - 1) * limit;

  const participant = await prisma.conversationParticipant.findUnique({
    where: {
      conversationId_userId: {
        conversationId,
        userId
      }
    }
  });

  if (!participant && req.user!.role !== 'ADMIN') {
    sendError(res, 'Access denied.', 403, 'FORBIDDEN');
    return;
  }

  const [messages, total] = await Promise.all([
    prisma.message.findMany({
      where: { conversationId },
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        sender: {
          select: {
            id: true,
            email: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        }
      }
    }),
    prisma.message.count({ where: { conversationId } })
  ]);

  sendSuccess(res, messages.reverse(), 'Messages retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export const createConversationSchema = z.object({
  recipientId: z.string().uuid()
});

export async function createConversation(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const { recipientId } = req.body;

  if (currentUserId === recipientId) {
    sendError(res, 'You cannot create a conversation with yourself.', 400, 'SELF_CONVERSATION');
    return;
  }

  const recipient = await prisma.user.findUnique({ where: { id: recipientId } });
  if (!recipient) {
    sendError(res, 'Recipient user not found.', 404, 'NOT_FOUND');
    return;
  }

  // Check if a direct conversation already exists between these 2 users
  const existing = await prisma.conversation.findFirst({
    where: {
      isGroup: false,
      AND: [
        { participants: { some: { userId: currentUserId } } },
        { participants: { some: { userId: recipientId } } }
      ]
    },
    include: {
      participants: {
        include: {
          user: {
            select: {
              id: true,
              email: true,
              role: true,
              profile: { select: { fullName: true, avatarUrl: true } }
            }
          }
        }
      }
    }
  });

  if (existing) {
    sendSuccess(res, existing, 'Existing conversation retrieved.', 200);
    return;
  }

  const conversation = await prisma.conversation.create({
    data: {
      isGroup: false,
      participants: {
        create: [
          { userId: currentUserId },
          { userId: recipientId }
        ]
      }
    },
    include: {
      participants: {
        include: {
          user: {
            select: {
              id: true,
              email: true,
              role: true,
              profile: { select: { fullName: true, avatarUrl: true } }
            }
          }
        }
      }
    }
  });

  sendSuccess(res, conversation, 'Conversation created successfully.', 201);
}

export async function sendMessage(req: Request, res: Response): Promise<void> {
  const { id: conversationId } = req.params;
  const senderId = req.user!.userId;
  const { content, mediaUrl } = req.body;

  const participant = await prisma.conversationParticipant.findUnique({
    where: {
      conversationId_userId: {
        conversationId,
        userId: senderId
      }
    }
  });

  if (!participant) {
    sendError(res, 'You cannot send messages to a conversation you are not part of.', 403, 'FORBIDDEN');
    return;
  }

  const message = await prisma.message.create({
    data: {
      conversationId,
      senderId,
      content,
      mediaUrl
    },
    include: {
      sender: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      }
    }
  });

  await prisma.conversation.update({
    where: { id: conversationId },
    data: { updatedAt: new Date() }
  });

  // Notify other participants in the conversation
  const otherParticipants = await prisma.conversationParticipant.findMany({
    where: {
      conversationId,
      userId: { not: senderId }
    }
  });

  for (const other of otherParticipants) {
    await prisma.notification.create({
      data: {
        userId: other.userId,
        type: 'MESSAGE',
        title: 'New Direct Message',
        message: `${message.sender.profile?.fullName || 'Someone'} sent you a message.`
      }
    }).catch(() => {});
  }

  sendSuccess(res, message, 'Message sent.', 201);
}
