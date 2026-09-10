import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { sendSuccess, sendError } from '../utils/response';

function safeJsonParse(val: string | null | undefined, fallback: any = []): any {
  if (!val) return fallback;
  try {
    return JSON.parse(val);
  } catch {
    return typeof fallback === 'object' && !Array.isArray(fallback) ? {} : [val];
  }
}

// ==========================================
// Zod Validation Schemas
// ==========================================

export const predictAdmissionSchema = z.object({
  institutionId: z.string().optional(),
  institutionName: z.string().min(2).max(100).optional(),
  programName: z.string().min(2).max(100),
  degree: z.string().default('B.TECH'),
  gpa: z.number().min(0.0).max(10.0),
  testType: z.enum(['JEE_MAIN', 'JEE_ADVANCED', 'SAT', 'ACT', 'GRE', 'NEET', 'BITSAT', 'IELTS', 'TOEFL']),
  testScore: z.number().min(0.0).max(2400.0)
});

export const compareCollegesSchema = z.object({
  collegeIds: z.array(z.string().uuid()).min(2).max(4)
});

export const updateAspirantProfileSchema = z.object({
  fullName: z.string().min(2).max(60).optional(),
  bio: z.string().max(500).optional(),
  targetDegree: z.string().max(60).optional(),
  targetMajor: z.string().max(80).optional(),
  targetUniversities: z.string().max(200).optional(),
  highSchool: z.string().max(120).optional(),
  expectedGradYear: z.number().int().min(2024).max(2035).optional(),
  entranceExamScores: z.record(z.any()).optional()
});

// ==========================================
// 1. Aspirant Home Dashboard
// ==========================================

export async function getAspirantHomeSummary(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;

    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: {
        profile: {
          include: {
            aspirantProfile: true
          }
        },
        savedColleges: {
          include: {
            institution: {
              include: {
                programs: true
              }
            }
          }
        },
        savedScholarships: {
          include: {
            scholarship: true
          }
        },
        admissionPredictions: {
          orderBy: { createdAt: 'desc' },
          take: 3
        },
        notifications: {
          where: { isRead: false },
          orderBy: { createdAt: 'desc' },
          take: 5
        }
      }
    });

    if (!user) {
      sendError(res, 'User not found', 404);
      return;
    }

    // Top recommended colleges (verified, ranked)
    const recommendedColleges = await prisma.institution.findMany({
      where: { verified: true },
      include: {
        programs: true,
        savedByUsers: {
          where: { userId }
        }
      },
      orderBy: { ranking: 'asc' },
      take: 4
    });

    const formattedRecommended = recommendedColleges.map((inst) => ({
      id: inst.id,
      name: inst.name,
      code: inst.code,
      city: inst.city,
      state: inst.state,
      country: inst.country,
      ranking: inst.ranking || 99,
      acceptanceRate: inst.acceptanceRate || 10.0,
      averageFees: inst.averageFees || 'Contact Institution',
      overview: inst.overview,
      campusSize: inst.campusSize,
      websiteUrl: inst.websiteUrl,
      programsCount: inst.programs.length,
      isSaved: inst.savedByUsers.length > 0
    }));

    const summary = {
      profile: {
        userId: user.id,
        fullName: user.profile?.fullName || 'Aspirant',
        email: user.email,
        bio: user.profile?.bio,
        targetDegree: user.profile?.aspirantProfile?.targetDegree || 'B.Tech',
        targetMajor: user.profile?.aspirantProfile?.targetMajor || 'Computer Science',
        targetUniversities: user.profile?.aspirantProfile?.targetUniversities,
        highSchool: user.profile?.aspirantProfile?.highSchool,
        expectedGradYear: user.profile?.aspirantProfile?.expectedGradYear || 2027,
        entranceExamScores: safeJsonParse(user.profile?.aspirantProfile?.entranceExamScores, null)
      },
      savedCollegesCount: user.savedColleges.length,
      savedScholarshipsCount: user.savedScholarships.length,
      recentPredictionsCount: user.admissionPredictions.length,
      recommendedColleges: formattedRecommended,
      recentPredictions: user.admissionPredictions.map((pred) => ({
        id: pred.id,
        institutionName: pred.institutionName,
        programName: pred.programName,
        degree: pred.degree,
        predictionPercentage: pred.predictionPercentage,
        qualificationStatus: pred.qualificationStatus,
        feedback: pred.feedback,
        createdAt: pred.createdAt.toISOString()
      })),
      savedColleges: user.savedColleges.map((sc) => ({
        id: sc.id,
        institutionId: sc.institution.id,
        name: sc.institution.name,
        city: sc.institution.city,
        state: sc.institution.state,
        country: sc.institution.country,
        ranking: sc.institution.ranking,
        averageFees: sc.institution.averageFees,
        savedAt: sc.savedAt.toISOString()
      })),
      savedScholarships: user.savedScholarships.map((ss) => ({
        id: ss.id,
        scholarshipId: ss.scholarship.id,
        name: ss.scholarship.name,
        provider: ss.scholarship.provider,
        amount: ss.scholarship.amount,
        deadline: ss.scholarship.deadline,
        savedAt: ss.savedAt.toISOString()
      })),
      notifications: user.notifications.map((n) => ({
        id: n.id,
        title: n.title,
        message: n.message,
        type: n.type,
        createdAt: n.createdAt.toISOString()
      }))
    };

    sendSuccess(res, summary, 'Aspirant dashboard summary retrieved');
  } catch (error: any) {
    console.error('getAspirantSummary error:', error);
    sendError(res, 'Failed to retrieve dashboard summary', 500);
  }
}

