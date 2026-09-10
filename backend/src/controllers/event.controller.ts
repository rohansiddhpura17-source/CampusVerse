import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export async function getEvents(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;
  const category = req.query.category as string | undefined;
  const status = req.query.status as string | undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where: any = {};

  if (search) {
    where.OR = [
      { title: { contains: search } },
      { description: { contains: search } },
      { location: { contains: search } }
    ];
  }

  if (category && category !== 'ALL') {
    where.category = category.toUpperCase();
  }

  if (status) {
    where.status = status.toUpperCase();
  }

  const [events, total] = await Promise.all([
    prisma.event.findMany({
      where,
      skip,
      take: limit,
      orderBy: { startTime: 'asc' },
      include: {
        organizer: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        },
        institution: true,
        registrations: {
          where: { userId: req.user?.userId || '' },
          select: { id: true }
        },
        _count: {
          select: { registrations: true }
        }
      }
    }),
    prisma.event.count({ where })
  ]);

  // Map isRegistered for the requesting user
  const enrichedEvents = events.map(e => ({
    ...e,
    isRegistered: (e.registrations && e.registrations.length > 0) || false
  }));

  sendSuccess(res, enrichedEvents, 'Events retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getEventById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user?.userId;

  const event = await prisma.event.findUnique({
    where: { id },
    include: {
      organizer: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      },
      institution: true,
      registrations: userId ? {
        where: { userId },
        select: { id: true }
      } : undefined,
      _count: {
        select: { registrations: true }
      }
    }
  });

  if (!event) {
    sendError(res, 'Event not found.', 404, 'NOT_FOUND');
    return;
  }

  const enrichedEvent = {
    ...event,
    isRegistered: ((event as any).registrations && (event as any).registrations.length > 0) || false
  };

  sendSuccess(res, enrichedEvent);
}

export async function registerForEvent(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;

  const event = await prisma.event.findUnique({
    where: { id }
  });

  if (!event || event.status === 'CANCELLED') {
    sendError(res, 'Event is cancelled or does not exist.', 400, 'EVENT_UNAVAILABLE');
    return;
  }

  if (event.registeredCount >= event.capacity) {
    sendError(res, 'Event capacity is full.', 400, 'CAPACITY_REACHED');
    return;
  }

  const existingRegistration = await prisma.eventRegistration.findUnique({
    where: {
      eventId_userId: {
        eventId: id,
        userId
      }
    }
  });

  if (existingRegistration) {
    sendError(res, 'You are already registered for this event.', 409, 'ALREADY_REGISTERED');
    return;
  }

  const [registration] = await prisma.$transaction([
    prisma.eventRegistration.create({
      data: {
        eventId: id,
        userId
      }
    }),
    prisma.event.update({
      where: { id },
      data: { registeredCount: { increment: 1 } }
    })
  ]);

  // Create notification
  await prisma.notification.create({
    data: {
      userId,
      type: 'EVENT',
      title: 'Event Registration Confirmed',
      message: `You are confirmed for ${event.title}.`
    }
  }).catch(() => {});

  sendSuccess(res, registration, 'Registered for event successfully.', 201);
}

export async function unregisterFromEvent(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;

  const existingRegistration = await prisma.eventRegistration.findUnique({
    where: {
      eventId_userId: {
        eventId: id,
        userId
      }
    }
  });

  if (!existingRegistration) {
    sendError(res, 'You are not registered for this event.', 400, 'NOT_REGISTERED');
    return;
  }

  await prisma.$transaction([
    prisma.eventRegistration.delete({
      where: {
        eventId_userId: {
          eventId: id,
          userId
        }
      }
    }),
    prisma.event.update({
      where: { id },
      data: { registeredCount: { decrement: 1 } }
    })
  ]);

  sendSuccess(res, { unregistered: true }, 'Unregistered from event successfully.');
}
