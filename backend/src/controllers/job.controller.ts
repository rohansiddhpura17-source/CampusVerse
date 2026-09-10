import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const createJobSchema = z.object({
  companyId: z.string().uuid(),
  title: z.string().min(3).max(150),
  description: z.string().min(10),
  roleType: z.enum(['FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'CONTRACT']).default('FULL_TIME'),
  location: z.string().min(1).max(100),
  isRemote: z.boolean().default(false),
  salaryRange: z.string().max(100).optional(),
  requirements: z.string().optional()
});

export const applyJobSchema = z.object({
  resumeUrl: z.string().url('A valid resume URL is required'),
  coverLetter: z.string().max(2000).optional()
});

export async function getJobs(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user?.userId;
  const search = req.query.search as string | undefined;
  const roleType = req.query.roleType as string | undefined;
  const companyId = req.query.companyId as string | undefined;
  const isRemote = req.query.isRemote ? req.query.isRemote === 'true' : undefined;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const where: any = { status: 'ACTIVE' };

  if (search) {
    where.OR = [
      { title: { contains: search } },
      { description: { contains: search } },
      { location: { contains: search } },
      { requirements: { contains: search } },
      { company: { name: { contains: search } } }
    ];
  }

  if (companyId) {
    where.companyId = companyId;
  }

  if (roleType) {
    where.roleType = roleType.toUpperCase();
  }

  if (isRemote !== undefined) {
    where.isRemote = isRemote;
  }

  const [jobs, total] = await Promise.all([
    prisma.job.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        company: true,
        poster: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true, avatarUrl: true } }
          }
        },
        savedBy: currentUserId ? { where: { userId: currentUserId } } : false,
        applications: currentUserId ? { where: { applicantId: currentUserId } } : false
      }
    }),
    prisma.job.count({ where })
  ]);

  const formatted = jobs.map((j: any) => ({
    ...j,
    isSaved: Array.isArray(j.savedBy) && j.savedBy.length > 0,
    hasApplied: Array.isArray(j.applications) && j.applications.length > 0,
    applicationStatus: Array.isArray(j.applications) && j.applications.length > 0 ? j.applications[0].status : null
  }));

  sendSuccess(res, formatted, 'Jobs retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

export async function getRecommendedJobs(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;

  const jobs = await prisma.job.findMany({
    where: { status: 'ACTIVE' },
    take: 10,
    orderBy: { createdAt: 'desc' },
    include: {
      company: true,
      poster: {
        select: {
          id: true,
          email: true,
          role: true,
          profile: { select: { fullName: true, avatarUrl: true } }
        }
      },
      savedBy: { where: { userId: currentUserId } },
      applications: { where: { applicantId: currentUserId } }
    }
  });

  const formatted = jobs.map((j: any) => ({
    ...j,
    isSaved: j.savedBy.length > 0,
    hasApplied: j.applications.length > 0,
    applicationStatus: j.applications.length > 0 ? j.applications[0].status : null
  }));

  sendSuccess(res, formatted, 'Recommended jobs retrieved');
}

export async function getJobById(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user?.userId;
  const { id } = req.params;

  const job = await prisma.job.findUnique({
    where: { id },
    include: {
      company: true,
      poster: {
        select: {
          id: true,
          email: true,
          role: true,
          profile: { select: { fullName: true, avatarUrl: true, headline: true } }
        }
      },
      savedBy: currentUserId ? { where: { userId: currentUserId } } : false,
      applications: currentUserId ? { where: { applicantId: currentUserId } } : false,
      _count: {
        select: { applications: true }
      }
    }
  });

  if (!job) {
    sendError(res, 'Job listing not found.', 404, 'NOT_FOUND');
    return;
  }

  const formatted = {
    ...job,
    isSaved: Array.isArray((job as any).savedBy) && (job as any).savedBy.length > 0,
    hasApplied: Array.isArray((job as any).applications) && (job as any).applications.length > 0,
    applicationStatus: Array.isArray((job as any).applications) && (job as any).applications.length > 0 ? (job as any).applications[0].status : null
  };

  sendSuccess(res, formatted);
}

export async function createJob(req: Request, res: Response): Promise<void> {
  const posterId = req.user!.userId;
  const { companyId, title, description, roleType, location, isRemote, salaryRange, requirements } = req.body;

  const company = await prisma.company.findUnique({
    where: { id: companyId }
  });

  if (!company) {
    sendError(res, 'Company not found.', 404, 'NOT_FOUND');
    return;
  }

  const job = await prisma.job.create({
    data: {
      companyId,
      posterId,
      title,
      description,
      roleType: roleType || 'FULL_TIME',
      location,
      isRemote: isRemote ?? false,
      salaryRange,
      requirements,
      status: 'ACTIVE'
    },
    include: { company: true }
  });

  sendSuccess(res, job, 'Job opportunity posted successfully.', 201);
}

export async function applyJob(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const applicantId = req.user!.userId;
  const { resumeUrl, coverLetter } = req.body;

  const job = await prisma.job.findUnique({ where: { id }, include: { company: true } });

  if (!job || job.status !== 'ACTIVE') {
    sendError(res, 'Job listing is no longer active or does not exist.', 400, 'JOB_INACTIVE');
    return;
  }

  const existingApplication = await prisma.jobApplication.findUnique({
    where: {
      jobId_applicantId: {
        jobId: id,
        applicantId
      }
    }
  });

  if (existingApplication) {
    sendError(res, 'You have already submitted an application for this position.', 409, 'ALREADY_APPLIED');
    return;
  }

  const application = await prisma.jobApplication.create({
    data: {
      jobId: id,
      applicantId,
      resumeUrl,
      coverLetter,
      status: 'APPLIED'
    },
    include: {
      job: { include: { company: true } }
    }
  });

  // Create notification
  await prisma.notification.create({
    data: {
      userId: applicantId,
      type: 'APPLICATION',
      title: 'Application Submitted',
      message: `Your application for ${job.title} at ${job.company.name} was received.`
    }
  }).catch(() => {});

  sendSuccess(res, application, 'Application submitted successfully.', 201);
}

export async function withdrawApplication(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const { id } = req.params;

  const app = await prisma.jobApplication.findUnique({ where: { id } });
  if (!app) {
    sendError(res, 'Application not found.', 404, 'NOT_FOUND');
    return;
  }

  if (app.applicantId !== currentUserId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to withdraw this application.', 403, 'FORBIDDEN');
    return;
  }

  const updated = await prisma.jobApplication.update({
    where: { id },
    data: { status: 'WITHDRAWN' }
  });

  sendSuccess(res, updated, 'Application withdrawn successfully.');
}

export async function updateApplicationStatus(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const { status } = req.body;
  const role = req.user!.role;

  const app = await prisma.jobApplication.findUnique({
    where: { id },
    include: { job: true }
  });

  if (!app) {
    sendError(res, 'Application not found.', 404, 'NOT_FOUND');
    return;
  }

  if (app.job.posterId !== req.user!.userId && role !== 'ADMIN') {
    sendError(res, 'You do not have permission to update this application status.', 403, 'FORBIDDEN');
    return;
  }

  const updated = await prisma.jobApplication.update({
    where: { id },
    data: { status }
  });

  // Notify applicant
  await prisma.notification.create({
    data: {
      userId: app.applicantId,
      type: 'APPLICATION',
      title: 'Job Application Status Updated',
      message: `Your application status for ${app.job.title} changed to ${status}.`
    }
  }).catch(() => {});

  sendSuccess(res, updated, `Application status updated to ${status}.`);
}

export async function getApplications(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const role = req.user!.role;

  const where = role === 'ADMIN' ? {} : { applicantId: userId };

  const applications = await prisma.jobApplication.findMany({
    where,
    orderBy: { appliedAt: 'desc' },
    include: {
      job: {
        include: { company: true }
      }
    }
  });

  sendSuccess(res, applications, 'Applications retrieved');
}

export async function getApplicationById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const userId = req.user!.userId;
  const role = req.user!.role;

  const application = await prisma.jobApplication.findUnique({
    where: { id },
    include: {
      job: {
        include: { company: true }
      },
      applicant: {
        select: {
          id: true,
          email: true,
          profile: { select: { fullName: true, phone: true } }
        }
      }
    }
  });

  if (!application) {
    sendError(res, 'Application not found.', 404, 'NOT_FOUND');
    return;
  }

  if (application.applicantId !== userId && role !== 'ADMIN') {
    sendError(res, 'You do not have permission to view this application.', 403, 'FORBIDDEN');
    return;
  }

  sendSuccess(res, application);
}