// ==========================================
// 2. Colleges & Explorer
// ==========================================

export async function getColleges(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user?.userId;
    const { search, country, maxFees, minRanking, sortBy } = req.query;

    const where: any = {};

    if (search && typeof search === 'string') {
      where.OR = [
        { name: { contains: search } },
        { city: { contains: search } },
        { state: { contains: search } },
        { country: { contains: search } }
      ];
    }

    if (country && typeof country === 'string') {
      where.country = { equals: country };
    }

    if (minRanking && typeof minRanking === 'string') {
      where.ranking = { lte: parseInt(minRanking, 10) };
    }

    let orderBy: any = { ranking: 'asc' };
    if (sortBy === 'acceptance') {
      orderBy = { acceptanceRate: 'desc' };
    } else if (sortBy === 'name') {
      orderBy = { name: 'asc' };
    }

    const colleges = await prisma.institution.findMany({
      where,
      include: {
        programs: true,
        savedByUsers: userId ? { where: { userId } } : false
      },
      orderBy
    });

    const formatted = colleges.map((inst) => ({
      id: inst.id,
      name: inst.name,
      code: inst.code,
      city: inst.city,
      state: inst.state,
      country: inst.country,
      ranking: inst.ranking || 99,
      acceptanceRate: inst.acceptanceRate || 10.0,
      averageFees: inst.averageFees || 'Contact Admissions',
      overview: inst.overview,
      campusSize: inst.campusSize,
      websiteUrl: inst.websiteUrl,
      programsCount: inst.programs.length,
      programs: inst.programs.map((p) => ({
        id: p.id,
        name: p.name,
        degree: p.degree,
        major: p.major,
        durationYears: p.durationYears,
        tuitionFee: p.tuitionFee,
        minGpa: p.minGpa,
        deadline: p.deadline
      })),
      isSaved: userId ? (inst as any).savedByUsers?.length > 0 : false
    }));

    sendSuccess(res, formatted, 'Colleges retrieved successfully');
  } catch (error: any) {
    console.error('getColleges error:', error);
    sendError(res, 'Failed to retrieve colleges', 500);
  }
}

