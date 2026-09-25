import crypto from 'crypto';
import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { hashPassword, comparePassword } from '../utils/password';
import { signToken, revokeToken } from '../utils/jwt';
import { sendSuccess, sendError } from '../utils/response';
import { otpService } from '../services/otp.service';
import { getUserPermissions } from '../middleware/rbac.middleware';
import { logAudit } from '../services/audit.service';

export const registerSchema = z.object({
  name: z.string().min(1, 'Full name is required').max(100),
  email: z.string().email('Valid email address is required').max(150),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters long')
    .regex(/^(?=.*[A-Za-z])(?=.*\d)/, 'Password must contain both letters and numbers'),
  role: z.enum(['ASPIRANT', 'STUDENT', 'ALUMNI', 'ADMIN']).default('STUDENT')
});

export const loginSchema = z.object({
  email: z.string().email('Valid email address is required'),
  password: z.string().min(1, 'Password is required')
});

export const sendOtpSchema = z.object({
  email: z.string().email('Valid email address is required'),
  purpose: z
    .enum(['EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ACCOUNT_RECOVERY'])
    .default('EMAIL_VERIFICATION')
});

export const verifyOtpSchema = z.object({
  email: z.string().email('Valid email address is required'),
  otp: z.string().length(6, 'Verification code must be 6 digits'),
  purpose: z
    .enum(['EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ACCOUNT_RECOVERY'])
    .default('EMAIL_VERIFICATION')
});

export const forgotPasswordSchema = z.object({
  email: z.string().email('Valid email address is required')
});

export const resetPasswordSchema = z.object({
  email: z.string().email('Valid email address is required'),
  otp: z.string().min(6, 'Verification code must be at least 6 characters').max(64, 'Verification code must not exceed 64 characters'),
  newPassword: z
    .string()
    .min(8, 'Password must be at least 8 characters long')
    .regex(/^(?=.*[A-Za-z])(?=.*\d)/, 'Password must contain both letters and numbers')
});

export async function register(req: Request, res: Response): Promise<void> {
  try {
    const { name, email, password, role } = req.body;
    const normalizedEmail = email.trim().toLowerCase();

    const existingUser = await prisma.user.findUnique({
      where: { email: normalizedEmail }
    });

    if (existingUser) {
      sendError(res, 'An account with this email address already exists.', 409, 'EMAIL_EXISTS');
      return;
    }

    const passwordHash = await hashPassword(password);
    const isAdminAuthorized = false;

    const newUser = await prisma.user.create({
      data: {
        email: normalizedEmail,
        passwordHash,
        role: role.toUpperCase(),
        isEmailVerified: false,
        isAdminAuthorized,
        isActive: true,
        profile: {
          create: {
            fullName: name.trim()
          }
        },
        privacySettings: { create: {} },
        securitySettings: { create: {} }
      },
      select: {
        id: true,
        email: true,
        role: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        createdAt: true,
        profile: {
          select: {
            id: true,
            fullName: true,
            bio: true,
            avatarUrl: true
          }
        }
      }
    });

    // Automatically dispatch secure verification OTP email
    await otpService.sendOtp(normalizedEmail, 'EMAIL_VERIFICATION').catch(() => {});

    const token = signToken({
      userId: newUser.id,
      email: newUser.email,
      role: newUser.role,
      isAdminAuthorized: newUser.isAdminAuthorized
    });

    sendSuccess(
      res,
      {
        token,
        user: {
          userId: newUser.id,
          email: newUser.email,
          name: newUser.profile?.fullName || name,
          role: newUser.role,
          isEmailVerified: newUser.isEmailVerified,
          isAdminAuthorized: newUser.isAdminAuthorized
        }
      },
      'Account registered successfully. Verification code sent to email.',
      201
    );
  } catch (error: any) {
    console.error('Register error:', error);
    const isDb = error?.name === 'PrismaClientInitializationError' || error?.message?.includes('ENOTFOUND') || error?.message?.includes('database');
    const msg = isDb
      ? 'Database service is currently unreachable or paused. Please restore the project in the Supabase console.'
      : (error?.message || 'An error occurred during registration.');
    sendError(res, msg, isDb ? 503 : 500, isDb ? 'DATABASE_UNAVAILABLE' : 'REGISTRATION_ERROR');
  }
}

