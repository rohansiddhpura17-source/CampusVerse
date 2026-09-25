import { prisma } from './prisma.service';

const SENSITIVE_KEYS = new Set([
  'password',
  'passwordhash',
  'token',
  'secret',
  'jwt',
  'apikey',
  'authorization',
  'refreshtoken',
  'otp',
  'otphash',
  'databaseurl',
  'supabasekey',
  'geminiapikey'
]);

export function sanitizeAuditData(data: any): any {
  if (!data || typeof data !== 'object') return data;
  if (Array.isArray(data)) return data.map(sanitizeAuditData);
  const sanitized: Record<string, any> = {};
  for (const [key, value] of Object.entries(data)) {
    if (SENSITIVE_KEYS.has(key.toLowerCase())) {
      sanitized[key] = '[REDACTED]';
    } else if (typeof value === 'object' && value !== null) {
      sanitized[key] = sanitizeAuditData(value);
    } else {
      sanitized[key] = value;
    }
  }
  return sanitized;
}

export interface AuditLogPayload {
  oldState?: Record<string, any>;
  newState?: Record<string, any>;
  details?: Record<string, any>;
  reason?: string;
  ipAddress?: string;
  userAgent?: string;
  [key: string]: any;
}

export async function logAudit(
  actorId: string,
  action: string,
  targetType: string,
  targetId: string,
  metadata?: AuditLogPayload | Record<string, any>
): Promise<void> {
  try {
    const sanitized = metadata ? sanitizeAuditData(metadata) : null;
    await prisma.auditLog.create({
      data: {
        actorId,
        action,
        targetType,
        targetId,
        metadata: sanitized ? JSON.stringify(sanitized) : null,
      },
    });
  } catch (error) {
    console.error('Failed to write audit log:', error);
  }
}

