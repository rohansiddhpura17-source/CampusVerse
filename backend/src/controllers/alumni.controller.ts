import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export async function getAlumni(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const search = req.query.search as string | undefined;
  const company = req.query.company as string | undefined;
  const industry = req.query.industry as string | undefined;
  const graduationYear = req.query.graduationYear ? parseInt(req.query.graduationYear as string, 10) : undefined;
  const willingToMentor = req.query.willingToMentor ? req.query.willingToMentor === 'true' : undefined;
  const willingToRefer = req.query.willingToRefer ? req.query.willingToRefer === 'true' : undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where: any = {
    role: 'ALUMNI',
    isActive: true
  };

  if (search) {
    where.OR = [
      { profile: { fullName: { contains: search } } },
      { profile: { headline: { contains: search } } },
      { profile: { location: { contains: search } } },
      { profile: { alumniProfile: { currentCompany: { contains: search } } } },
      { profile: { alumniProfile: { currentDesignation: { contains: search } } } },
      { profile: { alumniProfile: { industry: { contains: search } } } }
    ];
  }

  const alumniWhere: any = {};
  if (company) alumniWhere.currentCompany = { contains: company };
  if (industry) alumniWhere.industry = { contains: industry };
  if (graduationYear) alumniWhere.graduationYear = graduationYear;
  if (willingToMentor !== undefined) alumniWhere.willingToMentor = willingToMentor;
  if (willingToRefer !== undefined) alumniWhere.willingToRefer = willingToRefer;

  if (Object.keys(alumniWhere).length > 0) {
    where.profile = {
      ...(where.profile || {}),
      alumniProfile: alumniWhere
    };
  }

  const [alumniUsers, total] = await Promise.all([
    prisma.user.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      select: {
        id: true,
        email: true,
        role: true,
        createdAt: true,
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
            headline: true,
            bio: true,
            location: true,
            linkedin: true,
            website: true,
            alumniProfile: {
              include: { institution: true }
            }
          }
        },
        mentorProfile: true,
        skills: true,
        receivedConnections: {
          where: { requesterId: currentUserId }
        },
        sentConnections: {
          where: { receiverId: currentUserId }
        },
        savedByAlumni: {
          where: { userId: currentUserId }
        }
      }
    }),
    prisma.user.count({ where })
  ]);

  const formatted = alumniUsers.map((u) => {
    const connSent = u.receivedConnections[0];
    const connReceived = u.sentConnections[0];
    let connectionStatus = 'NONE';
    let connectionId: string | null = null;

    if (connSent) {
      connectionStatus = connSent.status; // PENDING, ACCEPTED, DECLINED
      connectionId = connSent.id;
    } else if (connReceived) {
      connectionStatus = connReceived.status === 'PENDING' ? 'RECEIVED' : connReceived.status;
      connectionId = connReceived.id;
    }

    return {
      userId: u.id,
      email: u.email,
      fullName: u.profile?.fullName || 'Alumni Member',
      headline: u.profile?.headline,
      bio: u.profile?.bio,
      avatarUrl: u.profile?.avatarUrl,
      location: u.profile?.location,
      linkedin: u.profile?.linkedin,
      website: u.profile?.website,
      company: u.profile?.alumniProfile?.currentCompany || 'Technology Company',
      designation: u.profile?.alumniProfile?.currentDesignation || 'Software Engineer',
      industry: u.profile?.alumniProfile?.industry || 'Technology',
      yearsOfExperience: u.profile?.alumniProfile?.yearsOfExperience || 0,
      graduationYear: u.profile?.alumniProfile?.graduationYear || 2021,
      degree: u.profile?.alumniProfile?.degree || 'B.Tech',
      institution: u.profile?.alumniProfile?.institution?.name || 'National Institute of Technology',
      willingToMentor: u.profile?.alumniProfile?.willingToMentor ?? true,
      willingToRefer: u.profile?.alumniProfile?.willingToRefer ?? true,
      mentorProfile: u.mentorProfile,
      skills: u.skills.map((s) => s.skillName),
      connectionStatus,
      connectionId,
      isSaved: u.savedByAlumni.length > 0,
      isSelf: u.id === currentUserId
    };
  });

  sendSuccess(res, formatted, 'Alumni retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getAlumniById(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const { id } = req.params;

  const u = await prisma.user.findUnique({
    where: { id },
    select: {
      id: true,
      email: true,
      role: true,
      createdAt: true,
      profile: {
        select: {
          fullName: true,
          avatarUrl: true,
          headline: true,
          bio: true,
          location: true,
          phone: true,
          linkedin: true,
          website: true,
          github: true,
          alumniProfile: {
            include: { institution: true }
          }
        }
      },
      mentorProfile: true,
      skills: true,
      receivedConnections: {
        where: { requesterId: currentUserId }
      },
      sentConnections: {
        where: { receiverId: currentUserId }
      },
      savedByAlumni: {
        where: { userId: currentUserId }
      }
    }
  });

  if (!u) {
    sendError(res, 'Alumni profile not found.', 404, 'NOT_FOUND');
    return;
  }

  const connSent = u.receivedConnections[0];
  const connReceived = u.sentConnections[0];
  let connectionStatus = 'NONE';
  let connectionId: string | null = null;

  if (connSent) {
    connectionStatus = connSent.status;
    connectionId = connSent.id;
  } else if (connReceived) {
    connectionStatus = connReceived.status === 'PENDING' ? 'RECEIVED' : connReceived.status;
    connectionId = connReceived.id;
  }

  // Count mutual connections
  const mutualCount = await prisma.userConnection.count({
    where: {
      status: 'ACCEPTED',
      OR: [
        { requesterId: u.id, receiver: { receivedConnections: { some: { requesterId: currentUserId, status: 'ACCEPTED' } } } },
        { receiverId: u.id, requester: { sentConnections: { some: { receiverId: currentUserId, status: 'ACCEPTED' } } } }
      ]
    }
  });

  const formatted = {
    userId: u.id,
    email: u.email,
    fullName: u.profile?.fullName || 'Alumni Member',
    headline: u.profile?.headline,
    bio: u.profile?.bio,
    avatarUrl: u.profile?.avatarUrl,
    location: u.profile?.location,
    phone: u.profile?.phone,
    linkedin: u.profile?.linkedin,
    website: u.profile?.website,
    github: u.profile?.github,
    company: u.profile?.alumniProfile?.currentCompany || 'Technology Company',
    designation: u.profile?.alumniProfile?.currentDesignation || 'Software Engineer',
    industry: u.profile?.alumniProfile?.industry || 'Technology',
    yearsOfExperience: u.profile?.alumniProfile?.yearsOfExperience || 0,
    graduationYear: u.profile?.alumniProfile?.graduationYear || 2021,
    degree: u.profile?.alumniProfile?.degree || 'B.Tech',
    institution: u.profile?.alumniProfile?.institution?.name || 'National Institute of Technology',
    willingToMentor: u.profile?.alumniProfile?.willingToMentor ?? true,
    willingToRefer: u.profile?.alumniProfile?.willingToRefer ?? true,
    mentorProfile: u.mentorProfile,
    skills: u.skills.map((s) => s.skillName),
    connectionStatus,
    connectionId,
    mutualConnectionsCount: mutualCount,
    isSaved: u.savedByAlumni.length > 0,
    isSelf: u.id === currentUserId
  };

  sendSuccess(res, formatted);
}