export async function getCollegeById(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user?.userId;
    const { id } = req.params;

    const inst = await prisma.institution.findUnique({
      where: { id },
      include: {
        programs: true,
        savedByUsers: userId ? { where: { userId } } : false
      }
    });

    if (!inst) {
      sendError(res, 'College not found', 404);
      return;
    }

    const formatted = {
      id: inst.id,
      name: inst.name,
      code: inst.code,
      city: inst.city,
      state: inst.state,
      country: inst.country,
      ranking: inst.ranking || 99,
      acceptanceRate: inst.acceptanceRate || 10.0,
      averageFees: inst.averageFees || 'Contact Admissions',
      overview: inst.overview,
      campusSize: inst.campusSize,
      websiteUrl: inst.websiteUrl,
      programs: inst.programs.map((p) => ({
        id: p.id,
        name: p.name,
        degree: p.degree,
        major: p.major,
        durationYears: p.durationYears,
        tuitionFee: p.tuitionFee,
        minGpa: p.minGpa,
        entranceExams: safeJsonParse(p.entranceExams, []),
        deadline: p.deadline,
        overview: p.overview
      })),
      isSaved: userId ? (inst as any).savedByUsers?.length > 0 : false
    };

    sendSuccess(res, formatted, 'College details retrieved successfully');
  } catch (error: any) {
    console.error('getCollegeById error:', error);
    sendError(res, 'Failed to retrieve college details', 500);
  }
}

export async function saveCollege(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { id } = req.params;

    const college = await prisma.institution.findUnique({ where: { id } });
    if (!college) {
      sendError(res, 'College not found', 404);
      return;
    }

    await prisma.savedCollege.upsert({
      where: {
        userId_institutionId: {
          userId,
          institutionId: id
        }
      },
      create: {
        userId,
        institutionId: id
      },
      update: {}
    });

    sendSuccess(res, { saved: true, collegeId: id }, 'College bookmarked successfully');
  } catch (error: any) {
    console.error('saveCollege error:', error);
    sendError(res, 'Failed to save college', 500);
  }
}

export async function unsaveCollege(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { id } = req.params;

    await prisma.savedCollege.deleteMany({
      where: {
        userId,
        institutionId: id
      }
    });

    sendSuccess(res, { saved: false, collegeId: id }, 'College removed from bookmarks');
  } catch (error: any) {
    console.error('unsaveCollege error:', error);
    sendError(res, 'Failed to unsave college', 500);
  }
}

export async function getSavedColleges(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;

    const saved = await prisma.savedCollege.findMany({
      where: { userId },
      include: {
        institution: {
          include: {
            programs: true
          }
        }
      },
      orderBy: { savedAt: 'desc' }
    });

    const formatted = saved.map((s) => ({
      id: s.institution.id,
      name: s.institution.name,
      code: s.institution.code,
      city: s.institution.city,
      state: s.institution.state,
      country: s.institution.country,
      ranking: s.institution.ranking || 99,
      acceptanceRate: s.institution.acceptanceRate || 10.0,
      averageFees: s.institution.averageFees || 'Contact Admissions',
      overview: s.institution.overview,
      programsCount: s.institution.programs.length,
      savedAt: s.savedAt.toISOString(),
      isSaved: true
    }));

    sendSuccess(res, formatted, 'Saved colleges retrieved');
  } catch (error: any) {
    console.error('getSavedColleges error:', error);
    sendError(res, 'Failed to retrieve saved colleges', 500);
  }
}

// ==========================================
// 3. College Comparison
// ==========================================

