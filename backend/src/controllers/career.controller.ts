import { Request, Response } from 'express';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

// -----------------------------------------------------------------------------
// 1. Career Roadmaps
// -----------------------------------------------------------------------------
export async function getRoadmaps(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const roadmaps = await prisma.careerRoadmap.findMany({
    where: { userId },
    orderBy: { createdAt: 'desc' }
  });

  const formatted = roadmaps.map((r) => {
    let parsedMilestones: any[] = [];
    try {
      parsedMilestones = JSON.parse(r.milestones);
    } catch {
      parsedMilestones = [];
    }
    return {
      id: r.id,
      userId: r.userId,
      title: r.title,
      targetRole: r.targetRole,
      milestones: parsedMilestones,
      progressPercentage: r.progressPercentage,
      createdAt: r.createdAt,
      updatedAt: r.updatedAt
    };
  });

  sendSuccess(res, formatted, 'Career roadmaps retrieved');
}

export async function createRoadmap(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { title, targetRole, milestones } = req.body;

  if (!title || !targetRole) {
    sendError(res, 'Title and target role are required.', 400, 'VALIDATION_ERROR');
    return;
  }

  const milestonesArray = Array.isArray(milestones) ? milestones : [];
  const completedCount = milestonesArray.filter((m: any) => m.completed).length;
  const progressPercentage = milestonesArray.length > 0
    ? (completedCount / milestonesArray.length) * 100
    : 0.0;

  const roadmap = await prisma.careerRoadmap.create({
    data: {
      userId,
      title,
      targetRole,
      milestones: JSON.stringify(milestonesArray),
      progressPercentage
    }
  });

  sendSuccess(res, {
    ...roadmap,
    milestones: milestonesArray
  }, 'Career roadmap created successfully.', 201);
}

export async function updateRoadmap(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id } = req.params;
  const { title, targetRole, milestones, progressPercentage } = req.body;

  const existing = await prisma.careerRoadmap.findUnique({ where: { id } });
  if (!existing) {
    sendError(res, 'Career roadmap not found.', 404, 'NOT_FOUND');
    return;
  }

  if (existing.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to modify this roadmap.', 403, 'FORBIDDEN');
    return;
  }

  let finalMilestonesStr = existing.milestones;
  let calculatedProgress = progressPercentage !== undefined ? progressPercentage : existing.progressPercentage;

  if (milestones !== undefined) {
    const milestonesArray = Array.isArray(milestones) ? milestones : [];
    finalMilestonesStr = JSON.stringify(milestonesArray);
    const completedCount = milestonesArray.filter((m: any) => m.completed).length;
    calculatedProgress = milestonesArray.length > 0 ? (completedCount / milestonesArray.length) * 100 : 0.0;
  }

  const updated = await prisma.careerRoadmap.update({
    where: { id },
    data: {
      title: title !== undefined ? title : existing.title,
      targetRole: targetRole !== undefined ? targetRole : existing.targetRole,
      milestones: finalMilestonesStr,
      progressPercentage: calculatedProgress
    }
  });

  let parsed: any[] = [];
  try {
    parsed = JSON.parse(updated.milestones);
  } catch {
    parsed = [];
  }

  sendSuccess(res, { ...updated, milestones: parsed }, 'Career roadmap updated.');
}

export async function deleteRoadmap(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id } = req.params;

  const existing = await prisma.careerRoadmap.findUnique({ where: { id } });
  if (!existing) {
    sendError(res, 'Career roadmap not found.', 404, 'NOT_FOUND');
    return;
  }

  if (existing.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to delete this roadmap.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.careerRoadmap.delete({ where: { id } });
  sendSuccess(res, { deleted: true }, 'Career roadmap deleted.');
}

// -----------------------------------------------------------------------------
// 2. Skill Development
// -----------------------------------------------------------------------------
export async function getSkills(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const skills = await prisma.skillProgress.findMany({
    where: { userId },
    orderBy: { updatedAt: 'desc' }
  });

  sendSuccess(res, skills, 'Skill development list retrieved');
}