export async function getConnections(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;

  const [activeConnections, pendingRequests] = await Promise.all([
    prisma.userConnection.findMany({
      where: {
        status: 'ACCEPTED',
        OR: [{ requesterId: currentUserId }, { receiverId: currentUserId }]
      },
      include: {
        requester: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: {
              select: {
                fullName: true,
                avatarUrl: true,
                headline: true,
                alumniProfile: true
              }
            }
          }
        },
        receiver: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: {
              select: {
                fullName: true,
                avatarUrl: true,
                headline: true,
                alumniProfile: true
              }
            }
          }
        }
      },
      orderBy: { updatedAt: 'desc' }
    }),
    prisma.userConnection.findMany({
      where: {
        receiverId: currentUserId,
        status: 'PENDING'
      },
      include: {
        requester: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: {
              select: {
                fullName: true,
                avatarUrl: true,
                headline: true,
                alumniProfile: true
              }
            }
          }
        }
      },
      orderBy: { createdAt: 'desc' }
    })
  ]);

  const connections = activeConnections.map((c) => {
    const peer = c.requesterId === currentUserId ? c.receiver : c.requester;
    return {
      connectionId: c.id,
      userId: peer.id,
      email: peer.email,
      fullName: peer.profile?.fullName || 'Network Connection',
      headline: peer.profile?.headline,
      avatarUrl: peer.profile?.avatarUrl,
      company: peer.profile?.alumniProfile?.currentCompany,
      designation: peer.profile?.alumniProfile?.currentDesignation,
      connectedAt: c.updatedAt
    };
  });

  const requests = pendingRequests.map((r) => ({
    connectionId: r.id,
    userId: r.requester.id,
    email: r.requester.email,
    fullName: r.requester.profile?.fullName || 'Alumni Member',
    headline: r.requester.profile?.headline,
    avatarUrl: r.requester.profile?.avatarUrl,
    company: r.requester.profile?.alumniProfile?.currentCompany,
    designation: r.requester.profile?.alumniProfile?.currentDesignation,
    requestedAt: r.createdAt
  }));

  sendSuccess(res, { connections, pendingRequests: requests }, 'Connections retrieved');
}

