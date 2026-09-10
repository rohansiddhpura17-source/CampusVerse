import { prisma } from './prisma.service';

export async function logAudit(
  actorId: string,
  action: string,
  targetType: string,
  targetId: string,
  metadata?: Record<string, any>
): Promise<void> {
  try {
    await prisma.auditLog.create({
      data: {
        actorId,
        action,
        targetType,
        targetId,
        metadata: metadata ? JSON.stringify(metadata) : null
      }
    });
  } catch (error) {
    console.error('Failed to write audit log:', error);
  }
}
