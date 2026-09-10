import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export async function getLibraryItems(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const category = req.query.category as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where: any = {};

  if (search) {
    where.OR = [
      { title: { contains: search } },
      { author: { contains: search } },
      { category: { contains: search } }
    ];
  }

  if (category && category !== 'ALL') {
    where.category = category.toUpperCase();
  }

  const [items, total] = await Promise.all([
    prisma.libraryItem.findMany({
      where,
      skip,
      take: limit,
      orderBy: { title: 'asc' },
      include: {
        institution: {
          select: { name: true, code: true }
        }
      }
    }),
    prisma.libraryItem.count({ where })
  ]);

  sendSuccess(res, items, 'Library resources retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getLibraryItemById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const item = await prisma.libraryItem.findUnique({
    where: { id },
    include: {
      institution: true
    }
  });

  if (!item) {
    sendError(res, 'Library item not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, item);
}
