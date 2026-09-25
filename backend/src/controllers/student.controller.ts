import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const updateStudentProfileSchema = z.object({
  studentIdNumber: z.string().optional(),
  degree: z.string().optional(),
  major: z.string().optional(),
  semester: z.number().int().min(1).max(12).optional(),
  cgpa: z.number().min(0.0).max(10.0).optional(),
  graduationYear: z.number().int().min(1950).max(2040).optional(),
  institutionId: z.string().uuid().optional().nullable()
});

export async function getStudentMe(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const profile = await prisma.profile.findUnique({
    where: { userId: req.user.userId },
    include: {
      studentProfile: {
        include: {
          institution: true
        }
      }
    }
  });

  const [studentSkills, enrollmentsCount] = await Promise.all([
    prisma.studentSkill.findMany({
      where: { studentId: req.user.userId },
      include: { skill: true }
    }),
    prisma.enrollment.count({
      where: { studentId: req.user.userId }
    })
  ]);

  sendSuccess(res, {
    profile: profile?.studentProfile,
    generalProfile: profile,
    skills: studentSkills,
    enrollmentsCount
  });
}

export async function updateStudentMe(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const userProfile = await prisma.profile.findUnique({
    where: { userId: req.user.userId }
  });

  if (!userProfile) {
    sendError(res, 'Profile not found. Please create profile first.', 404, 'NOT_FOUND');
    return;
  }

  const data = req.body;

  const studentProfile = await prisma.studentProfile.upsert({
    where: { profileId: userProfile.id },
    create: {
      profileId: userProfile.id,
      institutionId: data.institutionId || null,
      studentIdNumber: data.studentIdNumber || null,
      degree: data.degree || null,
      major: data.major || null,
      semester: data.semester !== undefined ? data.semester : null,
      cgpa: data.cgpa !== undefined ? data.cgpa : null,
      graduationYear: data.graduationYear !== undefined ? data.graduationYear : null
    },
    update: {
      institutionId: data.institutionId !== undefined ? data.institutionId : undefined,
      studentIdNumber: data.studentIdNumber !== undefined ? data.studentIdNumber : undefined,
      degree: data.degree !== undefined ? data.degree : undefined,
      major: data.major !== undefined ? data.major : undefined,
      semester: data.semester !== undefined ? data.semester : undefined,
      cgpa: data.cgpa !== undefined ? data.cgpa : undefined,
      graduationYear: data.graduationYear !== undefined ? data.graduationYear : undefined
    },
    include: {
      institution: true
    }
  });

  sendSuccess(res, studentProfile, 'Student profile updated successfully');
}

export async function getStudentAcademic(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const [academicRecords, enrollments] = await Promise.all([
    prisma.academicRecord.findMany({
      where: { studentId: req.user.userId },
      orderBy: { semesterNumber: 'asc' }
    }),
    prisma.enrollment.findMany({
      where: { studentId: req.user.userId },
      include: {
        subject: {
          include: { department: true }
        },
        semester: true,
        grades: true
      },
      orderBy: { createdAt: 'desc' }
    })
  ]);

  sendSuccess(res, {
    academicRecords,
    enrollments
  });
}

export async function getStudentProjects(req: Request, res: Response): Promise<void> {
  if (!req.user) {
    sendError(res, 'Unauthenticated.', 401, 'UNAUTHORIZED');
    return;
  }

  const projects = await prisma.project.findMany({
    where: { userId: req.user.userId },
    include: {
      technologies: true,
      links: true,
      members: true
    },
    orderBy: { createdAt: 'desc' }
  });

  sendSuccess(res, projects);
}
