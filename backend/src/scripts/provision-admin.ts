import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';
import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const dbUrl = process.env.DATABASE_URL || 'file:./dev.db';
const resolvedDbUrl = dbUrl.startsWith('file:./')
  ? `file:${path.resolve(__dirname, '../../prisma', dbUrl.substring(7))}`
  : dbUrl;

const prisma = new PrismaClient({
  datasources: {
    db: {
      url: resolvedDbUrl
    }
  }
});

async function provisionAdmin() {
  const email = process.env.ADMIN_PROVISION_EMAIL || process.argv[2];
  const password = process.env.ADMIN_PROVISION_PASSWORD || process.argv[3];
  const fullName = process.env.ADMIN_PROVISION_NAME || process.argv[4] || 'Campus Administrator';

  if (!email || !password) {
    console.error('================================================================');
    console.error('CAMPUSVERSE PRODUCTION ADMIN PROVISIONING');
    console.error('================================================================');
    console.error('Usage via environment variables:');
    console.error('  ADMIN_PROVISION_EMAIL="admin@yourinstitution.edu" \\');
    console.error('  ADMIN_PROVISION_PASSWORD="StrongSecurePassword123!" \\');
    console.error('  ADMIN_PROVISION_NAME="Senior Ops Admin" \\');
    console.error('  npm run admin:provision\n');
    console.error('Or via CLI arguments:');
    console.error('  npx ts-node src/scripts/provision-admin.ts <email> <password> [fullName]');
    console.error('================================================================');
    process.exit(1);
  }

  // 1. Validate email format
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    console.error('ERROR: Invalid email address format.');
    process.exit(1);
  }

  // 2. Validate password strength
  if (password.length < 12) {
    console.error('ERROR: Password must be at least 12 characters long for administrator accounts.');
    process.exit(1);
  }
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);

  if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
    console.error('ERROR: Password must contain uppercase, lowercase, numbers, and special characters.');
    process.exit(1);
  }

  try {
    console.log(`Provisioning authorized administrator account: ${email.replace(/(.{2})(.*)(@.*)/, '$1***$3')}...`);
    const passwordHash = await bcrypt.hash(password, 12);

    const existingUser = await prisma.user.findUnique({ where: { email } });

    let user;
    if (existingUser) {
      user = await prisma.user.update({
        where: { id: existingUser.id },
        data: {
          passwordHash,
          role: 'ADMIN',
          isAdminAuthorized: true,
          isEmailVerified: true,
          isActive: true
        }
      });
      console.log('Updated existing user account to AUTHORIZED ADMIN status.');
    } else {
      user = await prisma.user.create({
        data: {
          email,
          passwordHash,
          role: 'ADMIN',
          isAdminAuthorized: true,
          isEmailVerified: true,
          isActive: true
        }
      });
      console.log('Created new AUTHORIZED ADMIN user account.');
    }

    // Upsert Profile
    const profile = await prisma.profile.upsert({
      where: { userId: user.id },
      create: {
        userId: user.id,
        fullName,
        headline: 'Platform Operations Administrator',
        bio: 'Authorized administrator provisioned through secure production provisioning protocol.'
      },
      update: {
        fullName,
        headline: 'Platform Operations Administrator'
      }
    });

    // Upsert AdminProfile
    await prisma.adminProfile.upsert({
      where: { profileId: profile.id },
      create: {
        profileId: profile.id,
        department: 'Platform Operations',
        accessLevel: 'SUPERADMIN'
      },
      update: {
        department: 'Platform Operations',
        accessLevel: 'SUPERADMIN'
      }
    });

    // Record audit log
    await prisma.auditLog.create({
      data: {
        actorId: user.id,
        action: 'ADMIN_PROVISIONED_PRODUCTION',
        targetType: 'USER',
        targetId: user.id,
        metadata: JSON.stringify({
          emailMasked: email.replace(/(.{2})(.*)(@.*)/, '$1***$3'),
          isAdminAuthorized: true,
          provisionedAt: new Date().toISOString()
        })
      }
    });

    console.log('✓ Administrator account successfully provisioned and authorized.');
    console.log('✓ AuditLog entry logged.');
    console.log('✓ Zero credentials printed to output or persisted in source files.');
  } catch (err: any) {
    console.error('ERROR provisioning administrator:', err.message);
    process.exit(1);
  } finally {
    await prisma.$disconnect();
  }
}

provisionAdmin();