export async function login(req: Request, res: Response): Promise<void> {
  try {
    const { email, password } = req.body;
    const normalizedEmail = email.trim().toLowerCase();

    const user = await prisma.user.findUnique({
      where: { email: normalizedEmail },
      include: {
        profile: {
          select: { fullName: true }
        }
      }
    });

    if (!user || !user.isActive) {
      sendError(res, 'Invalid email or password.', 401, 'INVALID_CREDENTIALS');
      return;
    }

    const isPasswordValid = await comparePassword(password, user.passwordHash);

    if (!isPasswordValid) {
      sendError(res, 'Invalid email or password.', 401, 'INVALID_CREDENTIALS');
      return;
    }

    const { roles, permissions } = await getUserPermissions(user.id);
    const isAdministrative =
      user.role === 'ADMIN' ||
      roles.some((r) =>
        ['SUPER_ADMIN', 'ADMIN', 'MODERATOR', 'CONTENT_MANAGER', 'SUPPORT_ADMIN', 'ANALYTICS_ADMIN'].includes(r)
      );

    const jti = crypto.randomUUID();
    const token = signToken({
      userId: user.id,
      email: user.email,
      role: user.role,
      isAdminAuthorized: user.isAdminAuthorized,
      sessionVersion: user.sessionVersion,
      jti,
    });

    if (isAdministrative) {
      const forwarded = req.headers['x-forwarded-for'];
      const ipAddress = typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : req.ip || '127.0.0.1';
      const userAgent = req.headers['user-agent'] || 'Unknown Browser';
      const deviceName = userAgent.includes('Mac')
        ? 'macOS Web Client'
        : userAgent.includes('Windows')
        ? 'Windows Web Client'
        : userAgent.includes('Android')
        ? 'Android App'
        : userAgent.includes('iPhone')
        ? 'iOS App'
        : 'Web Client';
      const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

      await prisma.adminSession.create({
        data: {
          userId: user.id,
          tokenId: jti,
          deviceName,
          ipAddress,
          userAgent,
          expiresAt,
        },
      });

      await logAudit(user.id, 'ADMIN_LOGIN', 'USER', user.id, {
        ipAddress,
        userAgent,
        deviceName,
        roles,
      }).catch((e) => console.error('Failed to log audit ADMIN_LOGIN:', e));
    }

    sendSuccess(
      res,
      {
        token,
        user: {
          userId: user.id,
          email: user.email,
          name: user.profile?.fullName || '',
          role: user.role,
          roles,
          permissions,
          isEmailVerified: user.isEmailVerified,
          isAdminAuthorized: user.isAdminAuthorized
        }
      },
      'Login successful'
    );
  } catch (error: any) {
    console.error('Login error:', error);
    const isDb = error?.name === 'PrismaClientInitializationError' || error?.message?.includes('ENOTFOUND') || error?.message?.includes('database');
    const msg = isDb
      ? 'Database service is currently unreachable or paused. Please restore the project in the Supabase console.'
      : (error?.message || 'An error occurred during login.');
    sendError(res, msg, isDb ? 503 : 500, isDb ? 'DATABASE_UNAVAILABLE' : 'LOGIN_ERROR');
  }
}

export async function logout(req: Request, res: Response): Promise<void> {
  try {
    const rawToken = req.token || (req.headers.authorization?.startsWith('Bearer ') ? req.headers.authorization.substring(7).trim() : null);
    if (rawToken) {
      revokeToken(rawToken);
    }
    const jti = req.jti;
    if (jti) {
      await prisma.adminSession.updateMany({
        where: { tokenId: jti, revokedAt: null },
        data: { revokedAt: new Date(), revokedReason: 'USER_LOGOUT' },
      });
    }
    if (req.user?.userId) {
      await logAudit(req.user.userId, 'ADMIN_LOGOUT', 'USER', req.user.userId, {
        ipAddress: req.ip,
        userAgent: req.headers['user-agent'],
      }).catch(() => {});
    }
    sendSuccess(res, { loggedOut: true }, 'Successfully logged out.');
  } catch (error: any) {
    sendSuccess(res, { loggedOut: true }, 'Successfully logged out.');
  }
}

export async function refreshToken(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      sendError(res, 'Unauthenticated request.', 401, 'UNAUTHORIZED');
      return;
    }

    const user = await prisma.user.findUnique({
      where: { id: req.user.userId },
      include: {
        profile: {
          select: { fullName: true }
        }
      }
    });

    if (!user || !user.isActive) {
      sendError(res, 'User account is inactive or not found.', 401, 'UNAUTHORIZED');
      return;
    }

    const { roles, permissions } = await getUserPermissions(user.id);
    const jti = crypto.randomUUID();
    const token = signToken({
      userId: user.id,
      email: user.email,
      role: user.role,
      isAdminAuthorized: user.isAdminAuthorized,
      sessionVersion: user.sessionVersion,
      jti,
    });

    sendSuccess(res, {
      token,
      user: {
        userId: user.id,
        email: user.email,
        name: user.profile?.fullName || '',
        role: user.role,
        roles,
        permissions,
        isEmailVerified: user.isEmailVerified,
        isAdminAuthorized: user.isAdminAuthorized
      }
    }, 'Token refreshed successfully');
  } catch (error: any) {
    console.error('RefreshToken error:', error);
    sendError(res, 'Unable to refresh session token.', 500, 'REFRESH_ERROR');
  }
}

