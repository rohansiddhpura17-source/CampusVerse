import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

const ROLES = [
  {
    name: 'SUPER_ADMIN',
    description: 'Full administrative access across all system entities, settings, and audits',
    isSystem: true,
  },
  {
    name: 'ADMIN',
    description: 'Standard administrator managing users, events, and moderation',
    isSystem: true,
  },
  {
    name: 'MODERATOR',
    description: 'Responsible for reviewing reported content, resolving safety flags, and user moderation',
    isSystem: true,
  },
  {
    name: 'CONTENT_MANAGER',
    description: 'Manages colleges, courses, subjects, scholarships, and campus events',
    isSystem: true,
  },
  {
    name: 'SUPPORT_ADMIN',
    description: 'Student, aspirant, and alumni verification and support management',
    isSystem: true,
  },
  {
    name: 'ANALYTICS_ADMIN',
    description: 'Read-only telemetry, system analytics, AI usage monitoring, and audit log review',
    isSystem: true,
  },
];

const PERMISSIONS = [
  // Users & Roles
  { name: 'users.read', module: 'USERS', description: 'View user accounts, roles, and profiles' },
  { name: 'users.update', module: 'USERS', description: 'Update user profiles, roles, and credentials' },
  { name: 'users.suspend', module: 'USERS', description: 'Suspend or activate user accounts' },

  // Role-specific sub-entities
  { name: 'students.read', module: 'STUDENTS', description: 'View student academic profiles, GPAs, and records' },
  { name: 'students.update', module: 'STUDENTS', description: 'Update student institution and status' },
  { name: 'aspirants.read', module: 'ASPIRANTS', description: 'View applicant entrance scores and target programs' },
  { name: 'aspirants.update', module: 'ASPIRANTS', description: 'Update aspirant target data' },
  { name: 'alumni.read', module: 'ALUMNI', description: 'View alumni company positions, mentorship, and referrals' },
  { name: 'alumni.update', module: 'ALUMNI', description: 'Update alumni verification and mentor badges' },

  // Colleges & Academics
  { name: 'colleges.read', module: 'COLLEGES', description: 'View institutions and colleges' },
  { name: 'colleges.create', module: 'COLLEGES', description: 'Add new institutions and universities' },
  { name: 'colleges.update', module: 'COLLEGES', description: 'Update institution info, rankings, and details' },
  { name: 'colleges.delete', module: 'COLLEGES', description: 'Delete or archive institutions' },

  // Courses & Subjects
  { name: 'courses.read', module: 'COURSES', description: 'View academic courses and syllabi' },
  { name: 'courses.create', module: 'COURSES', description: 'Create academic courses' },
  { name: 'courses.update', module: 'COURSES', description: 'Update course details and credits' },
  { name: 'courses.delete', module: 'COURSES', description: 'Delete or archive academic courses' },

  // Scholarships
  { name: 'scholarships.read', module: 'SCHOLARSHIPS', description: 'View scholarship opportunities and applications' },
  { name: 'scholarships.create', module: 'SCHOLARSHIPS', description: 'Create new scholarship listings' },
  { name: 'scholarships.update', module: 'SCHOLARSHIPS', description: 'Update scholarship criteria and deadlines' },
  { name: 'scholarships.approve', module: 'SCHOLARSHIPS', description: 'Review and approve scholarship grant applications' },

  // Projects
  { name: 'projects.read', module: 'PROJECTS', description: 'View student and alumni engineering projects' },
  { name: 'projects.update', module: 'PROJECTS', description: 'Moderate or highlight project showcases' },
  { name: 'projects.delete', module: 'PROJECTS', description: 'Remove flagged project listings' },

  // Events
  { name: 'events.read', module: 'EVENTS', description: 'View campus events and registrations' },
  { name: 'events.create', module: 'EVENTS', description: 'Create official university events' },
  { name: 'events.update', module: 'EVENTS', description: 'Update event schedules, locations, and capacity' },
  { name: 'events.delete', module: 'EVENTS', description: 'Cancel or delete campus events' },

  // Mentorship
  { name: 'mentorship.read', module: 'MENTORSHIP', description: 'View mentors, sessions, and mentorship requests' },
  { name: 'mentorship.moderate', module: 'MENTORSHIP', description: 'Approve or suspend verified mentor status' },

  // Moderation & Reports
  { name: 'moderation.read', module: 'MODERATION', description: 'View content reports and flagged marketplace/posts' },
  { name: 'moderation.resolve', module: 'MODERATION', description: 'Take disciplinary action on reports' },

  // Analytics & Audits
  { name: 'analytics.read', module: 'ANALYTICS', description: 'View platform telemetry, user growth, and AI usage' },
  { name: 'audit.read', module: 'AUDIT', description: 'Inspect sensitive administrative audit logs' },

  // System Settings
  { name: 'settings.manage', module: 'SETTINGS', description: 'Configure platform flags, maintenance mode, and security policies' },
];

