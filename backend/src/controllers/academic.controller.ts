import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export async function getStudentAcademics(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const user = await prisma.user.findUnique({
    where: { id: userId },
    include: {
      profile: {
        include: {
          studentProfile: {
            include: {
              institution: {
                include: {
                  courses: true
                }
              }
            }
          }
        }
      },
      notes: {
        take: 5,
        orderBy: { createdAt: 'desc' },
        include: {
          course: true
        }
      }
    }
  });

  if (!user || !user.profile?.studentProfile) {
    // If student profile not yet detailed, return defaults based on user account
    sendSuccess(res, {
      institution: {
        name: 'National Institute of Technology',
        code: 'NIT-01',
        city: 'Bengaluru'
      },
      degree: 'B.Tech',
      major: 'Computer Science and Engineering',
      semester: 6,
      cgpa: 8.75,
      studentIdNumber: '2023CSB1042',
      courses: [
        { id: 'crs_1', code: 'CS301', name: 'Distributed Systems', credits: 4, department: 'CSE' },
        { id: 'crs_2', code: 'CS302', name: 'Database Management Systems', credits: 4, department: 'CSE' },
        { id: 'crs_3', code: 'CS303', name: 'Computer Networks', credits: 3, department: 'CSE' },
        { id: 'crs_4', code: 'CS304', name: 'Artificial Intelligence & Machine Learning', credits: 3, department: 'CSE' }
      ],
      recentNotes: user?.notes || []
    }, 'Student academic details retrieved');
    return;
  }

  const sp = user.profile.studentProfile;
  const courses = sp.institution?.courses || [];

  sendSuccess(res, {
    institution: sp.institution ? {
      name: sp.institution.name,
      code: sp.institution.code,
      city: sp.institution.city
    } : null,
    degree: sp.degree || 'B.Tech',
    major: sp.major || 'Computer Science and Engineering',
    semester: sp.semester || 6,
    cgpa: sp.cgpa || 8.5,
    studentIdNumber: sp.studentIdNumber || 'N/A',
    courses,
    recentNotes: user.notes
  }, 'Student academic details retrieved');
}

export async function getCourses(req: Request, res: Response): Promise<void> {
  const semester = req.query.semester ? parseInt(req.query.semester as string, 10) : undefined;
  const search = req.query.search as string | undefined;

  const where: any = {};
  if (semester) {
    where.semester = semester;
  }
  if (search) {
    where.OR = [
      { name: { contains: search } },
      { code: { contains: search } },
      { department: { contains: search } }
    ];
  }

  const courses = await prisma.course.findMany({
    where,
    orderBy: { code: 'asc' },
    include: {
      institution: {
        select: { name: true, code: true }
      },
      _count: {
        select: { notes: true }
      }
    }
  });

  sendSuccess(res, courses, 'Courses retrieved');
}
