import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const createProjectSchema = z.object({
  title: z.string().min(1, 'Project title is required').max(150),
  tagline: z.string().max(250).optional(),
  description: z.string().min(1, 'Project description is required'),
  category: z.string().default('WEB_DEV'),
  thumbnailUrl: z.string().url().optional().or(z.literal('')),
  demoUrl: z.string().url().optional().or(z.literal('')),
  githubUrl: z.string().url().optional().or(z.literal('')),
  isPublished: z.boolean().default(true),
  startDate: z.string().datetime().optional(),
  endDate: z.string().datetime().optional(),
  technologies: z.array(z.string()).optional(),
  links: z.array(z.object({
    label: z.string().min(1),
    url: z.string().url()
  })).optional()
});

export const updateProjectSchema = createProjectSchema.partial();

export async function listProjects(req: Request, res: Response): Promise<void> {
  const { search, category, technology, userId, page = '1', limit = '20' } = req.query;
  const pageNum = Math.max(1, parseInt(page as string, 10));
  const limitNum = Math.min(50, Math.max(1, parseInt(limit as string, 10)));
  const skip = (pageNum - 1) * limitNum;

  const whereClause: any = {
    isPublished: true
  };

  if (userId) {
    whereClause.userId = userId as string;
    // If requesting own projects, show unpublic as well if authenticated as that user
    if (req.user && req.user.userId === userId) {
      delete whereClause.isPublished;
    }
  }

  if (category) {
    whereClause.category = category as string;
  }

  if (search) {
    whereClause.OR = [
      { title: { contains: search as string, mode: 'insensitive' } },
      { description: { contains: search as string, mode: 'insensitive' } },
      { tagline: { contains: search as string, mode: 'insensitive' } }
    ];
  }

  if (technology) {
    whereClause.technologies = {
      some: {
        name: { equals: technology as string, mode: 'insensitive' }
      }
    };
  }

  const [projects, total] = await Promise.all([
    prisma.project.findMany({
      where: whereClause,
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: {
              select: { fullName: true, avatarUrl: true, headline: true }
            }
          }
        },
        technologies: true,
        links: true,
        members: true
      },
      orderBy: { createdAt: 'desc' },
      skip,
      take: limitNum
    }),
    prisma.project.count({ where: whereClause })
  ]);

  sendSuccess(res, {
    projects,
    pagination: {
      page: pageNum,
      limit: limitNum,
      total,
      pages: Math.ceil(total / limitNum)
    }
  });
}

export async function getProjectById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const project = await prisma.project.findUnique({
    where: { id },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          role: true,
          profile: {
            select: { fullName: true, avatarUrl: true, headline: true, github: true, linkedin: true }
          }
        }
      },
      technologies: true,
      links: true,
      members: true
    }
  });

  if (!project) {
    sendError(res, 'Project not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, project);
}

export async function createProject(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const { title, tagline, description, category, thumbnailUrl, demoUrl, githubUrl, isPublished, startDate, endDate, technologies, links } = req.body;

  const project = await prisma.project.create({
    data: {
      userId: req.user.userId,
      title,
      tagline,
      description,
      category,
      thumbnailUrl: thumbnailUrl || null,
      demoUrl: demoUrl || null,
      githubUrl: githubUrl || null,
      isPublished: isPublished ?? true,
      startDate: startDate ? new Date(startDate) : null,
      endDate: endDate ? new Date(endDate) : null,
      technologies: technologies && technologies.length > 0 ? {
        create: technologies.map((t: string) => ({ name: t }))
      } : undefined,
      links: links && links.length > 0 ? {
        create: links.map((l: { label: string; url: string }) => ({ label: l.label, url: l.url }))
      } : undefined
    },
    include: {
      technologies: true,
      links: true
    }
  });

  sendSuccess(res, project, 'Project created successfully', 201);
}

export async function updateProject(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const { id } = req.params;
  const project = await prisma.project.findUnique({ where: { id } });

  if (!project) {
    sendError(res, 'Project not found.', 404, 'NOT_FOUND');
    return;
  }

  if (project.userId !== req.user.userId && req.user.role !== 'ADMIN') {
    sendError(res, 'You are not authorized to update this project.', 403, 'FORBIDDEN');
    return;
  }

  const { title, tagline, description, category, thumbnailUrl, demoUrl, githubUrl, isPublished, technologies, links } = req.body;

  const updated = await prisma.$transaction(async (tx) => {
    if (technologies && Array.isArray(technologies)) {
      await tx.projectTechnology.deleteMany({ where: { projectId: id } });
      await tx.projectTechnology.createMany({
        data: technologies.map((name: string) => ({ projectId: id, name }))
      });
    }

    if (links && Array.isArray(links)) {
      await tx.projectLink.deleteMany({ where: { projectId: id } });
      await tx.projectLink.createMany({
        data: links.map((l: { label: string; url: string }) => ({ projectId: id, label: l.label, url: l.url }))
      });
    }

    return tx.project.update({
      where: { id },
      data: {
        title: title !== undefined ? title : undefined,
        tagline: tagline !== undefined ? tagline : undefined,
        description: description !== undefined ? description : undefined,
        category: category !== undefined ? category : undefined,
        thumbnailUrl: thumbnailUrl !== undefined ? thumbnailUrl : undefined,
        demoUrl: demoUrl !== undefined ? demoUrl : undefined,
        githubUrl: githubUrl !== undefined ? githubUrl : undefined,
        isPublished: isPublished !== undefined ? isPublished : undefined
      },
      include: {
        technologies: true,
        links: true
      }
    });
  });

  sendSuccess(res, updated, 'Project updated successfully');
}

export async function deleteProject(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const { id } = req.params;
  const project = await prisma.project.findUnique({ where: { id } });

  if (!project) {
    sendError(res, 'Project not found.', 404, 'NOT_FOUND');
    return;
  }

  if (project.userId !== req.user.userId && req.user.role !== 'ADMIN') {
    sendError(res, 'You are not authorized to delete this project.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.project.delete({ where: { id } });
  sendSuccess(res, { deleted: true }, 'Project deleted successfully');
}
