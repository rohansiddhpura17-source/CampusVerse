import * as crypto from 'crypto';
import { prisma } from './prisma.service';
import { env } from '../config/env';
import { emailService } from './email.service';

export interface OtpResult {
  success: boolean;
  message: string;
  cooldownRemainingSeconds?: number;
}

export class OtpService {
  /**
   * Generates a cryptographically secure 6-digit OTP.
   */
  public generateSecureOtp(): string {
    return crypto.randomInt(100000, 1000000).toString();
  }

  /**
   * Computes a deterministic SHA-256 HMAC-like hash of the OTP.
   */
  public hashOtp(otp: string): string {
    return crypto.createHash('sha256').update(otp + env.JWT_SECRET).digest('hex');
  }

  /**
   * Constant-time verification of entered OTP against stored hash.
   */
  public verifyHash(enteredOtp: string, storedHash: string): boolean {
    const computedHash = this.hashOtp(enteredOtp);
    const bufA = Buffer.from(computedHash, 'hex');
    const bufB = Buffer.from(storedHash, 'hex');
    if (bufA.length !== bufB.length) return false;
    return crypto.timingSafeEqual(bufA, bufB);
  }

  /**
   * Requests, persists, and delivers an OTP to the given email address.
   */
  public async sendOtp(
    email: string,
    purpose: 'EMAIL_VERIFICATION' | 'PASSWORD_RESET' | 'ACCOUNT_RECOVERY'
  ): Promise<OtpResult> {
    const normalizedEmail = email.trim().toLowerCase();

    // 1. Check Resend Cooldown
    const latestOtp = await prisma.otpToken.findFirst({
      where: {
        email: normalizedEmail,
        purpose
      },
      orderBy: { createdAt: 'desc' }
    });

    if (latestOtp) {
      const elapsedSeconds = Math.floor((Date.now() - latestOtp.createdAt.getTime()) / 1000);
      if (elapsedSeconds < env.OTP_RESEND_COOLDOWN_SECONDS) {
        const cooldownRemaining = env.OTP_RESEND_COOLDOWN_SECONDS - elapsedSeconds;
        return {
          success: false,
          message: `Please wait ${cooldownRemaining} seconds before requesting a new code.`,
          cooldownRemainingSeconds: cooldownRemaining
        };
      }
    }

    // 2. Invalidate previous active OTPs for the same user and purpose
    await prisma.otpToken.updateMany({
      where: {
        email: normalizedEmail,
        purpose,
        isUsed: false
      },
      data: {
        isUsed: true,
        usedAt: new Date()
      }
    });

    // 3. Generate Cryptographically Secure OTP
    const rawOtp = this.generateSecureOtp();
    const otpHash = this.hashOtp(rawOtp);
    const expiresAt = new Date(Date.now() + env.OTP_EXPIRY_MINUTES * 60 * 1000);

    // 4. Persist Hashed OTP Record
    await prisma.otpToken.create({
      data: {
        email: normalizedEmail,
        otpHash,
        purpose,
        expiresAt,
        attemptCount: 0,
        maxAttempts: env.OTP_MAX_ATTEMPTS,
        isUsed: false
      }
    });

    // 5. Deliver via Email Service
    const delivery = await emailService.sendOtpEmail(normalizedEmail, rawOtp, purpose);

    if (!delivery.success) {
      return {
        success: false,
        message: delivery.error || 'Failed to deliver verification code email.'
      };
    }

    return {
      success: true,
      message: 'Verification code sent successfully.'
    };
  }

  /**
   * Verifies an entered OTP for the given email and purpose.
   */
  public async verifyOtp(
    email: string,
    otp: string,
    purpose: 'EMAIL_VERIFICATION' | 'PASSWORD_RESET' | 'ACCOUNT_RECOVERY'
  ): Promise<OtpResult> {
    const normalizedEmail = email.trim().toLowerCase();
    const trimmedOtp = otp.trim();

    if (!trimmedOtp || trimmedOtp.length < 6 || trimmedOtp.length > 64) {
      return {
        success: false,
        message: 'Please enter a valid verification code.'
      };
    }

    // 1. Fetch active OTP record
    const activeToken = await prisma.otpToken.findFirst({
      where: {
        email: normalizedEmail,
        purpose,
        isUsed: false
      },
      orderBy: { createdAt: 'desc' }
    });

    if (!activeToken) {
      return {
        success: false,
        message: 'No active verification code found. Please request a new code.'
      };
    }

    // 2. Check Expiration
    if (new Date() > activeToken.expiresAt) {
      await prisma.otpToken.update({
        where: { id: activeToken.id },
        data: { isUsed: true, usedAt: new Date() }
      });
      return {
        success: false,
        message: 'Verification code has expired. Please request a new code.'
      };
    }

    // 3. Check Attempt Limit
    if (activeToken.attemptCount >= activeToken.maxAttempts) {
      await prisma.otpToken.update({
        where: { id: activeToken.id },
        data: { isUsed: true, usedAt: new Date() }
      });
      return {
        success: false,
        message: 'Maximum verification attempts exceeded. Please request a new code.'
      };
    }

    // 4. Verify Hash
    const isValid = this.verifyHash(trimmedOtp, activeToken.otpHash);

    if (!isValid) {
      const newAttemptCount = activeToken.attemptCount + 1;
      const isMaxExceeded = newAttemptCount >= activeToken.maxAttempts;

      await prisma.otpToken.update({
        where: { id: activeToken.id },
        data: {
          attemptCount: newAttemptCount,
          ...(isMaxExceeded && { isUsed: true, usedAt: new Date() })
        }
      });

      return {
        success: false,
        message: isMaxExceeded
          ? 'Maximum verification attempts exceeded. Please request a new code.'
          : 'Invalid verification code. Please check and try again.'
      };
    }

    // 5. Successful Verification — Mark OTP as permanently used
    await prisma.otpToken.update({
      where: { id: activeToken.id },
      data: {
        isUsed: true,
        usedAt: new Date()
      }
    });

    // 6. If EMAIL_VERIFICATION, mark user as verified in database
    if (purpose === 'EMAIL_VERIFICATION') {
      await prisma.user.updateMany({
        where: { email: normalizedEmail },
        data: { isEmailVerified: true }
      });
    }

    return {
      success: true,
      message: 'Verification successful.'
    };
  }

  /**
   * Resets password using an OTP verified for PASSWORD_RESET purpose.
   */
  public async resetPasswordWithOtp(
    email: string,
    otp: string,
    newPasswordHash: string
  ): Promise<OtpResult> {
    const verification = await this.verifyOtp(email, otp, 'PASSWORD_RESET');
    if (!verification.success) {
      return verification;
    }

    const normalizedEmail = email.trim().toLowerCase();
    const user = await prisma.user.findUnique({
      where: { email: normalizedEmail }
    });

    if (!user) {
      return {
        success: false,
        message: 'User account not found.'
      };
    }

    await prisma.user.update({
      where: { id: user.id },
      data: {
        passwordHash: newPasswordHash,
        securitySettings: {
          upsert: {
            create: { lastPasswordChange: new Date() },
            update: { lastPasswordChange: new Date() }
          }
        }
      }
    });

    return {
      success: true,
      message: 'Password reset successfully. You can now login with your new password.'
    };
  }
}

export const otpService = new OtpService();
