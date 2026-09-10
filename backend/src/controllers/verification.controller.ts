import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { sendSuccess, sendError } from '../utils/response';

export const submitVerificationSchema = z.object({
  documentType: z.enum(['STUDENT_ID', 'ALUMNI_DEGREE', 'ENROLLMENT_LETTER', 'GOVERNMENT_ID', 'FACULTY_ID']),
  documentUrl: z.string().url('A valid document URL is required')
});

export const reviewVerificationSchema = z.object({
  status: z.enum(['APPROVED', 'REJECTED']),
  rejectionReason: z.string().max(500).optional()
});

export async function getVerifications(req: Request, res: Response): Promise<void> {
  const status = req.query.status as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where = status ? { status: status.toUpperCase() } : {};

  const [verifications, total] = await Promise.all([
    prisma.verification.findMany({
      where,
      skip,
      take: limit,
      orderBy: { submittedAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
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
    }),
    prisma.verification.count({ where })
  ]);

  sendSuccess(
    res,
    verifications,
    'Verifications retrieved',
    200,
    { page, limit, total, totalPages: Math.ceil(total / limit) }
  );
}

export async function submitVerification(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { documentType, documentUrl } = req.body;

  const verification = await prisma.verification.create({
    data: {
      userId,
      documentType,
      documentUrl,
      status: 'PENDING'
    }
  });

  sendSuccess(res, verification, 'Verification document submitted successfully.', 201);
}

export async function reviewVerification(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const reviewerId = req.user!.userId;
  const { status, rejectionReason } = req.body;

  const existing = await prisma.verification.findUnique({
    where: { id },
    include: { user: true }
  });

  if (!existing) {
    sendError(res, 'Verification record not found.', 404, 'NOT_FOUND');
    return;
  }

  const updated = await prisma.verification.update({
    where: { id },
    data: {
      status,
      reviewerId,
      rejectionReason: status === 'REJECTED' ? rejectionReason : null,
      reviewedAt: new Date()
    }
  });

  // If approved, update user's email/institution verification status
  if (status === 'APPROVED') {
    await prisma.user.update({
      where: { id: existing.userId },
      data: { isEmailVerified: true }
    });
  }

  // Notify user of verification decision
  await prisma.notification.create({
    data: {
      userId: existing.userId,
      type: 'SYSTEM',
      title: status === 'APPROVED' ? 'Verification Approved' : 'Verification Rejected',
      message: status === 'APPROVED'
        ? 'Your identity documents have been verified and approved.'
        : `Your verification was rejected: ${rejectionReason || 'Please submit valid documents.'}`
    }
  }).catch(() => {});

  await logAudit(
    reviewerId,
    `ADMIN_${status}_VERIFICATION`,
    'VERIFICATION',
    id,
    { targetUserId: existing.userId, status, rejectionReason }
  );

  sendSuccess(res, updated, `Verification ${status.toLowerCase()} successfully.`);
}