export async function compareColleges(req: Request, res: Response): Promise<void> {
  try {
    const { collegeIds } = req.body;

    const colleges = await prisma.institution.findMany({
      where: {
        id: { in: collegeIds }
      },
      include: {
        programs: true
      }
    });

    if (colleges.length < 2) {
      sendError(res, 'At least 2 valid colleges required for comparison', 400);
      return;
    }

    const comparisonData = colleges.map((inst) => ({
      id: inst.id,
      name: inst.name,
      country: inst.country,
      city: inst.city,
      state: inst.state,
      ranking: inst.ranking || 99,
      acceptanceRate: inst.acceptanceRate || 10.0,
      averageFees: inst.averageFees || 'N/A',
      campusSize: inst.campusSize || 'N/A',
      websiteUrl: inst.websiteUrl || 'N/A',
      programs: inst.programs.map((p) => ({
        name: p.name,
        degree: p.degree,
        major: p.major,
        tuitionFee: p.tuitionFee,
        minGpa: p.minGpa || 7.0
      }))
    }));

    sendSuccess(res, { colleges: comparisonData }, 'Comparison results generated');
  } catch (error: any) {
    console.error('compareColleges error:', error);
    sendError(res, 'Failed to compare colleges', 500);
  }
}

// ==========================================
// 4. Admission Predictor
// ==========================================

export async function predictAdmission(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { institutionId, institutionName, programName, degree, gpa, testType, testScore } = req.body;

    let targetInstName = institutionName || 'Selected Institution';
    let targetAcceptanceRate = 15.0;
    let minGpaReq = 8.0;

    if (institutionId) {
      const inst = await prisma.institution.findFirst({
        where: {
          OR: [
            { id: institutionId },
            { code: institutionId }
          ]
        },
        include: { programs: true }
      });
      if (inst) {
        targetInstName = inst.name;
        targetAcceptanceRate = inst.acceptanceRate || 15.0;
        const matchingProg = inst.programs.find((p) => p.name.toLowerCase().includes(programName.toLowerCase()));
        if (matchingProg && matchingProg.minGpa) {
          minGpaReq = matchingProg.minGpa;
        }
      }
    }

    // Weighted algorithmic prediction calculation
    let gpaScore = Math.min(100.0, (gpa / (minGpaReq || 8.0)) * 80.0);
    
    // Normalize test scores to 0-100 scale
    let normalizedTestScore = 75.0;
    if (testType === 'JEE_MAIN') {
      normalizedTestScore = Math.min(100.0, testScore); // Percentile
    } else if (testType === 'SAT') {
      normalizedTestScore = Math.min(100.0, (testScore / 1600.0) * 100.0);
    } else if (testType === 'ACT') {
      normalizedTestScore = Math.min(100.0, (testScore / 36.0) * 100.0);
    } else if (testType === 'GRE') {
      normalizedTestScore = Math.min(100.0, ((testScore - 260.0) / 80.0) * 100.0);
    } else if (testType === 'BITSAT') {
      normalizedTestScore = Math.min(100.0, (testScore / 390.0) * 100.0);
    } else if (testType === 'NEET') {
      normalizedTestScore = Math.min(100.0, (testScore / 720.0) * 100.0);
    }

    // Acceptance rate selectivity adjustment
    const selectivityFactor = Math.max(0.4, Math.min(1.0, (targetAcceptanceRate + 10.0) / 30.0));
    
    let rawPercentage = ((gpaScore * 0.45) + (normalizedTestScore * 0.55)) * selectivityFactor;
    rawPercentage = Math.max(15.0, Math.min(96.5, Math.round(rawPercentage * 10.0) / 10.0));

    let qualificationStatus = 'COMPETITIVE';
    if (rawPercentage >= 85.0) {
      qualificationStatus = 'STRONG_CANDIDATE';
    } else if (rawPercentage >= 60.0) {
      qualificationStatus = 'COMPETITIVE';
    } else if (rawPercentage >= 35.0) {
      qualificationStatus = 'REACH';
    } else {
      qualificationStatus = 'UNLIKELY';
    }

    let feedback = '';
    const recommendations: string[] = [];

    if (qualificationStatus === 'STRONG_CANDIDATE') {
      feedback = `Outstanding academic profile! Your ${gpa} GPA and ${testScore} on ${testType} significantly exceed typical applicant cutoffs for ${targetInstName}.`;
      recommendations.push('Prepare and lock round 1 counseling choices early.');
      recommendations.push('Apply for institutional merit scholarships before the deadline.');
      recommendations.push('Connect with current students on CampusVerse to explore housing and course registration.');
    } else if (qualificationStatus === 'COMPETITIVE') {
      feedback = `Solid competitive match. Your metrics align well with admitted cohorts at ${targetInstName}, with high probability in standard admission rounds.`;
      recommendations.push('Keep 1-2 safety options in your shortlist.');
      recommendations.push('Focus on maintaining high final semester GPA.');
      recommendations.push('Ensure all verification documents and transcripts are prepared.');
    } else if (qualificationStatus === 'REACH') {
      feedback = `${targetInstName} is highly selective (${targetAcceptanceRate}% acceptance). Your profile qualifies for consideration, but holistic factors will weigh heavily.`;
      recommendations.push('Craft impactful personal essays highlighting projects and extracurricular initiatives.');
      recommendations.push('Secure strong faculty recommendation letters.');
      recommendations.push('Consider retaking standardized tests if target windows allow.');
    } else {
      feedback = `Admission to ${targetInstName} is challenging with current metrics. Consider targeting related programs or safety institutions.`;
      recommendations.push('Explore alternate institutions with higher historical acceptance rates for your score bracket.');
      recommendations.push('Consider academic pathway or bridge diploma options.');
    }

    // Persist prediction
    const prediction = await prisma.admissionPrediction.create({
      data: {
        userId,
        institutionId: institutionId || null,
        institutionName: targetInstName,
        programName,
        degree,
        gpa,
        testType,
        testScore,
        predictionPercentage: rawPercentage,
        qualificationStatus,
        feedback,
        recommendations: JSON.stringify(recommendations)
      }
    });

    sendSuccess(res, {
      id: prediction.id,
      institutionName: targetInstName,
      programName,
      degree,
      gpa,
      testType,
      testScore,
      predictionPercentage: rawPercentage,
      qualificationStatus,
      feedback,
      recommendations,
      createdAt: prediction.createdAt.toISOString()
    }, 'Admission prediction calculated successfully');
  } catch (error: any) {
    console.error('predictAdmission error:', error);
    sendError(res, 'Failed to compute admission prediction', 500);
  }
}