export async function getCurrentUser(req: Request, res: Response): Promise<void> {
  try {
    if (!req.user) {
      sendError(res, 'Unauthenticated request.', 401, 'UNAUTHORIZED');
      return;
    }

    const user = await prisma.user.findUnique({
      where: { id: req.user.userId },
      select: {
        id: true,
        email: true,
        role: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        createdAt: true,
        profile: {
          select: {
            id: true,
            fullName: true,
            bio: true,
            avatarUrl: true,
            headline: true,
            phone: true,
            location: true,
            website: true,
            github: true,
            linkedin: true,
            aspirantProfile: true,
            studentProfile: {
              include: { institution: true }
            },
            alumniProfile: {
              include: { institution: true }
            },
            adminProfile: true
          }
        },
        mentorProfile: true
      }
    });

    if (!user) {
      sendError(res, 'User profile not found.', 404, 'NOT_FOUND');
      return;
    }

    const { roles, permissions } = await getUserPermissions(user.id);

    sendSuccess(res, {
      userId: user.id,
      email: user.email,
      name: user.profile?.fullName || '',
      role: user.role,
      roles,
      permissions,
      isEmailVerified: user.isEmailVerified,
      isAdminAuthorized: user.isAdminAuthorized,
      createdAt: user.createdAt,
      profile: user.profile,
      mentorProfile: user.mentorProfile
    });
  } catch (error: any) {
    console.error('GetCurrentUser error:', error);
    sendError(res, 'Error retrieving user profile.', 500, 'PROFILE_ERROR');
  }
}

export async function sendOtp(req: Request, res: Response): Promise<void> {
  try {
    const { email, purpose } = req.body;
    const normalizedEmail = email.trim().toLowerCase();

    const result = await otpService.sendOtp(normalizedEmail, purpose);

    if (!result.success) {
      if (result.cooldownRemainingSeconds) {
        sendError(res, result.message, 429, 'RATE_LIMITED');
        return;
      }
      sendError(res, result.message, 400, 'OTP_SEND_FAILED');
      return;
    }

    sendSuccess(res, { sent: true }, 'Verification code sent.');
  } catch (error: any) {
    console.error('SendOtp error:', error);
    sendError(res, 'Error dispatching verification code.', 500, 'OTP_ERROR');
  }
}

export async function verifyOtp(req: Request, res: Response): Promise<void> {
  try {
    const { email, otp, purpose } = req.body;
    const normalizedEmail = email.trim().toLowerCase();

    const result = await otpService.verifyOtp(normalizedEmail, otp, purpose);

    if (!result.success) {
      sendError(res, result.message, 400, 'INVALID_OTP');
      return;
    }

    sendSuccess(res, { isEmailVerified: true }, 'Verification successful.');
  } catch (error: any) {
    console.error('VerifyOtp error:', error);
    sendError(res, 'Error verifying code.', 500, 'VERIFY_ERROR');
  }
}

export async function forgotPassword(req: Request, res: Response): Promise<void> {
  try {
    const { email } = req.body;
    const normalizedEmail = email.trim().toLowerCase();

    const user = await prisma.user.findUnique({
      where: { email: normalizedEmail }
    });

    if (user) {
      await otpService.sendOtp(normalizedEmail, 'PASSWORD_RESET').catch(() => {});
    }

    sendSuccess(
      res,
      { sent: true },
      'If an account with this email exists, a password reset code has been sent.'
    );
  } catch (error: any) {
    console.error('ForgotPassword error:', error);
    sendError(res, 'Error processing password reset request.', 500, 'FORGOT_PASSWORD_ERROR');
  }
}

export async function resetPassword(req: Request, res: Response): Promise<void> {
  try {
    const { email, otp, newPassword } = req.body;
    const normalizedEmail = email.trim().toLowerCase();

    const newPasswordHash = await hashPassword(newPassword);
    const result = await otpService.resetPasswordWithOtp(normalizedEmail, otp, newPasswordHash);

    if (!result.success) {
      sendError(res, result.message, 400, 'PASSWORD_RESET_FAILED');
      return;
    }

    sendSuccess(res, { reset: true }, result.message);
  } catch (error: any) {
    console.error('ResetPassword error:', error);
    sendError(res, 'Error resetting password.', 500, 'RESET_PASSWORD_ERROR');
  }
}
