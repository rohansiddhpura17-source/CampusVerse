import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export async function getNotifications(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const unreadOnly = req.query.unread === 'true';

  const where: any = { userId };
  if (unreadOnly) {
    where.isRead = false;
  }

  const notifications = await prisma.notification.findMany({
    where,
    orderBy: { createdAt: 'desc' },
    take: 50
  });

  sendSuccess(res, notifications, 'Notifications retrieved');
}

export async function markNotificationAsRead(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;

  const notification = await prisma.notification.findUnique({
    where: { id }
  });

  if (!notification || notification.userId !== userId) {
    sendError(res, 'Notification not found.', 404, 'NOT_FOUND');
    return;
  }

  const updated = await prisma.notification.update({
    where: { id },
    data: { isRead: true }
  });

  sendSuccess(res, updated, 'Notification marked as read.');
}
