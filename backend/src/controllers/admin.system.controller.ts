import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { sendSuccess, sendError } from '../utils/response';

export const updateSystemSettingsSchema = z.object({
  maintenanceMode: z.boolean().optional(),
  allowNewRegistrations: z.boolean().optional(),
  autoModeration: z.boolean().optional(),
  strictVerification: z.boolean().optional(),
  require2FAForAdmins: z.boolean().optional(),
});

export const updateFeatureFlagSchema = z.object({
  isEnabled: z.boolean(),
  targetRoles: z.string().nullable().optional(),
});

// -----------------------------------------------------------------------------
// 1. System Settings & Server Telemetry
// -----------------------------------------------------------------------------

export async function getAdminSystemSettings(req: Request, res: Response): Promise<void> {
  try {
    let settings = await prisma.platformSettings.findUnique({
      where: { id: 'GLOBAL_SETTINGS' },
    });

    if (!settings) {
      settings = await prisma.platformSettings.create({
        data: {
          id: 'GLOBAL_SETTINGS',
          maintenanceMode: false,
          allowNewRegistrations: true,
          autoModeration: true,
          strictVerification: true,
          require2FAForAdmins: true,
          systemVersion: '2.5.0-hardened',
        },
      });
    }

    const memory = process.memoryUsage();

    sendSuccess(res, {
      publicConfig: {
        id: settings.id,
        maintenanceMode: settings.maintenanceMode,
        allowNewRegistrations: settings.allowNewRegistrations,
        autoModeration: settings.autoModeration,
        strictVerification: settings.strictVerification,
        require2FAForAdmins: settings.require2FAForAdmins,
        systemVersion: settings.systemVersion,
        lastBackup: settings.lastBackup,
        updatedAt: settings.updatedAt,
      },
      serverTelemetry: {
        nodeVersion: process.version,
        platform: process.platform,
        uptimeSeconds: Math.floor(process.uptime()),
        memoryRssMb: Math.round(memory.rss / (1024 * 1024)),
        memoryHeapUsedMb: Math.round(memory.heapUsed / (1024 * 1024)),
        environment: process.env.NODE_ENV || 'production',
        databaseEngine: 'PostgreSQL 15 (Supabase Managed Cloud)',
        databaseRegion: 'ap-south-1 (Mumbai)',
        prismaClientVersion: '5.22.0',
      },
    });
  } catch (error: any) {
    sendError(res, error.message || 'Failed to fetch system settings', 500);
  }
}

export async function updateAdminSystemSettings(req: Request, res: Response): Promise<void> {
  try {
    const actorId = req.user!.userId;
    const body = req.body;

    const oldSettings = await prisma.platformSettings.findUnique({
      where: { id: 'GLOBAL_SETTINGS' },
    });

    const updated = await prisma.platformSettings.upsert({
      where: { id: 'GLOBAL_SETTINGS' },
      update: {
        ...body,
        updatedAt: new Date(),
      },
      create: {
        id: 'GLOBAL_SETTINGS',
        ...body,
      },
    });

    await logAudit(actorId, 'ADMIN_UPDATED_SYSTEM_SETTINGS', 'SYSTEM', 'GLOBAL_SETTINGS', {
      oldState: oldSettings,
      newState: updated,
    });

    sendSuccess(res, updated, 'System settings updated successfully.');
  } catch (error: any) {
    sendError(res, error.message || 'Failed to update system settings', 500);
  }
}

// -----------------------------------------------------------------------------
// 2. Feature Flags Management
// -----------------------------------------------------------------------------

export async function getAdminFeatureFlags(req: Request, res: Response): Promise<void> {
  try {
    const flags = await prisma.featureFlag.findMany({
      orderBy: { key: 'asc' },
    });

    sendSuccess(res, flags);
  } catch (error: any) {
    sendError(res, error.message || 'Failed to fetch feature flags', 500);
  }
}

export async function toggleAdminFeatureFlag(req: Request, res: Response): Promise<void> {
  try {
    const { key } = req.params;
    const { isEnabled, targetRoles } = req.body;
    const actorId = req.user!.userId;

    const existing = await prisma.featureFlag.findUnique({
      where: { key },
    });

    if (!existing) {
      sendError(res, `Feature flag '${key}' not found.`, 404, 'NOT_FOUND');
      return;
    }

    const updated = await prisma.featureFlag.update({
      where: { key },
      data: {
        isEnabled,
        targetRoles: targetRoles !== undefined ? targetRoles : existing.targetRoles,
      },
    });

    await logAudit(actorId, 'ADMIN_TOGGLED_FEATURE_FLAG', 'FEATURE_FLAG', existing.id, {
      key,
      oldValue: existing.isEnabled,
      newValue: isEnabled,
      targetRoles: updated.targetRoles,
    });

    sendSuccess(res, updated, `Feature flag '${key}' set to ${isEnabled ? 'ENABLED' : 'DISABLED'}.`);
  } catch (error: any) {
    sendError(res, error.message || 'Failed to toggle feature flag', 500);
  }
}