// -----------------------------------------------------------------------------
// Saved Jobs
// -----------------------------------------------------------------------------
export async function saveJob(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id: jobId } = req.params;

  const saved = await prisma.savedJob.upsert({
    where: {
      userId_jobId: { userId, jobId }
    },
    create: { userId, jobId },
    update: {}
  });

  sendSuccess(res, saved, 'Job bookmarked successfully.', 201);
}

export async function unsaveJob(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id: jobId } = req.params;

  await prisma.savedJob.deleteMany({
    where: { userId, jobId }
  });

  sendSuccess(res, { unsaved: true }, 'Job removed from saved.');
}

export async function getSavedJobs(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const saved = await prisma.savedJob.findMany({
    where: { userId },
    include: {
      job: {
        include: { company: true }
      }
    },
    orderBy: { savedAt: 'desc' }
  });

  const formatted = saved.map((s) => ({
    id: s.id,
    jobId: s.jobId,
    savedAt: s.savedAt,
    job: {
      ...s.job,
      isSaved: true
    }
  }));

  sendSuccess(res, formatted, 'Saved jobs retrieved');
}

// -----------------------------------------------------------------------------
// Companies
// -----------------------------------------------------------------------------
export async function getCompanies(req: Request, res: Response): Promise<void> {
  const search = req.query.search as string | undefined;

  const where: any = {};
  if (search) {
    where.OR = [
      { name: { contains: search } },
      { industry: { contains: search } },
      { description: { contains: search } }
    ];
  }

  const companies = await prisma.company.findMany({
    where,
    orderBy: { name: 'asc' },
    include: {
      _count: {
        select: { jobs: { where: { status: 'ACTIVE' } } }
      }
    }
  });

  sendSuccess(res, companies, 'Companies retrieved');
}

