import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const createMentorshipRequestSchema = z.object({
  mentorId: z.string().uuid(),
  goal: z.string().min(5).max(200),
  message: z.string().min(10).max(1000)
});

export const respondMentorshipRequestSchema = z.object({
  status: z.enum(['ACCEPTED', 'DECLINED', 'COMPLETED'])
});

export const updateSessionSchema = z.object({
  status: z.enum(['SCHEDULED', 'COMPLETED', 'CANCELLED']).optional(),
  notes: z.string().max(2000).optional(),
  meetingUrl: z.string().url().optional()
});

export async function getMentors(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const expertise = req.query.expertise as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where: any = { isAcceptingMentees: true };

  if (search) {
    where.OR = [
      { title: { contains: search } },
      { company: { contains: search } },
      { bio: { contains: search } },
      { user: { profile: { fullName: { contains: search } } } }
    ];
  }

  if (expertise) {
    where.expertise = { contains: expertise };
  }

  const [mentors, total] = await Promise.all([
    prisma.mentorProfile.findMany({
      where,
      skip,
      take: limit,
      orderBy: { rating: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: {
              select: {
                fullName: true,
                avatarUrl: true,
                headline: true,
                location: true,
                linkedin: true
              }
            }
          }
        }
      }
    }),
    prisma.mentorProfile.count({ where })
  ]);

  sendSuccess(res, mentors, 'Mentors retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getMentorById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const mentor = await prisma.mentorProfile.findUnique({
    where: { id },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          role: true,
          profile: true
        }
      }
    }
  });

  if (!mentor) {
    sendError(res, 'Mentor profile not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, mentor);
}

export async function requestMentorship(req: Request, res: Response): Promise<void> {
  const menteeId = req.user!.userId;
  const { mentorId, goal, message } = req.body;

  const mentor = await prisma.mentorProfile.findUnique({
    where: { id: mentorId }
  });

  if (!mentor || !mentor.isAcceptingMentees) {
    sendError(res, 'This mentor is currently not accepting new mentorship requests.', 400, 'MENTOR_UNAVAILABLE');
    return;
  }

  if (mentor.userId === menteeId) {
    sendError(res, 'You cannot request mentorship from your own profile.', 400, 'SELF_REQUEST');
    return;
  }

  const mentorshipRequest = await prisma.mentorshipRequest.create({
    data: {
      mentorId,
      menteeId,
      goal,
      message,
      status: 'PENDING'
    }
  });

  // Notify mentor
  await prisma.notification.create({
    data: {
      userId: mentor.userId,
      type: 'MENTORSHIP',
      title: 'New Mentorship Request',
      message: `${req.user?.email || 'A student'} requested mentorship for goal: ${goal}`
    }
  }).catch(() => {});

  sendSuccess(res, mentorshipRequest, 'Mentorship request submitted successfully.', 201);
}

export async function respondMentorshipRequest(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const { status } = req.body;

  const request = await prisma.mentorshipRequest.findUnique({
    where: { id },
    include: { mentor: true }
  });

  if (!request) {
    sendError(res, 'Mentorship request not found.', 404, 'NOT_FOUND');
    return;
  }

  if (request.mentor.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to respond to this request.', 403, 'FORBIDDEN');
    return;
  }

  const updated = await prisma.mentorshipRequest.update({
    where: { id },
    data: {
      status,
      respondedAt: new Date()
    }
  });

  // If accepted, automatically schedule a kickoff session and notify mentee
  if (status === 'ACCEPTED') {
    await prisma.mentorshipSession.create({
      data: {
        requestId: id,
        scheduledAt: new Date(Date.now() + 86400000 * 2), // 2 days later
        durationMinutes: 45,
        status: 'SCHEDULED',
        notes: 'Kickoff Mentorship Session'
      }
    });

    await prisma.notification.create({
      data: {
        userId: request.menteeId,
        type: 'MENTORSHIP',
        title: 'Mentorship Request Accepted',
        message: 'Your mentorship request was accepted and a kickoff session was scheduled!'
      }
    }).catch(() => {});
  }

  sendSuccess(res, updated, `Mentorship request ${status.toLowerCase()}.`);
}

export async function getMentorshipSessions(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const role = req.user!.role;

  const where = role === 'ADMIN'
    ? {}
    : {
        request: {
          OR: [
            { menteeId: userId },
            { mentor: { userId } }
          ]
        }
      };

  const sessions = await prisma.mentorshipSession.findMany({
    where,
    orderBy: { scheduledAt: 'asc' },
    include: {
      request: {
        include: {
          mentee: {
            select: { id: true, email: true, profile: { select: { fullName: true } } }
          },
          mentor: {
            include: {
              user: { select: { id: true, email: true, profile: { select: { fullName: true } } } }
            }
          }
        }
      }
    }
  });

  sendSuccess(res, sessions, 'Mentorship sessions retrieved');
}

export async function updateMentorshipSession(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const session = await prisma.mentorshipSession.findUnique({
    where: { id },
    include: {
      request: {
        include: { mentor: true }
      }
    }
  });

  if (!session) {
    sendError(res, 'Session not found.', 404, 'NOT_FOUND');
    return;
  }

  const isParticipant = session.request.menteeId === req.user!.userId || session.request.mentor.userId === req.user!.userId;

  if (!isParticipant && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to modify this session.', 403, 'FORBIDDEN');
    return;
  }

  const updated = await prisma.mentorshipSession.update({
    where: { id },
    data: req.body
  });

  sendSuccess(res, updated, 'Mentorship session updated.');
}
