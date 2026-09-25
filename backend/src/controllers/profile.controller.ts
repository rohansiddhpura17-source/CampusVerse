import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const updateProfileSchema = z.object({
  fullName: z.string().min(1).max(100).optional(),
  bio: z.string().max(500).optional(),
  avatarUrl: z.string().url().optional().or(z.literal('')),
  headline: z.string().max(150).optional(),
  phone: z.string().max(25).optional(),
  location: z.string().max(100).optional(),
  website: z.string().url().optional().or(z.literal('')),
  github: z.string().optional(),
  linkedin: z.string().optional()
});

export async function getProfile(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const profile = await prisma.profile.findUnique({
    where: { userId: req.user.userId },
    include: {
      user: {
        select: { id: true, email: true, role: true, isEmailVerified: true, isAdminAuthorized: true }
      },
      studentProfile: true,
      aspirantProfile: true,
      alumniProfile: true,
      adminProfile: true
    }
  });

  if (!profile) {
    sendError(res, 'Profile not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, profile);
}

export async function updateProfile(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const data = req.body;

  const profile = await prisma.profile.upsert({
    where: { userId: req.user.userId },
    create: {
      userId: req.user.userId,
      fullName: data.fullName || 'User',
      bio: data.bio || null,
      avatarUrl: data.avatarUrl || null,
      headline: data.headline || null,
      phone: data.phone || null,
      location: data.location || null,
      website: data.website || null,
      github: data.github || null,
      linkedin: data.linkedin || null
    },
    update: {
      fullName: data.fullName !== undefined ? data.fullName : undefined,
      bio: data.bio !== undefined ? data.bio : undefined,
      avatarUrl: data.avatarUrl !== undefined ? data.avatarUrl : undefined,
      headline: data.headline !== undefined ? data.headline : undefined,
      phone: data.phone !== undefined ? data.phone : undefined,
      location: data.location !== undefined ? data.location : undefined,
      website: data.website !== undefined ? data.website : undefined,
      github: data.github !== undefined ? data.github : undefined,
      linkedin: data.linkedin !== undefined ? data.linkedin : undefined
    },
    include: {
      studentProfile: true,
      aspirantProfile: true,
      alumniProfile: true
    }
  });

  sendSuccess(res, profile, 'Profile updated successfully');
}