export async function getPredictionHistory(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;

    const predictions = await prisma.admissionPrediction.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' }
    });

    const formatted = predictions.map((p) => ({
      id: p.id,
      institutionName: p.institutionName,
      programName: p.programName,
      degree: p.degree,
      gpa: p.gpa,
      testType: p.testType,
      testScore: p.testScore,
      predictionPercentage: p.predictionPercentage,
      qualificationStatus: p.qualificationStatus,
      feedback: p.feedback,
      recommendations: safeJsonParse(p.recommendations, []),
      createdAt: p.createdAt.toISOString()
    }));

    sendSuccess(res, formatted, 'Prediction history retrieved');
  } catch (error: any) {
    console.error('getPredictionHistory error:', error);
    sendError(res, 'Failed to retrieve prediction history', 500);
  }
}

// ==========================================
// 5. Scholarships & Finder
// ==========================================

export async function getScholarships(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user?.userId;
    const { search, category, country } = req.query;

    const where: any = {};

    if (search && typeof search === 'string') {
      where.OR = [
        { name: { contains: search } },
        { provider: { contains: search } },
        { eligibility: { contains: search } }
      ];
    }

    if (category && typeof category === 'string') {
      where.category = { equals: category.toUpperCase() };
    }

    if (country && typeof country === 'string') {
      where.country = { equals: country };
    }

    const scholarships = await prisma.scholarship.findMany({
      where,
      include: {
        savedByUsers: userId ? { where: { userId } } : false
      },
      orderBy: { createdAt: 'desc' }
    });

    const formatted = scholarships.map((s) => ({
      id: s.id,
      name: s.name,
      provider: s.provider,
      amount: s.amount,
      deadline: s.deadline,
      eligibility: s.eligibility,
      description: s.description,
      requirements: safeJsonParse(s.requirements, []),
      applicationUrl: s.applicationUrl,
      category: s.category,
      country: s.country,
      isSaved: userId ? (s as any).savedByUsers?.length > 0 : false
    }));

    sendSuccess(res, formatted, 'Scholarships retrieved successfully');
  } catch (error: any) {
    console.error('getScholarships error:', error);
    sendError(res, 'Failed to retrieve scholarships', 500);
  }
}

