import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

export const updateUserSchema = z.object({
  fullName: z.string().min(1).max(100).optional(),
  headline: z.string().max(200).optional(),
  bio: z.string().max(1000).optional(),
  avatarUrl: z.string().url().optional().or(z.literal('')),
  location: z.string().max(100).optional(),
  phone: z.string().max(20).optional(),
  website: z.string().url().optional().or(z.literal('')),
  github: z.string().max(100).optional().or(z.literal('')),
  linkedin: z.string().max(100).optional().or(z.literal(''))
});

export const updateStudentProfileSchema = z.object({
  fullName: z.string().min(1).max(100).optional(),
  bio: z.string().max(1000).optional(),
  avatarUrl: z.string().url().optional().or(z.literal('')),
  location: z.string().max(100).optional(),
  phone: z.string().max(20).optional(),
  university: z.string().max(150).optional(),
  degree: z.string().max(100).optional(),
  branch: z.string().max(100).optional(),
  semester: z.number().int().min(1).max(12).optional(),
  cgpa: z.number().min(0.0).max(10.0).optional(),
  graduationYear: z.number().int().min(2020).max(2040).optional(),
  skills: z.array(z.string()).optional()
});

export const updateAlumniProfileSchema = z.object({
  fullName: z.string().min(1).max(100).optional(),
  headline: z.string().max(200).optional(),
  bio: z.string().max(1000).optional(),
  avatarUrl: z.string().url().optional().or(z.literal('')),
  location: z.string().max(100).optional(),
  phone: z.string().max(20).optional(),
  linkedin: z.string().max(150).optional().or(z.literal('')),
  website: z.string().max(150).optional().or(z.literal('')),
  github: z.string().max(150).optional().or(z.literal('')),
  company: z.string().max(150).optional(),
  designation: z.string().max(150).optional(),
  industry: z.string().max(100).optional(),
  yearsOfExperience: z.number().int().min(0).max(50).optional(),
  degree: z.string().max(100).optional(),
  graduationYear: z.number().int().min(1970).max(2030).optional(),
  willingToMentor: z.boolean().optional(),
  willingToRefer: z.boolean().optional(),
  skills: z.array(z.string()).optional()
});

export async function getUserById(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  const user = await prisma.user.findUnique({
    where: { id },
    select: {
      id: true,
      email: true,
      role: true,
      isEmailVerified: true,
      createdAt: true,
      profile: {
        select: {
          id: true,
          fullName: true,
          bio: true,
          avatarUrl: true,
          headline: true,
          location: true,
          phone: true,
          website: true,
          github: true,
          linkedin: true,
          studentProfile: {
            include: { institution: true }
          },
          alumniProfile: {
            include: { institution: true }
          },
          aspirantProfile: true
        }
      },
      mentorProfile: true,
      skills: true
    }
  });

  if (!user) {
    sendError(res, 'User not found.', 404, 'NOT_FOUND');
    return;
  }

  sendSuccess(res, user);
}

export async function updateUser(req: Request, res: Response): Promise<void> {
  const { id } = req.params;

  if (req.user?.userId !== id && req.user?.role !== 'ADMIN') {
    sendError(res, 'You do not have permission to update this user.', 403, 'FORBIDDEN');
    return;
  }

  const existingProfile = await prisma.profile.findUnique({
    where: { userId: id }
  });

  if (!existingProfile) {
    sendError(res, 'Profile not found.', 404, 'NOT_FOUND');
    return;
  }

  const updatedProfile = await prisma.profile.update({
    where: { userId: id },
    data: req.body
  });

  sendSuccess(res, updatedProfile, 'Profile updated successfully.');
}

