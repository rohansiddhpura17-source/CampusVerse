import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { getUserPermissions } from '../middleware/rbac.middleware';
import { sendSuccess, sendError } from '../utils/response';

// -----------------------------------------------------------------------------
// Validation Schemas
// -----------------------------------------------------------------------------

export const createRoleSchema = z.object({
  name: z.string().min(2).max(50).regex(/^[A-Z0-9_]+$/, 'Role name must be uppercase alphanumeric and underscores (e.g. LAB_ADMIN)'),
  description: z.string().max(255).optional(),
  permissionIds: z.array(z.string()).default([]),
});

export const assignRoleSchema = z.object({
  roleId: z.string().min(1, 'Role ID is required'),
});

export const updateRolePermissionsSchema = z.object({
  permissionIds: z.array(z.string()),
});

// -----------------------------------------------------------------------------
// Helper: Check if user is SUPER_ADMIN
// -----------------------------------------------------------------------------
async function isActorSuperAdmin(userId: string): Promise<boolean> {
  const { roles } = await getUserPermissions(userId);
  return roles.includes('SUPER_ADMIN');
}

// -----------------------------------------------------------------------------
// 1. Roles Management
// -----------------------------------------------------------------------------

export async function getAdminRoles(req: Request, res: Response): Promise<void> {
  try {
    const roles = await prisma.role.findMany({
      orderBy: { createdAt: 'asc' },
      include: {
        permissions: {
          include: {
            permission: true,
          },
        },
        userRoles: {
          include: {
            user: {
              select: {
                id: true,
                email: true,
                role: true,
                isActive: true,
                profile: {
                  select: { fullName: true, avatarUrl: true },
                },
              },
            },
          },
        },
      },
    });

    const formatted = roles.map((r) => ({
      id: r.id,
      name: r.name,
      description: r.description,
      isSystem: r.isSystem,
      userCount: r.userRoles.length,
      permissionCount: r.permissions.length,
      permissions: r.permissions.map((p) => ({
        id: p.permission.id,
        name: p.permission.name,
        module: p.permission.module,
        description: p.permission.description,
      })),
      users: r.userRoles.map((ur) => ({
        id: ur.user.id,
        email: ur.user.email,
        fullName: ur.user.profile?.fullName || ur.user.email,
        isActive: ur.user.isActive,
        assignedAt: ur.assignedAt,
      })),
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
    }));

    sendSuccess(res, formatted);
  } catch (error: any) {
    sendError(res, error.message || 'Failed to fetch roles', 500);
  }
}

export async function createAdminRole(req: Request, res: Response): Promise<void> {
  try {
    const { name, description, permissionIds } = req.body;
    const actorId = req.user!.userId;

    const normalizedName = name.toUpperCase().trim();
    if (['SUPER_ADMIN', 'ADMIN', 'MODERATOR'].includes(normalizedName)) {
      sendError(res, 'Cannot create system-reserved role names.', 400, 'RESERVED_ROLE_NAME');
      return;
    }

    const existing = await prisma.role.findUnique({
      where: { name: normalizedName },
    });
    if (existing) {
      sendError(res, `Role '${normalizedName}' already exists.`, 409, 'ROLE_EXISTS');
      return;
    }

    const role = await prisma.role.create({
      data: {
        name: normalizedName,
        description: description || null,
        isSystem: false,
        permissions: {
          create: permissionIds.map((pId: string) => ({
            permissionId: pId,
          })),
        },
      },
      include: {
        permissions: {
          include: { permission: true },
        },
      },
    });

    await logAudit(actorId, 'ADMIN_CREATED_ROLE', 'ROLE', role.id, {
      name: role.name,
      permissionCount: permissionIds.length,
    });

    sendSuccess(res, role, 'Role created successfully', 201);
  } catch (error: any) {
    sendError(res, error.message || 'Failed to create role', 500);
  }
}

// -----------------------------------------------------------------------------
// 2. Permissions Directory
// -----------------------------------------------------------------------------

export async function getAdminPermissions(req: Request, res: Response): Promise<void> {
  try {
    const permissions = await prisma.permission.findMany({
      orderBy: [{ module: 'asc' }, { name: 'asc' }],
    });

    // Group permissions by module
    const grouped: Record<string, typeof permissions> = {};
    for (const p of permissions) {
      if (!grouped[p.module]) {
        grouped[p.module] = [];
      }
      grouped[p.module].push(p);
    }

    sendSuccess(res, {
      total: permissions.length,
      permissions,
      modules: grouped,
    });
  } catch (error: any) {
    sendError(res, error.message || 'Failed to fetch permissions', 500);
  }
}