export async function getScholarshipById(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user?.userId;
    const { id } = req.params;

    const s = await prisma.scholarship.findUnique({
      where: { id },
      include: {
        savedByUsers: userId ? { where: { userId } } : false
      }
    });

    if (!s) {
      sendError(res, 'Scholarship not found', 404);
      return;
    }

    const formatted = {
      id: s.id,
      name: s.name,
      provider: s.provider,
      amount: s.amount,
      deadline: s.deadline,
      eligibility: s.eligibility,
      description: s.description,
      requirements: safeJsonParse(s.requirements, []),
      applicationUrl: s.applicationUrl,
      category: s.category,
      country: s.country,
      isSaved: userId ? (s as any).savedByUsers?.length > 0 : false
    };

    sendSuccess(res, formatted, 'Scholarship retrieved successfully');
  } catch (error: any) {
    console.error('getScholarshipById error:', error);
    sendError(res, 'Failed to retrieve scholarship', 500);
  }
}

export async function saveScholarship(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { id } = req.params;

    const scholarship = await prisma.scholarship.findUnique({
      where: { id }
    });

    if (!scholarship) {
      sendError(res, 'Scholarship not found', 404);
      return;
    }

    const existing = await prisma.savedScholarship.findUnique({
      where: {
        userId_scholarshipId: {
          userId,
          scholarshipId: id
        }
      }
    });

    const saved = await prisma.savedScholarship.upsert({
      where: {
        userId_scholarshipId: {
          userId,
          scholarshipId: id
        }
      },
      create: {
        userId,
        scholarshipId: id
      },
      update: {}
    });

    sendSuccess(res, { saved: true, isSaved: true, scholarshipId: id, id: scholarship.id }, 'Scholarship saved successfully');
  } catch (error: any) {
    console.error('saveScholarship error:', error);
    sendError(res, 'Failed to save scholarship', 500);
  }
}

export async function unsaveScholarship(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { id } = req.params;

    await prisma.savedScholarship.deleteMany({
      where: {
        userId,
        scholarshipId: id
      }
    });

    sendSuccess(res, { saved: false, scholarshipId: id }, 'Scholarship removed from saved list');
  } catch (error: any) {
    console.error('unsaveScholarship error:', error);
    sendError(res, 'Failed to unsave scholarship', 500);
  }
}

export async function getSavedScholarships(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;

    const saved = await prisma.savedScholarship.findMany({
      where: { userId },
      include: {
        scholarship: true
      },
      orderBy: { savedAt: 'desc' }
    });

    const formatted = saved.map((item) => ({
      id: item.scholarship.id,
      savedScholarshipId: item.id,
      scholarshipId: item.scholarship.id,
      name: item.scholarship.name,
      provider: item.scholarship.provider,
      amount: item.scholarship.amount,
      deadline: item.scholarship.deadline,
      eligibility: item.scholarship.eligibility,
      category: item.scholarship.category,
      country: item.scholarship.country,
      savedAt: item.savedAt.toISOString(),
      isSaved: true
    }));

    sendSuccess(res, formatted, 'Saved scholarships retrieved');
  } catch (error: any) {
    console.error('getSavedScholarships error:', error);
    sendError(res, 'Failed to retrieve saved scholarships', 500);
  }
}