export async function updateStudentProfile(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const {
    fullName,
    bio,
    avatarUrl,
    location,
    phone,
    university,
    degree,
    branch,
    semester,
    cgpa,
    graduationYear,
    skills
  } = req.body;

  let profile = await prisma.profile.findUnique({
    where: { userId },
    include: { studentProfile: true }
  });

  if (!profile) {
    profile = await prisma.profile.create({
      data: {
        userId,
        fullName: fullName || 'Student User',
        bio: bio || null,
        avatarUrl: avatarUrl || null,
        location: location || null,
        phone: phone || null
      },
      include: { studentProfile: true }
    });
  } else {
    profile = await prisma.profile.update({
      where: { userId },
      data: {
        fullName: fullName !== undefined ? fullName : profile.fullName,
        bio: bio !== undefined ? bio : profile.bio,
        avatarUrl: avatarUrl !== undefined ? avatarUrl : profile.avatarUrl,
        location: location !== undefined ? location : profile.location,
        phone: phone !== undefined ? phone : profile.phone
      },
      include: { studentProfile: true }
    });
  }

  if (profile.studentProfile) {
    await prisma.studentProfile.update({
      where: { profileId: profile.id },
      data: {
        degree: degree !== undefined ? degree : profile.studentProfile.degree,
        major: branch !== undefined ? branch : profile.studentProfile.major,
        semester: semester !== undefined ? semester : profile.studentProfile.semester,
        cgpa: cgpa !== undefined ? cgpa : profile.studentProfile.cgpa,
        graduationYear: graduationYear !== undefined ? graduationYear : profile.studentProfile.graduationYear
      }
    });
  } else {
    await prisma.studentProfile.create({
      data: {
        profileId: profile.id,
        degree: degree || 'B.Tech',
        major: branch || 'Computer Science and Engineering',
        semester: semester || 6,
        cgpa: cgpa || 8.5,
        graduationYear: graduationYear || 2026
      }
    });
  }

  if (skills && Array.isArray(skills)) {
    for (const skill of skills) {
      await prisma.skillProgress.upsert({
        where: {
          userId_skillName: {
            userId,
            skillName: skill
          }
        },
        create: {
          userId,
          skillName: skill,
          category: 'TECHNICAL',
          level: 'INTERMEDIATE',
          verified: true
        },
        update: {}
      }).catch(() => {});
    }
  }

  const completeUser = await prisma.user.findUnique({
    where: { id: userId },
    select: {
      id: true,
      email: true,
      role: true,
      profile: {
        include: {
          studentProfile: {
            include: { institution: true }
          }
        }
      },
      skills: true
    }
  });

  sendSuccess(res, completeUser, 'Student profile updated successfully.');
}

export async function updateAlumniProfile(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const {
    fullName,
    headline,
    bio,
    avatarUrl,
    location,
    phone,
    linkedin,
    website,
    github,
    company,
    designation,
    industry,
    yearsOfExperience,
    degree,
    graduationYear,
    willingToMentor,
    willingToRefer,
    skills
  } = req.body;

  let profile = await prisma.profile.findUnique({
    where: { userId },
    include: { alumniProfile: true }
  });

  if (!profile) {
    profile = await prisma.profile.create({
      data: {
        userId,
        fullName: fullName || 'Alumni Member',
        headline: headline || null,
        bio: bio || null,
        avatarUrl: avatarUrl || null,
        location: location || null,
        phone: phone || null,
        linkedin: linkedin || null,
        website: website || null,
        github: github || null
      },
      include: { alumniProfile: true }
    });
  } else {
    profile = await prisma.profile.update({
      where: { userId },
      data: {
        fullName: fullName !== undefined ? fullName : profile.fullName,
        headline: headline !== undefined ? headline : profile.headline,
        bio: bio !== undefined ? bio : profile.bio,
        avatarUrl: avatarUrl !== undefined ? avatarUrl : profile.avatarUrl,
        location: location !== undefined ? location : profile.location,
        phone: phone !== undefined ? phone : profile.phone,
        linkedin: linkedin !== undefined ? linkedin : profile.linkedin,
        website: website !== undefined ? website : profile.website,
        github: github !== undefined ? github : profile.github
      },
      include: { alumniProfile: true }
    });
  }

  if (profile.alumniProfile) {
    await prisma.alumniProfile.update({
      where: { profileId: profile.id },
      data: {
        currentCompany: company !== undefined ? company : profile.alumniProfile.currentCompany,
        currentDesignation: designation !== undefined ? designation : profile.alumniProfile.currentDesignation,
        industry: industry !== undefined ? industry : profile.alumniProfile.industry,
        yearsOfExperience: yearsOfExperience !== undefined ? yearsOfExperience : profile.alumniProfile.yearsOfExperience,
        degree: degree !== undefined ? degree : profile.alumniProfile.degree,
        graduationYear: graduationYear !== undefined ? graduationYear : profile.alumniProfile.graduationYear,
        willingToMentor: willingToMentor !== undefined ? willingToMentor : profile.alumniProfile.willingToMentor,
        willingToRefer: willingToRefer !== undefined ? willingToRefer : profile.alumniProfile.willingToRefer
      }
    });
  } else {
    await prisma.alumniProfile.create({
      data: {
        profileId: profile.id,
        currentCompany: company || 'Technology Company',
        currentDesignation: designation || 'Software Engineer',
        industry: industry || 'Technology',
        yearsOfExperience: yearsOfExperience || 3,
        degree: degree || 'B.Tech CS',
        graduationYear: graduationYear || 2021,
        willingToMentor: willingToMentor ?? true,
        willingToRefer: willingToRefer ?? true
      }
    });
  }

  if (skills && Array.isArray(skills)) {
    for (const skill of skills) {
      await prisma.skillProgress.upsert({
        where: {
          userId_skillName: {
            userId,
            skillName: skill
          }
        },
        create: {
          userId,
          skillName: skill,
          category: 'TECHNICAL',
          level: 'ADVANCED',
          verified: true
        },
        update: {}
      }).catch(() => {});
    }
  }

  const completeUser = await prisma.user.findUnique({
    where: { id: userId },
    select: {
      id: true,
      email: true,
      role: true,
      profile: {
        include: {
          alumniProfile: {
            include: { institution: true }
          }
        }
      },
      mentorProfile: true,
      skills: true
    }
  });

  sendSuccess(res, completeUser, 'Alumni profile updated successfully.');
}

