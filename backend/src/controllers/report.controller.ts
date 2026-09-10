import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const createReportSchema = z.object({
  targetType: z.enum(['USER', 'POST', 'COMMENT', 'MARKETPLACE_ITEM', 'MESSAGE']),
  targetId: z.string().min(1),
  reason: z.string().min(5).max(1000)
});

export async function createReport(req: Request, res: Response): Promise<void> {
  const reporterId = req.user!.userId;
  const { targetType, targetId, reason } = req.body;

  const report = await prisma.report.create({
    data: {
      reporterId,
      targetType,
      targetId,
      reason,
      status: 'PENDING'
    }
  });

  sendSuccess(res, report, 'Safety report submitted. Our team will review it.', 201);
}

export async function getReportById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const report = await prisma.report.findUnique({
    where: { id },
    include: {
      reporter: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true } }
        }
      },
      reviewer: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true } }
        }
      }
    }
  });

  if (!report) {
    sendError(res, 'Report not found.', 404, 'NOT_FOUND');
    return;
  }

  if (report.reporterId !== userId && role !== 'ADMIN') {
    sendError(res, 'You do not have permission to view this report.', 403, 'FORBIDDEN');
    return;
  }

  sendSuccess(res, report);
}