export async function connectWithAlumni(req: Request, res: Response): Promise<void> {
  const requesterId = req.user!.userId;
  const { id: receiverId } = req.params;

  if (requesterId === receiverId) {
    sendError(res, 'You cannot connect with yourself.', 400, 'SELF_CONNECT');
    return;
  }

  const receiver = await prisma.user.findUnique({ where: { id: receiverId } });
  if (!receiver) {
    sendError(res, 'User not found.', 404, 'NOT_FOUND');
    return;
  }

  const existing = await prisma.userConnection.findFirst({
    where: {
      OR: [
        { requesterId, receiverId },
        { requesterId: receiverId, receiverId: requesterId }
      ]
    }
  });

  if (existing) {
    if (existing.status === 'ACCEPTED') {
      sendError(res, 'You are already connected with this user.', 409, 'ALREADY_CONNECTED');
      return;
    }
    if (existing.status === 'PENDING') {
      sendError(res, 'A connection request is already pending.', 409, 'REQUEST_PENDING');
      return;
    }
    // If declined previously, reopen
    const updated = await prisma.userConnection.update({
      where: { id: existing.id },
      data: { requesterId, receiverId, status: 'PENDING' }
    });
    sendSuccess(res, updated, 'Connection request sent.', 200);
    return;
  }

  const connection = await prisma.userConnection.create({
    data: {
      requesterId,
      receiverId,
      status: 'PENDING'
    }
  });

  // Notify receiver
  await prisma.notification.create({
    data: {
      userId: receiverId,
      type: 'CONNECTION',
      title: 'New Connection Request',
      message: `${req.user?.email || 'An alumni member'} sent you a connection request.`
    }
  }).catch(() => {});

  sendSuccess(res, connection, 'Connection request sent successfully.', 201);
}

export async function respondConnectionRequest(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const { id } = req.params;
  const { status } = req.body; // ACCEPTED or DECLINED

  if (status !== 'ACCEPTED' && status !== 'DECLINED') {
    sendError(res, 'Invalid connection response status. Must be ACCEPTED or DECLINED.', 400, 'INVALID_STATUS');
    return;
  }

  const connection = await prisma.userConnection.findUnique({
    where: { id }
  });

  if (!connection) {
    sendError(res, 'Connection request not found.', 404, 'NOT_FOUND');
    return;
  }

  if (connection.receiverId !== currentUserId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to respond to this connection request.', 403, 'FORBIDDEN');
    return;
  }

  const updated = await prisma.userConnection.update({
    where: { id },
    data: { status }
  });

  if (status === 'ACCEPTED') {
    await prisma.notification.create({
      data: {
        userId: connection.requesterId,
        type: 'CONNECTION',
        title: 'Connection Accepted',
        message: 'Your connection request was accepted!'
      }
    }).catch(() => {});
  }

  sendSuccess(res, updated, `Connection request ${status.toLowerCase()}.`);
}