export async function upsertSkill(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { skillName, category, level, assessmentScore } = req.body;

  if (!skillName) {
    sendError(res, 'Skill name is required.', 400, 'VALIDATION_ERROR');
    return;
  }

  const skill = await prisma.skillProgress.upsert({
    where: {
      userId_skillName: { userId, skillName }
    },
    create: {
      userId,
      skillName,
      category: category || 'TECHNICAL',
      level: level || 'INTERMEDIATE',
      verified: true,
      assessmentScore: assessmentScore !== undefined ? assessmentScore : 85.0
    },
    update: {
      category: category !== undefined ? category : undefined,
      level: level !== undefined ? level : undefined,
      assessmentScore: assessmentScore !== undefined ? assessmentScore : undefined
    }
  });

  sendSuccess(res, skill, 'Skill updated successfully.', 200);
}

export async function deleteSkill(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id } = req.params;

  const existing = await prisma.skillProgress.findUnique({ where: { id } });
  if (!existing) {
    sendError(res, 'Skill not found.', 404, 'NOT_FOUND');
    return;
  }

  if (existing.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to delete this skill.', 403, 'FORBIDDEN');
    return;
  }

  await prisma.skillProgress.delete({ where: { id } });
  sendSuccess(res, { deleted: true }, 'Skill removed.');
}

// -----------------------------------------------------------------------------
// 3. Mock Interview Sessions & Results
// -----------------------------------------------------------------------------
export async function getInterviews(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const sessions = await prisma.interviewSession.findMany({
    where: { userId },
    orderBy: { completedAt: 'desc' }
  });

  const formatted = sessions.map((s) => {
    let strengthsList: string[] = [];
    let improvementsList: string[] = [];
    try {
      strengthsList = JSON.parse(s.strengths || '[]');
    } catch {
      strengthsList = s.strengths ? [s.strengths] : [];
    }
    try {
      improvementsList = JSON.parse(s.improvements || '[]');
    } catch {
      improvementsList = s.improvements ? [s.improvements] : [];
    }
    return {
      id: s.id,
      userId: s.userId,
      roleTarget: s.roleTarget,
      topic: s.topic,
      durationMinutes: s.durationMinutes,
      feedbackScore: s.feedbackScore,
      transcript: s.transcript,
      strengths: strengthsList,
      improvements: improvementsList,
      completedAt: s.completedAt
    };
  });

  sendSuccess(res, formatted, 'Interview sessions retrieved');
}

export async function getInterviewById(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { id } = req.params;

  const s = await prisma.interviewSession.findUnique({ where: { id } });
  if (!s) {
    sendError(res, 'Interview session not found.', 404, 'NOT_FOUND');
    return;
  }

  if (s.userId !== userId && req.user!.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to view this interview session.', 403, 'FORBIDDEN');
    return;
  }

  let strengthsList: string[] = [];
  let improvementsList: string[] = [];
  try {
    strengthsList = JSON.parse(s.strengths || '[]');
  } catch {
    strengthsList = s.strengths ? [s.strengths] : [];
  }
  try {
    improvementsList = JSON.parse(s.improvements || '[]');
  } catch {
    improvementsList = s.improvements ? [s.improvements] : [];
  }

  sendSuccess(res, {
    id: s.id,
    userId: s.userId,
    roleTarget: s.roleTarget,
    topic: s.topic,
    durationMinutes: s.durationMinutes,
    feedbackScore: s.feedbackScore,
    transcript: s.transcript,
    strengths: strengthsList,
    improvements: improvementsList,
    completedAt: s.completedAt
  });
}

export async function createInterview(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const {
    roleTarget,
    topic,
    durationMinutes,
    feedbackScore,
    technicalScore,
    behavioralScore,
    systemDesignScore,
    communicationScore,
    transcript,
    strengths,
    improvements
  } = req.body;

  if (!roleTarget || !topic) {
    sendError(res, 'Role target and topic are required.', 400, 'VALIDATION_ERROR');
    return;
  }

  const strengthsArr = Array.isArray(strengths) ? strengths : ['Clear communication', 'Solid technical foundation'];
  const improvementsArr = Array.isArray(improvements) ? improvements : ['Elaborate more on edge-case scenarios'];

  const session = await prisma.interviewSession.create({
    data: {
      userId,
      roleTarget,
      topic,
      durationMinutes: durationMinutes || 30,
      feedbackScore: feedbackScore !== undefined ? feedbackScore : 88.0,
      transcript: transcript || 'Mock interview completed successfully with detailed question-answer evaluation.',
      strengths: JSON.stringify(strengthsArr),
      improvements: JSON.stringify(improvementsArr)
    }
  });

  sendSuccess(res, {
    ...session,
    technicalScore: technicalScore || 90.0,
    behavioralScore: behavioralScore || 85.0,
    systemDesignScore: systemDesignScore || 88.0,
    communicationScore: communicationScore || 89.0,
    strengths: strengthsArr,
    improvements: improvementsArr
  }, 'Mock interview session saved.', 201);
}

