import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { sendSuccess, sendError } from '../utils/response';

export const createNoteSchema = z.object({
  title: z.string().min(3).max(150),
  description: z.string().max(2000).optional(),
  fileUrl: z.string().url(),
  courseId: z.string().uuid().optional(),
  tags: z.string().optional(),
  isPublic: z.boolean().default(true)
});

export const requestRemovalSchema = z.object({
  reason: z.string().min(3).max(500)
});

export const updateNoteSchema = createNoteSchema.partial();

export async function getNotes(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const courseId = req.query.courseId as string | undefined;
  const tag = req.query.tag as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  // Only return PUBLISHED public notes in the general catalog
  const where: any = {
    status: 'PUBLISHED',
    isPublic: true
  };

  if (search) {
    where.OR = [
      { title: { contains: search } },
      { description: { contains: search } },
      { tags: { contains: search } }
    ];
  }

  if (courseId) {
    where.courseId = courseId;
  }

  if (tag) {
    where.tags = { contains: tag };
  }

  const [notes, total] = await Promise.all([
    prisma.note.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        },
        course: {
          select: {
            id: true,
            code: true,
            name: true,
            department: true
          }
        }
      }
    }),
    prisma.note.count({ where })
  ]);

  sendSuccess(res, notes, 'Notes retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getMyNotes(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '50', 10);
  const skip = (page - 1) * limit;

  const [notes, total] = await Promise.all([
    prisma.note.findMany({
      where: { userId },
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        },
        course: {
          select: {
            id: true,
            code: true,
            name: true,
            department: true
          }
        }
      }
    }),
    prisma.note.count({ where: { userId } })
  ]);

  sendSuccess(res, notes, 'My notes retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getNoteById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const note = await prisma.note.findUnique({
    where: { id },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      },
      course: true
    }
  });

  if (!note) {
    sendError(res, 'Note not found.', 404, 'NOT_FOUND');
    return;
  }

  // If note is not published, only the creator or an admin can access it
  if (note.status !== 'PUBLISHED' && note.userId !== userId && role !== 'ADMIN') {
    sendError(res, 'Unauthorized: You do not have permission to view unpublished notes.', 403, 'FORBIDDEN');
    return;
  }

  // Increment download/view count only for published notes
  if (note.status === 'PUBLISHED') {
    await prisma.note.update({
      where: { id },
      data: { downloadsCount: { increment: 1 } }
    });
  }

  sendSuccess(res, note);
}

export async function createNote(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const role = req.user!.role;
  const { title, description, fileUrl, courseId, tags, isPublic } = req.body;

  // Admin notes are published immediately; student notes enter PENDING_REVIEW
  const isAdmin = role === 'ADMIN';
  const status = isAdmin ? 'PUBLISHED' : 'PENDING_REVIEW';
  const finalIsPublic = isAdmin ? (isPublic !== undefined ? isPublic : true) : false;

  const note = await prisma.note.create({
    data: {
      userId,
      title,
      description,
      fileUrl,
      courseId,
      tags,
      isPublic: finalIsPublic,
      status,
      reviewedBy: isAdmin ? userId : null,
      reviewedAt: isAdmin ? new Date() : null
    },
    include: {
      course: true,
      user: {
        select: {
          id: true,
          profile: { select: { fullName: true } }
        }
      }
    }
  });

  if (isAdmin) {
    await logAudit(userId, 'ADMIN_CREATED_NOTE', 'NOTE', note.id, {
      title: note.title,
      status: note.status,
      result: 'SUCCESS'
    });
  }

  sendSuccess(res, note, isAdmin ? 'Study note published.' : 'Study note submitted for moderation review.', 201);
}

export async function requestNoteRemoval(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;
  const { reason } = req.body;

  const note = await prisma.note.findUnique({
    where: { id }
  });

  if (!note) {
    sendError(res, 'Note not found.', 404, 'NOT_FOUND');
    return;
  }

  if (note.userId !== userId && role !== 'ADMIN') {
    sendError(res, 'Unauthorized: You can only request removal for your own notes.', 403, 'FORBIDDEN');
    return;
  }

  if (note.status === 'REMOVED') {
    sendError(res, 'Note is already removed.', 400, 'BAD_REQUEST');
    return;
  }

  if (note.status === 'REMOVAL_REQUESTED') {
    sendError(res, 'A removal request is already pending review for this note.', 400, 'BAD_REQUEST');
    return;
  }

  const updatedNote = await prisma.note.update({
    where: { id },
    data: {
      status: 'REMOVAL_REQUESTED',
      removalReason: reason
    },
    include: { course: true }
  });

  sendSuccess(res, updatedNote, 'Note removal request submitted for admin review.');
}

export async function updateNote(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const note = await prisma.note.findUnique({
    where: { id }
  });

  if (!note) {
    sendError(res, 'Note not found.', 404, 'NOT_FOUND');
    return;
  }

  if (note.userId !== userId && role !== 'ADMIN') {
    sendError(res, 'Unauthorized: You can only edit your own notes.', 403, 'FORBIDDEN');
    return;
  }

  if (role !== 'ADMIN') {
    if (note.status === 'REMOVED' || note.status === 'REMOVAL_REQUESTED') {
      sendError(res, `Cannot edit note in '${note.status}' status.`, 400, 'BAD_REQUEST');
      return;
    }
  }

  // If a published note is edited by a student, reset to PENDING_REVIEW for re-moderation
  const dataToUpdate: any = { ...req.body };
  if (role !== 'ADMIN' && note.status === 'PUBLISHED') {
    dataToUpdate.status = 'PENDING_REVIEW';
    dataToUpdate.isPublic = false;
  }

  const updatedNote = await prisma.note.update({
    where: { id },
    data: dataToUpdate,
    include: {
      course: true
    }
  });

  sendSuccess(
    res,
    updatedNote,
    role !== 'ADMIN' && note.status === 'PUBLISHED'
      ? 'Note updated and resubmitted for moderation review.'
      : 'Note updated.'
  );
}

export async function deleteNote(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  // Server-side strict restriction: Students cannot delete notes directly
  if (role !== 'ADMIN') {
    sendError(
      res,
      'Unauthorized: Students cannot delete notes directly. Please request removal via moderation.',
      403,
      'FORBIDDEN'
    );
    return;
  }

  const note = await prisma.note.findUnique({
    where: { id }
  });

  if (!note) {
    sendError(res, 'Note not found.', 404, 'NOT_FOUND');
    return;
  }

  await prisma.note.delete({
    where: { id }
  });

  await logAudit(userId, 'ADMIN_DELETED_NOTE', 'NOTE', id, {
    title: note.title,
    authorId: note.userId,
    result: 'SUCCESS'
  });

  sendSuccess(res, { deleted: true }, 'Note permanently removed.');
}
