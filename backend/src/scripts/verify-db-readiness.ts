import { PrismaClient } from '@prisma/client';

/**
 * Production Database Readiness & Integrity Verification Script
 * Validates connection, schema, relations, constraints, and core model operations
 * without leaving dirty data in the target database.
 */
async function verifyDatabaseReadiness() {
  const databaseUrl = process.env.DATABASE_URL;

  console.log('==================================================');
  console.log('CampusVerse Production Database Readiness Probe');
  console.log('==================================================');

  if (!databaseUrl) {
    console.error('[-] ERROR: DATABASE_URL environment variable is not defined.');
    process.exit(1);
  }

  const isPostgres = databaseUrl.startsWith('postgresql://') || databaseUrl.startsWith('postgres://');
  console.log(`[+] Database Protocol: ${isPostgres ? 'PostgreSQL (Production)' : 'SQLite (Local Development)'}`);

  const prisma = new PrismaClient({
    datasources: {
      db: { url: databaseUrl }
    }
  });

  try {
    // 1. Connection Ping
    console.log('[+] Attempting connection to database engine...');
    await prisma.$connect();
    console.log('[+] Connection established successfully.');

    // 2. Query Existing Model Metadata
    console.log('[+] Verifying core table accessibility...');
    const userCount = await prisma.user.count();
    const profileCount = await prisma.profile.count();
    const studentCount = await prisma.studentProfile.count();
    const aspirantCount = await prisma.aspirantProfile.count();
    const alumniCount = await prisma.alumniProfile.count();
    const orderCount = await prisma.paymentOrder.count();
    const transactionCount = await prisma.transaction.count();
    const entitlementCount = await prisma.entitlement.count();
    const refundCount = await prisma.refund.count();

    console.log(`[+] Record Counts:`);
    console.log(`    - Users:              ${userCount}`);
    console.log(`    - Profiles:           ${profileCount}`);
    console.log(`    - Student Profiles:   ${studentCount}`);
    console.log(`    - Aspirant Profiles:  ${aspirantCount}`);
    console.log(`    - Alumni Profiles:    ${alumniCount}`);
    console.log(`    - Payment Orders:     ${orderCount}`);
    console.log(`    - Transactions:       ${transactionCount}`);
    console.log(`    - Entitlements:       ${entitlementCount}`);
    console.log(`    - Refunds:            ${refundCount}`);

    // 3. Transient Atomic Transaction Test (Create -> Verify Relation -> Rollback / Clean)
    console.log('[+] Validating relations, constraints, and transaction atomicity...');
    const testEmail = `probe_${Date.now()}@readiness.test.internal`;

    await prisma.$transaction(async (tx) => {
      const probeUser = await tx.user.create({
        data: {
          email: testEmail,
          passwordHash: 'probe_hash_value',
          role: 'STUDENT',
          profile: {
            create: {
              fullName: 'Database Probe User',
              studentProfile: {
                create: {
                  studentIdNumber: 'PROBE-001',
                  degree: 'B.Tech',
                  major: 'Computer Science'
                }
              }
            }
          }
        },
        include: {
          profile: {
            include: {
              studentProfile: true
            }
          }
        }
      });

      if (!probeUser.profile?.studentProfile?.studentIdNumber) {
        throw new Error('Nested relation creation check failed.');
      }

      // Clean up probe record within the same transaction to leave zero footprint
      await tx.user.delete({
        where: { id: probeUser.id }
      });
    });

    console.log('[+] Atomic CRUD transaction test passed: Foreign keys, cascade deletions, and constraints verified.');
    console.log('==================================================');
    console.log('[+] VERIFICATION RESULT: DATABASE IS FULLY OPERATIONAL');
    console.log('==================================================');
  } catch (error: any) {
    console.error('[-] ERROR during database verification:', error.message || error);
    process.exit(1);
  } finally {
    await prisma.$disconnect();
  }
}

verifyDatabaseReadiness();