export async function deleteConnection(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const { id } = req.params;

  const connection = await prisma.userConnection.findUnique({
    where: { id }
  });

  if (!connection) {
    sendError(res, 'Connection not found.', 404, 'NOT_FOUND');
    return;
  }

  if (connection.requesterId !== currentUserId && connection.receiverId !== currentUserId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to delete this connection.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.userConnection.delete({ where: { id } });
  sendSuccess(res, { deleted: true }, 'Connection removed.');
}

export async function saveAlumni(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id: alumniId } = req.params;

  try {
    const target = await prisma.user.findUnique({ where: { id: alumniId } });
    if (!target) {
      sendError(res, 'Target alumni user not found.', 404, 'NOT_FOUND');
      return;
    }

    const saved = await prisma.savedAlumni.upsert({
      where: {
        userId_alumniId: { userId, alumniId }
      },
      create: { userId, alumniId },
      update: {}
    });

    sendSuccess(res, saved, 'Alumni profile saved.', 201);
  } catch (err: any) {
    sendError(res, err.message || 'Failed to save alumni profile.', 500, 'INTERNAL_ERROR');
  }
}

export async function unsaveAlumni(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id: alumniId } = req.params;

  await prisma.savedAlumni.deleteMany({
    where: { userId, alumniId }
  });

  sendSuccess(res, { unsaved: true }, 'Alumni profile removed from saved.');
}

export async function getSavedAlumni(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const saved = await prisma.savedAlumni.findMany({
    where: { userId },
    include: {
      alumni: {
        select: {
          id: true,
          email: true,
          profile: {
            select: {
              fullName: true,
              avatarUrl: true,
              headline: true,
              location: true,
              alumniProfile: true
            }
          },
          mentorProfile: true
        }
      }
    },
    orderBy: { savedAt: 'desc' }
  });

  const formatted = saved.map((s) => ({
    userId: s.alumni.id,
    email: s.alumni.email,
    fullName: s.alumni.profile?.fullName || 'Alumni Member',
    headline: s.alumni.profile?.headline,
    avatarUrl: s.alumni.profile?.avatarUrl,
    location: s.alumni.profile?.location,
    company: s.alumni.profile?.alumniProfile?.currentCompany,
    designation: s.alumni.profile?.alumniProfile?.currentDesignation,
    savedAt: s.savedAt
  }));

  sendSuccess(res, formatted, 'Saved alumni retrieved');
}

export async function getNetworkActivity(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;

  // Build real activity from recent connections, new jobs, and mentorship milestones
  const [recentConnections, recentJobs, recentMentorships] = await Promise.all([
    prisma.userConnection.findMany({
      where: { status: 'ACCEPTED' },
      take: 5,
      orderBy: { updatedAt: 'desc' },
      include: {
        requester: { select: { profile: { select: { fullName: true, avatarUrl: true } } } },
        receiver: { select: { profile: { select: { fullName: true, avatarUrl: true } } } }
      }
    }),
    prisma.job.findMany({
      take: 5,
      orderBy: { createdAt: 'desc' },
      include: { company: true, poster: { select: { profile: { select: { fullName: true } } } } }
    }),
    prisma.mentorshipRequest.findMany({
      where: { status: 'ACCEPTED' },
      take: 5,
      orderBy: { respondedAt: 'desc' },
      include: {
        mentor: { include: { user: { select: { profile: { select: { fullName: true } } } } } },
        mentee: { select: { profile: { select: { fullName: true } } } }
      }
    })
  ]);

  const activities = [
    ...recentConnections.map((c) => ({
      id: `act_conn_${c.id}`,
      type: 'CONNECTION',
      title: `${c.requester.profile?.fullName || 'Alumni'} and ${c.receiver.profile?.fullName || 'Alumni'} connected`,
      description: 'Expanded their campus professional network',
      timestamp: c.updatedAt
    })),
    ...recentJobs.map((j) => ({
      id: `act_job_${j.id}`,
      type: 'JOB',
      title: `${j.poster.profile?.fullName || 'Alumni'} posted ${j.title}`,
      description: `Opportunity at ${j.company.name} (${j.location})`,
      timestamp: j.createdAt
    })),
    ...recentMentorships.map((m) => ({
      id: `act_mentor_${m.id}`,
      type: 'MENTORSHIP',
      title: `${m.mentor.user.profile?.fullName || 'Mentor'} started mentoring ${m.mentee.profile?.fullName || 'Student'}`,
      description: m.goal,
      timestamp: m.respondedAt || m.requestedAt
    }))
  ].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

  sendSuccess(res, activities, 'Network activity retrieved');
}