// -----------------------------------------------------------------------------
// 3. User Role Assignment & Revocation Safeguards
// -----------------------------------------------------------------------------

export async function assignUserRole(req: Request, res: Response): Promise<void> {
  try {
    const targetUserId = req.params.id;
    const { roleId } = req.body;
    const actorId = req.user!.userId;

    // RULE 1: Self-role assignment / escalation prohibited
    if (targetUserId === actorId) {
      sendError(
        res,
        'Privilege Escalation Guard: Administrators cannot assign or escalate their own roles.',
        403,
        'CANNOT_MODIFY_OWN_ROLES'
      );
      return;
    }

    const role = await prisma.role.findUnique({
      where: { id: roleId },
    });
    if (!role) {
      sendError(res, 'Role not found.', 404, 'NOT_FOUND');
      return;
    }

    // RULE 2: Only SUPER_ADMIN can assign SUPER_ADMIN
    const isSuperAdmin = await isActorSuperAdmin(actorId);
    if (role.name === 'SUPER_ADMIN' && !isSuperAdmin) {
      sendError(
        res,
        'Unauthorized role escalation: Only existing SUPER_ADMINs can grant the SUPER_ADMIN role.',
        403,
        'UNAUTHORIZED_ROLE_ESCALATION'
      );
      return;
    }

    const targetUser = await prisma.user.findUnique({
      where: { id: targetUserId },
      include: {
        userRoles: {
          where: { roleId },
        },
      },
    });

    if (!targetUser) {
      sendError(res, 'Target user not found.', 404, 'NOT_FOUND');
      return;
    }

    if (targetUser.userRoles.length > 0) {
      sendError(res, `User already has the '${role.name}' role assigned.`, 409, 'ROLE_ALREADY_ASSIGNED');
      return;
    }

    // Assign the role in transaction and ensure admin authorization if administrative role
    const isAdminType = ['SUPER_ADMIN', 'ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'CONTENT_MANAGER', 'ANALYTICS_ADMIN'].includes(role.name);

    await prisma.$transaction([
      prisma.userRole.create({
        data: {
          userId: targetUserId,
          roleId: role.id,
          assignedById: actorId,
        },
      }),
      ...(isAdminType
        ? [
            prisma.user.update({
              where: { id: targetUserId },
              data: {
                role: role.name === 'SUPER_ADMIN' || role.name === 'ADMIN' ? 'ADMIN' : targetUser.role,
                isAdminAuthorized: true,
              },
            }),
          ]
        : []),
    ]);

    await logAudit(actorId, 'ADMIN_ASSIGNED_ROLE', 'USER', targetUserId, {
      roleId: role.id,
      roleName: role.name,
      targetUserEmail: targetUser.email,
    });

    sendSuccess(res, { roleId: role.id, roleName: role.name }, `Role '${role.name}' assigned successfully.`);
  } catch (error: any) {
    sendError(res, error.message || 'Failed to assign role', 500);
  }
}

export async function revokeUserRole(req: Request, res: Response): Promise<void> {
  try {
    const targetUserId = req.params.id;
    const roleId = req.params.roleId;
    const actorId = req.user!.userId;

    // RULE 1: Self-role mutation prohibited
    if (targetUserId === actorId) {
      sendError(
        res,
        'Privilege Escalation Guard: Administrators cannot revoke or alter their own roles.',
        403,
        'CANNOT_MODIFY_OWN_ROLES'
      );
      return;
    }

    const role = await prisma.role.findUnique({
      where: { id: roleId },
    });
    if (!role) {
      sendError(res, 'Role not found.', 404, 'NOT_FOUND');
      return;
    }

    // RULE 2: Only SUPER_ADMIN can manage SUPER_ADMIN assignments
    const isSuperAdmin = await isActorSuperAdmin(actorId);
    if (role.name === 'SUPER_ADMIN' && !isSuperAdmin) {
      sendError(
        res,
        'Unauthorized action: Only SUPER_ADMINs can revoke the SUPER_ADMIN role.',
        403,
        'UNAUTHORIZED_ROLE_ESCALATION'
      );
      return;
    }

    // RULE 3: Safeguard against revoking the last active SUPER_ADMIN
    if (role.name === 'SUPER_ADMIN') {
      const activeSuperAdmins = await prisma.userRole.count({
        where: {
          roleId: role.id,
          user: {
            isActive: true,
          },
        },
      });

      if (activeSuperAdmins <= 1) {
        sendError(
          res,
          'Critical System Safeguard: Cannot revoke the last active SUPER_ADMIN in the system.',
          403,
          'CANNOT_DELETE_LAST_SUPER_ADMIN'
        );
        return;
      }
    }

    const existingAssignment = await prisma.userRole.findFirst({
      where: {
        userId: targetUserId,
        roleId: role.id,
      },
    });

    if (!existingAssignment) {
      sendError(res, 'Role assignment not found for this user.', 404, 'NOT_FOUND');
      return;
    }

    await prisma.userRole.delete({
      where: { id: existingAssignment.id },
    });

    await logAudit(actorId, 'ADMIN_REVOKED_ROLE', 'USER', targetUserId, {
      roleId: role.id,
      roleName: role.name,
    });

    sendSuccess(res, null, `Role '${role.name}' revoked successfully.`);
  } catch (error: any) {
    sendError(res, error.message || 'Failed to revoke role', 500);
  }
}

// -----------------------------------------------------------------------------
// 4. Role Permission Modification Safeguards
// -----------------------------------------------------------------------------

export async function updateRolePermissions(req: Request, res: Response): Promise<void> {
  try {
    const roleId = req.params.id;
    const { permissionIds } = req.body;
    const actorId = req.user!.userId;

    const isSuperAdmin = await isActorSuperAdmin(actorId);
    if (!isSuperAdmin) {
      sendError(
        res,
        'Unauthorized: Only SUPER_ADMINs are authorized to modify role permissions.',
        403,
        'UNAUTHORIZED_PERMISSION_MODIFICATION'
      );
      return;
    }

    const role = await prisma.role.findUnique({
      where: { id: roleId },
      include: {
        permissions: {
          select: { permissionId: true },
        },
      },
    });

    if (!role) {
      sendError(res, 'Role not found.', 404, 'NOT_FOUND');
      return;
    }

    // SUPER_ADMIN permissions cannot be truncated
    if (role.name === 'SUPER_ADMIN') {
      sendError(
        res,
        'SUPER_ADMIN permissions are immutable and automatically include all system privileges.',
        403,
        'SUPER_ADMIN_PERMISSIONS_IMMUTABLE'
      );
      return;
    }

    const oldPermIds = role.permissions.map((p) => p.permissionId);

    // Atomically replace permissions
    await prisma.$transaction([
      prisma.rolePermission.deleteMany({
        where: { roleId },
      }),
      prisma.rolePermission.createMany({
        data: permissionIds.map((pId: string) => ({
          roleId,
          permissionId: pId,
        })),
      }),
    ]);

    await logAudit(actorId, 'ADMIN_UPDATED_ROLE_PERMISSIONS', 'ROLE', roleId, {
      roleName: role.name,
      oldPermissionCount: oldPermIds.length,
      newPermissionCount: permissionIds.length,
    });

    sendSuccess(res, { roleId, count: permissionIds.length }, 'Role permissions updated successfully.');
  } catch (error: any) {
    sendError(res, error.message || 'Failed to update role permissions', 500);
  }
}

// -----------------------------------------------------------------------------
// 5. Inspect User Roles and Effective Permissions
// -----------------------------------------------------------------------------

export async function getUserPermissionsDetails(req: Request, res: Response): Promise<void> {
  try {
    const targetUserId = req.params.id;
    const user = await prisma.user.findUnique({
      where: { id: targetUserId },
      select: {
        id: true,
        email: true,
        role: true,
        isActive: true,
        isAdminAuthorized: true,
        profile: {
          select: {
            fullName: true,
            avatarUrl: true,
          },
        },
      },
    });

    if (!user) {
      sendError(res, 'User not found.', 404, 'NOT_FOUND');
      return;
    }

    const { roles, permissions } = await getUserPermissions(targetUserId);

    sendSuccess(res, {
      user,
      roles,
      permissions,
      isSuperAdmin: roles.includes('SUPER_ADMIN'),
    });
  } catch (error: any) {
    sendError(res, error.message || 'Failed to fetch user permissions', 500);
  }
}