// -----------------------------------------------------------------------------
// Privacy & Security Settings
// -----------------------------------------------------------------------------
export async function getPrivacySettings(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  let settings = await prisma.privacySettings.findUnique({ where: { userId } });
  if (!settings) {
    settings = await prisma.privacySettings.create({ data: { userId } });
  }

  sendSuccess(res, settings, 'Privacy settings retrieved');
}

export async function updatePrivacySettings(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { showEmail, showPhone, showGpa, allowMessagesFrom, allowMentorshipRequests } = req.body;

  const updated = await prisma.privacySettings.upsert({
    where: { userId },
    create: {
      userId,
      showEmail: showEmail ?? false,
      showPhone: showPhone ?? false,
      showGpa: showGpa ?? false,
      allowMessagesFrom: allowMessagesFrom || 'ALL',
      allowMentorshipRequests: allowMentorshipRequests ?? true
    },
    update: {
      showEmail: showEmail !== undefined ? showEmail : undefined,
      showPhone: showPhone !== undefined ? showPhone : undefined,
      showGpa: showGpa !== undefined ? showGpa : undefined,
      allowMessagesFrom: allowMessagesFrom !== undefined ? allowMessagesFrom : undefined,
      allowMentorshipRequests: allowMentorshipRequests !== undefined ? allowMentorshipRequests : undefined
    }
  });

  sendSuccess(res, updated, 'Privacy settings updated.');
}

export async function getSecuritySettings(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  let settings = await prisma.securitySettings.findUnique({ where: { userId } });
  if (!settings) {
    settings = await prisma.securitySettings.create({ data: { userId } });
  }

  sendSuccess(res, settings, 'Security settings retrieved');
}

export async function updateSecuritySettings(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { twoFactorEnabled, loginAlertsEnabled } = req.body;

  const updated = await prisma.securitySettings.upsert({
    where: { userId },
    create: {
      userId,
      twoFactorEnabled: twoFactorEnabled ?? false,
      loginAlertsEnabled: loginAlertsEnabled ?? true
    },
    update: {
      twoFactorEnabled: twoFactorEnabled !== undefined ? twoFactorEnabled : undefined,
      loginAlertsEnabled: loginAlertsEnabled !== undefined ? loginAlertsEnabled : undefined
    }
  });

  sendSuccess(res, updated, 'Security settings updated.');
}

export async function createAccountRecovery(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { recoveryEmail } = req.body;

  if (!recoveryEmail || !recoveryEmail.includes('@')) {
    sendError(res, 'Valid recovery email is required.', 400, 'VALIDATION_ERROR');
    return;
  }

  const token = `rec_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  const expiresAt = new Date(Date.now() + 86400000 * 7); // 7 days

  const recovery = await prisma.accountRecovery.create({
    data: {
      userId,
      recoveryEmail,
      token,
      expiresAt
    }
  });

  sendSuccess(res, { recoveryEmail: recovery.recoveryEmail, token: recovery.token }, 'Recovery email configured successfully.', 201);
}