export async function getCompanyById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const company = await prisma.company.findUnique({
    where: { id },
    include: {
      jobs: {
        where: { status: 'ACTIVE' },
        orderBy: { createdAt: 'desc' }
      },
      _count: {
        select: { jobs: true }
      }
    }
  });

  if (!company) {
    sendError(res, 'Company not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, company);
}

export async function getCompanyJobs(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const jobs = await prisma.job.findMany({
    where: { companyId: id, status: 'ACTIVE' },
    orderBy: { createdAt: 'desc' },
    include: { company: true }
  });

  sendSuccess(res, jobs, 'Company open jobs retrieved');
}

// -----------------------------------------------------------------------------
// Referrals
// -----------------------------------------------------------------------------
export async function getReferrals(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const role = req.user!.role;

  const where = role === 'ADMIN'
    ? {}
    : {
        OR: [
          { alumniId: userId },
          { studentId: userId }
        ]
      };

  const referrals = await prisma.referral.findMany({
    where,
    orderBy: { createdAt: 'desc' },
    include: {
      job: { include: { company: true } },
      alumni: { select: { id: true, email: true, profile: { select: { fullName: true, avatarUrl: true } } } },
      student: { select: { id: true, email: true, profile: { select: { fullName: true, avatarUrl: true } } } }
    }
  });

  sendSuccess(res, referrals, 'Referrals retrieved');
}

export async function requestReferral(req: Request, res: Response): Promise<void> {
  const studentId = req.user!.userId;
  const { alumniId, jobId, companyName, notes } = req.body;

  if (!alumniId || !companyName) {
    sendError(res, 'Alumni ID and company name are required.', 400, 'VALIDATION_ERROR');
    return;
  }

  const referral = await prisma.referral.create({
    data: {
      alumniId,
      studentId,
      jobId: jobId || null,
      companyName,
      status: 'REQUESTED',
      notes
    },
    include: {
      alumni: { select: { id: true, email: true, profile: { select: { fullName: true } } } }
    }
  });

  // Notify alumni
  await prisma.notification.create({
    data: {
      userId: alumniId,
      type: 'APPLICATION',
      title: 'New Referral Request',
      message: `${req.user?.email || 'A campus member'} requested a job referral for ${companyName}.`
    }
  }).catch(() => {});

  sendSuccess(res, referral, 'Referral requested successfully.', 201);
}

export async function updateReferralStatus(req: Request, res: Response): Promise<void> {
  const currentUserId = req.user!.userId;
  const { id } = req.params;
  const { status, notes } = req.body;

  const referral = await prisma.referral.findUnique({ where: { id } });
  if (!referral) {
    sendError(res, 'Referral not found.', 404, 'NOT_FOUND');
    return;
  }

  if (referral.alumniId !== currentUserId && req.user!.role !== 'ADMIN') {
    sendError(res, 'Only the alumni providing the referral can update this status.', 403, 'FORBIDDEN');
    return;
  }

  const updated = await prisma.referral.update({
    where: { id },
    data: {
      status: status || referral.status,
      notes: notes !== undefined ? notes : referral.notes
    }
  });

  sendSuccess(res, updated, 'Referral status updated.');
}
