import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

export async function seedProducts() {
  console.log('Seeding monetization products with integer paise amounts...');

  const products = [
    {
      sku: 'CV_STUDENT_PRO_1M',
      title: 'CampusVerse Student Pro (1 Month)',
      description: 'Unlimited AI mock interview sessions, priority verified badge, and access to exclusive campus workshops.',
      amountPaise: 49900, // ₹499.00
      currency: 'INR',
      targetRole: 'STUDENT',
      productType: 'STUDENT_PREMIUM',
      isActive: true,
      metadata: JSON.stringify({ durationDays: 30, featureFlags: ['AI_INTERVIEWS', 'VERIFIED_BADGE'] })
    },
    {
      sku: 'CV_ASPIRANT_PASS_1M',
      title: 'Aspirant Admissions FastTrack (1 Month)',
      description: 'AI admission probability predictions, direct alumni campus tours, and scholarship finder matching.',
      amountPaise: 29900, // ₹299.00
      currency: 'INR',
      targetRole: 'ASPIRANT',
      productType: 'ASPIRANT_PREMIUM',
      isActive: true,
      metadata: JSON.stringify({ durationDays: 30, featureFlags: ['ADMISSION_AI', 'CAMPUS_TOURS'] })
    },
    {
      sku: 'CV_ALUMNI_PRO_1M',
      title: 'Alumni Recruiter & Mentor Pro (1 Month)',
      description: 'Priority job posting placement, student talent directory search, and featured mentor listing.',
      amountPaise: 99900, // ₹999.00
      currency: 'INR',
      targetRole: 'ALUMNI',
      productType: 'ALUMNI_PREMIUM',
      isActive: true,
      metadata: JSON.stringify({ durationDays: 30, featureFlags: ['PRIORITY_JOBS', 'TALENT_SEARCH'] })
    },
    {
      sku: 'CV_AI_CREDITS_50',
      title: '50 AI Interview & Resume Credits',
      description: '50 credits for AI resume scoring, ATS optimization, and personalized interview practice feedback.',
      amountPaise: 19900, // ₹199.00
      currency: 'INR',
      targetRole: 'ALL',
      productType: 'AI_CREDITS',
      isActive: true,
      metadata: JSON.stringify({ credits: 50 })
    },
    {
      sku: 'CV_MOCK_INTERVIEW_PASS',
      title: '1-on-1 Alumni Mock Interview Pass',
      description: '1 live 45-minute technical or behavioral mock interview with a verified industry alumni.',
      amountPaise: 149900, // ₹1,499.00
      currency: 'INR',
      targetRole: 'STUDENT',
      productType: 'MENTORSHIP',
      isActive: true,
      metadata: JSON.stringify({ sessions: 1, durationMinutes: 45 })
    }
  ];

  for (const prod of products) {
    await prisma.product.upsert({
      where: { sku: prod.sku },
      update: {
        title: prod.title,
        description: prod.description,
        amountPaise: prod.amountPaise,
        currency: prod.currency,
        targetRole: prod.targetRole,
        productType: prod.productType,
        isActive: prod.isActive,
        metadata: prod.metadata
      },
      create: prod
    });
  }

  const count = await prisma.product.count();
  console.log(`Successfully upserted products. Total products in catalog: ${count}`);
}

if (require.main === module) {
  seedProducts()
    .then(async () => {
      await prisma.$disconnect();
      process.exit(0);
    })
    .catch(async (e) => {
      console.error(e);
      await prisma.$disconnect();
      process.exit(1);
    });
}