// -----------------------------------------------------------------------------
// 4. Career Preferences
// -----------------------------------------------------------------------------
export async function getCareerPreferences(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  let prefs = await prisma.careerPreference.findUnique({ where: { userId } });
  if (!prefs) {
    prefs = await prisma.careerPreference.create({
      data: {
        userId,
        preferredRoles: JSON.stringify(['Senior Software Engineer', 'Tech Lead']),
        preferredLocations: JSON.stringify(['Bengaluru', 'Hyderabad', 'Remote']),
        targetSalary: '₹30,00,000 - ₹50,00,000',
        remotePreference: 'HYBRID',
        industries: JSON.stringify(['Technology', 'Cloud Infrastructure']),
        jobAlerts: true
      }
    });
  }

  let roles: string[] = [];
  let locations: string[] = [];
  let industries: string[] = [];
  try { roles = JSON.parse(prefs.preferredRoles || '[]'); } catch {}
  try { locations = JSON.parse(prefs.preferredLocations || '[]'); } catch {}
  try { industries = JSON.parse(prefs.industries || '[]'); } catch {}

  sendSuccess(res, {
    id: prefs.id,
    userId: prefs.userId,
    preferredRoles: roles,
    preferredLocations: locations,
    targetSalary: prefs.targetSalary,
    remotePreference: prefs.remotePreference,
    industries,
    jobAlerts: prefs.jobAlerts,
    updatedAt: prefs.updatedAt
  }, 'Career preferences retrieved');
}

export async function updateCareerPreferences(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { preferredRoles, preferredLocations, targetSalary, remotePreference, industries, jobAlerts } = req.body;

  const rolesStr = Array.isArray(preferredRoles) ? JSON.stringify(preferredRoles) : undefined;
  const locsStr = Array.isArray(preferredLocations) ? JSON.stringify(preferredLocations) : undefined;
  const indStr = Array.isArray(industries) ? JSON.stringify(industries) : undefined;

  const updated = await prisma.careerPreference.upsert({
    where: { userId },
    create: {
      userId,
      preferredRoles: rolesStr || '[]',
      preferredLocations: locsStr || '[]',
      targetSalary: targetSalary || 'Competitive',
      remotePreference: remotePreference || 'ANY',
      industries: indStr || '[]',
      jobAlerts: jobAlerts !== undefined ? jobAlerts : true
    },
    update: {
      preferredRoles: rolesStr !== undefined ? rolesStr : undefined,
      preferredLocations: locsStr !== undefined ? locsStr : undefined,
      targetSalary: targetSalary !== undefined ? targetSalary : undefined,
      remotePreference: remotePreference !== undefined ? remotePreference : undefined,
      industries: indStr !== undefined ? indStr : undefined,
      jobAlerts: jobAlerts !== undefined ? jobAlerts : undefined
    }
  });

  let roles: string[] = [];
  let locations: string[] = [];
  let inds: string[] = [];
  try { roles = JSON.parse(updated.preferredRoles || '[]'); } catch {}
  try { locations = JSON.parse(updated.preferredLocations || '[]'); } catch {}
  try { inds = JSON.parse(updated.industries || '[]'); } catch {}

  sendSuccess(res, {
    id: updated.id,
    userId: updated.userId,
    preferredRoles: roles,
    preferredLocations: locations,
    targetSalary: updated.targetSalary,
    remotePreference: updated.remotePreference,
    industries: inds,
    jobAlerts: updated.jobAlerts,
    updatedAt: updated.updatedAt
  }, 'Career preferences updated.');
}
