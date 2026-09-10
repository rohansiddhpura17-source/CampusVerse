import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { sendSuccess, sendError } from '../utils/response';

export const createMarketplaceItemSchema = z.object({
  title: z.string().min(3).max(120),
  description: z.string().min(10).max(2000),
  price: z.number().min(0),
  category: z.enum(['TEXTBOOK', 'ELECTRONICS', 'NOTES', 'FURNITURE', 'OTHER']),
  condition: z.enum(['NEW', 'LIKE_NEW', 'GOOD', 'FAIR']),
  images: z.array(z.string().url()).optional()
});

export const updateMarketplaceItemSchema = createMarketplaceItemSchema.partial().extend({
  status: z.enum(['AVAILABLE', 'RESERVED', 'SOLD']).optional()
});

export async function getMarketplaceItems(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const category = req.query.category as string | undefined;
  const condition = req.query.condition as string | undefined;
  const minPrice = req.query.minPrice ? parseFloat(req.query.minPrice as string) : undefined;
  const maxPrice = req.query.maxPrice ? parseFloat(req.query.maxPrice as string) : undefined;
  const status = (req.query.status as string || 'AVAILABLE').toUpperCase();
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where: any = { status };

  if (search) {
    where.OR = [
      { title: { contains: search } },
      { description: { contains: search } }
    ];
  }

  if (category) {
    where.category = category.toUpperCase();
  }

  if (condition) {
    where.condition = condition.toUpperCase();
  }

  if (minPrice !== undefined || maxPrice !== undefined) {
    where.price = {};
    if (minPrice !== undefined) where.price.gte = minPrice;
    if (maxPrice !== undefined) where.price.lte = maxPrice;
  }

  const [items, total] = await Promise.all([
    prisma.marketplaceItem.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        seller: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, phone: true, avatarUrl: true } }
          }
        }
      }
    }),
    prisma.marketplaceItem.count({ where })
  ]);

  sendSuccess(res, items, 'Marketplace items retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getMarketplaceItemById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const item = await prisma.marketplaceItem.findUnique({
    where: { id },
    include: {
      seller: {
        select: {
          id: true,
          email: true,
          role: true,
          profile: { select: { fullName: true, phone: true, avatarUrl: true, location: true } }
        }
      }
    }
  });

  if (!item) {
    sendError(res, 'Marketplace item not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, item);
}

export async function createMarketplaceItem(req: Request, res: Response): Promise<void> {
  const sellerId = req.user!.userId;
  const { title, description, price, category, condition, images } = req.body;

  const item = await prisma.marketplaceItem.create({
    data: {
      sellerId,
      title,
      description,
      price,
      category,
      condition,
      images: images ? JSON.stringify(images) : null,
      status: 'AVAILABLE'
    }
  });

  sendSuccess(res, item, 'Item listed on campus marketplace.', 201);
}

export async function updateMarketplaceItem(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const item = await prisma.marketplaceItem.findUnique({
    where: { id }
  });

  if (!item) {
    sendError(res, 'Item not found.', 404, 'NOT_FOUND');
    return;
  }

  if (item.sellerId !== userId && role !== 'ADMIN') {
    sendError(res, 'You do not have permission to modify this listing.', 403, 'FORBIDDEN');
    return;
  }

  const data: any = { ...req.body };
  if (data.images) {
    data.images = JSON.stringify(data.images);
  }

  const updated = await prisma.marketplaceItem.update({
    where: { id },
    data
  });

  sendSuccess(res, updated, 'Marketplace listing updated.');
}

export async function deleteMarketplaceItem(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const item = await prisma.marketplaceItem.findUnique({
    where: { id }
  });

  if (!item) {
    sendError(res, 'Item not found.', 404, 'NOT_FOUND');
    return;
  }

  if (item.sellerId !== userId && role !== 'ADMIN') {
    sendError(res, 'You do not have permission to delete this listing.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.marketplaceItem.delete({
    where: { id }
  });

  if (role === 'ADMIN' && item.sellerId !== userId) {
    await logAudit(userId, 'ADMIN_REMOVED_LISTING', 'MARKETPLACE_ITEM', id, { title: item.title });
  }

  sendSuccess(res, { deleted: true }, 'Marketplace listing removed.');
}