// ==========================================
// 6. Aspirant Profile & Exam Scores
// ==========================================

export async function getAspirantProfile(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;

    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: {
        profile: {
          include: {
            aspirantProfile: true
          }
        }
      }
    });

    if (!user) {
      sendError(res, 'User not found', 404);
      return;
    }

    const asp = user.profile?.aspirantProfile;
    const formatted = {
      userId: user.id,
      email: user.email,
      fullName: user.profile?.fullName || '',
      bio: user.profile?.bio || '',
      avatarUrl: user.profile?.avatarUrl,
      targetDegree: asp?.targetDegree || 'B.Tech',
      targetMajor: asp?.targetMajor || 'Computer Science',
      targetUniversities: asp?.targetUniversities || '',
      highSchool: asp?.highSchool || '',
      expectedGradYear: asp?.expectedGradYear || 2027,
      entranceExamScores: safeJsonParse(asp?.entranceExamScores, {})
    };

    sendSuccess(res, formatted, 'Aspirant profile retrieved');
  } catch (error: any) {
    console.error('getAspirantProfile error:', error);
    sendError(res, 'Failed to retrieve aspirant profile', 500);
  }
}

export async function updateAspirantProfile(req: Request, res: Response): Promise<void> {
  try {
    const userId = req.user!.userId;
    const {
      fullName,
      bio,
      targetDegree,
      targetMajor,
      targetUniversities,
      highSchool,
      expectedGradYear,
      entranceExamScores
    } = req.body;

    const profile = await prisma.profile.findUnique({
      where: { userId }
    });

    if (!profile) {
      sendError(res, 'Profile not found', 404);
      return;
    }

    if (fullName !== undefined || bio !== undefined) {
      await prisma.profile.update({
        where: { userId },
        data: {
          ...(fullName !== undefined && { fullName }),
          ...(bio !== undefined && { bio })
        }
      });
    }

    await prisma.aspirantProfile.upsert({
      where: { profileId: profile.id },
      create: {
        profileId: profile.id,
        targetDegree,
        targetMajor,
        targetUniversities,
        highSchool,
        expectedGradYear,
        entranceExamScores: entranceExamScores ? JSON.stringify(entranceExamScores) : null
      },
      update: {
        ...(targetDegree !== undefined && { targetDegree }),
        ...(targetMajor !== undefined && { targetMajor }),
        ...(targetUniversities !== undefined && { targetUniversities }),
        ...(highSchool !== undefined && { highSchool }),
        ...(expectedGradYear !== undefined && { expectedGradYear }),
        ...(entranceExamScores !== undefined && { entranceExamScores: JSON.stringify(entranceExamScores) })
      }
    });

    const updatedUser = await prisma.user.findUnique({
      where: { id: userId },
      include: {
        profile: {
          include: {
            aspirantProfile: true
          }
        }
      }
    });

    const updatedAsp = updatedUser?.profile?.aspirantProfile;
    sendSuccess(res, {
      userId: updatedUser!.id,
      email: updatedUser!.email,
      fullName: updatedUser!.profile?.fullName,
      bio: updatedUser!.profile?.bio,
      targetDegree: updatedAsp?.targetDegree,
      targetMajor: updatedAsp?.targetMajor,
      targetUniversities: updatedAsp?.targetUniversities,
      highSchool: updatedAsp?.highSchool,
      expectedGradYear: updatedAsp?.expectedGradYear,
      entranceExamScores: safeJsonParse(updatedAsp?.entranceExamScores, {})
    }, 'Aspirant profile updated successfully');
  } catch (error: any) {
    console.error('updateAspirantProfile error:', error);
    sendError(res, 'Failed to update aspirant profile', 500);
  }
}