const ROLE_PERMISSIONS: Record<string, string[]> = {
  SUPER_ADMIN: ['*'], // All permissions
  ADMIN: [
    'users.read', 'users.update', 'users.suspend',
    'students.read', 'students.update',
    'aspirants.read', 'aspirants.update',
    'alumni.read', 'alumni.update',
    'colleges.read', 'colleges.create', 'colleges.update',
    'courses.read', 'courses.create', 'courses.update',
    'scholarships.read', 'scholarships.create', 'scholarships.update', 'scholarships.approve',
    'projects.read', 'projects.update',
    'events.read', 'events.create', 'events.update', 'events.delete',
    'mentorship.read', 'mentorship.moderate',
    'moderation.read', 'moderation.resolve',
    'analytics.read',
    'audit.read',
    'settings.manage',
  ],
  MODERATOR: [
    'users.read', 'users.suspend',
    'moderation.read', 'moderation.resolve',
    'projects.read', 'projects.update', 'projects.delete',
    'events.read', 'events.update',
    'mentorship.read', 'mentorship.moderate',
    'audit.read',
  ],
  CONTENT_MANAGER: [
    'colleges.read', 'colleges.create', 'colleges.update',
    'courses.read', 'courses.create', 'courses.update',
    'scholarships.read', 'scholarships.create', 'scholarships.update',
    'events.read', 'events.create', 'events.update', 'events.delete',
    'projects.read', 'projects.update',
  ],
  SUPPORT_ADMIN: [
    'users.read',
    'students.read', 'students.update',
    'aspirants.read', 'aspirants.update',
    'alumni.read', 'alumni.update',
    'scholarships.read', 'scholarships.approve',
    'moderation.read',
  ],
  ANALYTICS_ADMIN: [
    'users.read',
    'students.read',
    'aspirants.read',
    'alumni.read',
    'analytics.read',
    'audit.read',
  ],
};

async function seedRbac() {
  console.log('--- Starting Idempotent RBAC Seeding ---');

  // 1. Upsert Permissions
  const permissionMap = new Map<string, string>();
  for (const perm of PERMISSIONS) {
    const p = await prisma.permission.upsert({
      where: { name: perm.name },
      update: { module: perm.module, description: perm.description },
      create: perm,
    });
    permissionMap.set(p.name, p.id);
  }
  console.log(`✅ Upserted ${permissionMap.size} permissions`);

  // 2. Upsert Roles and RolePermissions
  for (const roleDef of ROLES) {
    const role = await prisma.role.upsert({
      where: { name: roleDef.name },
      update: { description: roleDef.description, isSystem: roleDef.isSystem },
      create: roleDef,
    });

    const targetPermNames = ROLE_PERMISSIONS[role.name] || [];
    const permissionIdsToAssign: string[] = targetPermNames.includes('*')
      ? Array.from(permissionMap.values())
      : targetPermNames.map(name => permissionMap.get(name)).filter(Boolean) as string[];

    for (const permId of permissionIdsToAssign) {
      await prisma.rolePermission.upsert({
        where: {
          roleId_permissionId: {
            roleId: role.id,
            permissionId: permId,
          },
        },
        update: {},
        create: {
          roleId: role.id,
          permissionId: permId,
        },
      });
    }
    console.log(`✅ Configured role: ${role.name} (${permissionIdsToAssign.length} permissions)`);
  }

  // 3. Assign existing ADMIN users the SUPER_ADMIN role
  const adminUsers = await prisma.user.findMany({
    where: { role: 'ADMIN' },
    select: { id: true, email: true },
  });

  const superAdminRole = await prisma.role.findUnique({ where: { name: 'SUPER_ADMIN' } });

  if (superAdminRole && adminUsers.length > 0) {
    for (const admin of adminUsers) {
      await prisma.userRole.upsert({
        where: {
          userId_roleId: {
            userId: admin.id,
            roleId: superAdminRole.id,
          },
        },
        update: {},
        create: {
          userId: admin.id,
          roleId: superAdminRole.id,
        },
      });
      console.log(`✅ Assigned SUPER_ADMIN role to existing admin user: ${admin.email}`);
    }
  }

  // 4. Seed initial Feature Flags
  const flags = [
    { key: 'enable_ai_career_readiness', name: 'AI Career Readiness Scoring', isEnabled: true },
    { key: 'enable_instant_alumni_referrals', name: 'Instant Alumni Referrals', isEnabled: true },
    { key: 'enable_scholarship_auto_match', name: 'Automated Scholarship Matching', isEnabled: true },
    { key: 'strict_moderation_queue', name: 'Strict Content Pre-Moderation', isEnabled: false },
  ];

  for (const flag of flags) {
    await prisma.featureFlag.upsert({
      where: { key: flag.key },
      update: { name: flag.name },
      create: flag,
    });
  }
  console.log(`✅ Seeded ${flags.length} system feature flags`);

  console.log('--- RBAC Seeding Completed Successfully ---');
}

seedRbac()
  .catch((err) => {
    console.error('RBAC Seeding Failed:', err);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
