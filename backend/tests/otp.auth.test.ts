import request from 'supertest';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';
import { otpService } from '../src/services/otp.service';
import { emailService } from '../src/services/email.service';
import { env } from '../src/config/env';

describe('REAL PRODUCTION OTP & AUTH SECURITY SUITE', () => {
  const testEmail = `otp_test_${Date.now()}@campusverse.edu`;
  const testPassword = 'Password123';

  beforeAll(async () => {
    emailService.clearSentEmails();
  });

  afterAll(async () => {
    // Cleanup OTP test records
    await prisma.otpToken.deleteMany({ where: { email: { contains: 'otp_test_' } } });
    await prisma.user.deleteMany({ where: { email: { contains: 'otp_test_' } } });
  });

  // ---------------------------------------------------------------------------
  // 1. Cryptographic OTP Generation & Properties
  // ---------------------------------------------------------------------------
  describe('1. Cryptographic OTP Generation & Properties', () => {
    it('1 & 3: should generate cryptographically secure 6-digit OTPs', () => {
      for (let i = 0; i < 20; i++) {
        const otp = otpService.generateSecureOtp();
        expect(otp).toHaveLength(6);
        expect(/^\d{6}$/.test(otp)).toBe(true);
        const num = parseInt(otp, 10);
        expect(num).toBeGreaterThanOrEqual(100000);
        expect(num).toBeLessThan(1000000);
      }
    });

    it('2: should generate distinct unique OTPs across successive calls', () => {
      const otps = new Set<string>();
      for (let i = 0; i < 50; i++) {
        otps.add(otpService.generateSecureOtp());
      }
      expect(otps.size).toBeGreaterThan(45);
    });

    it('4 & 18: should hash OTPs deterministically and never store plaintext OTP', async () => {
      const rawOtp = '749201';
      const hash = otpService.hashOtp(rawOtp);
      expect(hash).toHaveLength(64); // SHA-256 hex length
      expect(hash).not.toContain(rawOtp);

      expect(otpService.verifyHash(rawOtp, hash)).toBe(true);
      expect(otpService.verifyHash('111111', hash)).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // 2. Registration Verification OTP Flow
  // ---------------------------------------------------------------------------
  describe('2. Registration Verification Flow & Security', () => {
    it('20, 16, 17, 18: should register user, send OTP email, and NOT expose OTP in response or db', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'OTP Test Aspirant',
          email: testEmail,
          password: testPassword,
          role: 'ASPIRANT'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.email).toBe(testEmail);
      expect(res.body.data.user.isEmailVerified).toBe(false);

      // 17. Verify response NEVER contains the OTP
      expect(JSON.stringify(res.body)).not.toMatch(/\b\d{6}\b/);

      // 16. Verify email was delivered to mock/email service
      const sent = emailService.getSentEmails();
      const lastEmail = sent.find(e => e.to === testEmail);
      expect(lastEmail).toBeDefined();
      expect(lastEmail?.subject).toContain('Email Verification Code');
      expect(lastEmail?.text).toContain('CampusVerse');
      expect(lastEmail?.text).toContain('Expiration:');

      // 18. Verify database record contains ONLY hash, NO plaintext OTP
      const dbRecord = await prisma.otpToken.findFirst({
        where: { email: testEmail, purpose: 'EMAIL_VERIFICATION' },
        orderBy: { createdAt: 'desc' }
      });
      expect(dbRecord).toBeDefined();
      expect(dbRecord?.otpHash).toHaveLength(64);
      expect(dbRecord?.isUsed).toBe(false);
    });

    it('12 & 13: should fail verification when wrong or missing OTP is provided', async () => {
      // Missing / invalid format
      const missingRes = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: testEmail, otp: '123', purpose: 'EMAIL_VERIFICATION' });
      expect(missingRes.status).toBe(400);

      // Wrong 6-digit code
      const wrongRes = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: testEmail, otp: '000000', purpose: 'EMAIL_VERIFICATION' });
      expect(wrongRes.status).toBe(400);
      expect(wrongRes.body.success).toBe(false);
    });

    it('11: should fail verification when wrong purpose is specified', async () => {
      // Find the generated OTP from email service to test purpose isolation
      const sent = emailService.getSentEmails();
      const lastEmail = sent.find(e => e.to === testEmail);
      const match = lastEmail?.text.match(/verification code:\s*(\d{6})/i);
      const rawOtp = match ? match[1] : '999999';

      const res = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: testEmail, otp: rawOtp, purpose: 'PASSWORD_RESET' });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
    });

    it('5 & 7: should verify valid OTP, mark as used, and update isEmailVerified', async () => {
      const sent = emailService.getSentEmails();
      const lastEmail = sent.find(e => e.to === testEmail);
      const match = lastEmail?.text.match(/verification code:\s*(\d{6})/i);
      expect(match).not.toBeNull();
      const rawOtp = match![1];

      const res = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: testEmail, otp: rawOtp, purpose: 'EMAIL_VERIFICATION' });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // Verify user in DB is now verified
      const user = await prisma.user.findUnique({ where: { email: testEmail } });
      expect(user?.isEmailVerified).toBe(true);

      // Verify OTP token is marked used
      const dbRecord = await prisma.otpToken.findFirst({
        where: { email: testEmail, purpose: 'EMAIL_VERIFICATION' },
        orderBy: { createdAt: 'desc' }
      });
      expect(dbRecord?.isUsed).toBe(true);
    });

    it('7: should reject reuse of already used OTP', async () => {
      const sent = emailService.getSentEmails();
      const lastEmail = sent.find(e => e.to === testEmail);
      const match = lastEmail?.text.match(/verification code:\s*(\d{6})/i);
      const rawOtp = match![1];

      const res = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: testEmail, otp: rawOtp, purpose: 'EMAIL_VERIFICATION' });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // 3. Resend, Invalidation & Cooldown
  // ---------------------------------------------------------------------------
  describe('3. Resend Protection, Cooldown & Invalidation', () => {
    it('10 & 14: should enforce resend cooldown and rate limiting', async () => {
      const resendEmail = `resend_cooldown_${Date.now()}_${Math.floor(Math.random() * 10000)}@campusverse.edu`;
      // 1. Initial send
      const firstRes = await request(app)
        .post('/api/v1/auth/send-otp')
        .send({ email: resendEmail, purpose: 'EMAIL_VERIFICATION' });
      expect(firstRes.status).toBe(200);

      // 2. Immediate second send (should be blocked by 60s cooldown)
      const secondRes = await request(app)
        .post('/api/v1/auth/send-otp')
        .send({ email: resendEmail, purpose: 'EMAIL_VERIFICATION' });
      expect(secondRes.status).toBe(429);
      expect(secondRes.body.error.message).toContain('Please wait');
    });

    it('9: should invalidate previous OTP when new OTP is issued after cooldown', async () => {
      const resendEmail = `resend_inval_${Date.now()}_${Math.floor(Math.random() * 10000)}@campusverse.edu`;
      const firstRes = await request(app)
        .post('/api/v1/auth/send-otp')
        .send({ email: resendEmail, purpose: 'EMAIL_VERIFICATION' });
      expect(firstRes.status).toBe(200);

      // Manually backdate the first OTP in DB to simulate cooldown expiry
      await prisma.otpToken.updateMany({
        where: { email: resendEmail },
        data: { createdAt: new Date(Date.now() - 70000) }
      });

      // Get old OTP from first send
      const sent1 = emailService.getSentEmails().filter(e => e.to === resendEmail);
      const oldOtpMatch = sent1[sent1.length - 1].text.match(/verification code:\s*(\d{6})/i);
      const oldOtp = oldOtpMatch![1];

      // Request fresh OTP
      const freshRes = await request(app)
        .post('/api/v1/auth/send-otp')
        .send({ email: resendEmail, purpose: 'EMAIL_VERIFICATION' });
      expect(freshRes.status).toBe(200);

      // Verify old OTP now FAILS
      const oldOtpAttempt = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: resendEmail, otp: oldOtp, purpose: 'EMAIL_VERIFICATION' });
      expect(oldOtpAttempt.status).toBe(400);

      // Verify new OTP SUCCEEDS
      const sent2 = emailService.getSentEmails().filter(e => e.to === resendEmail);
      const newOtpMatch = sent2[sent2.length - 1].text.match(/verification code:\s*(\d{6})/i);
      const newOtp = newOtpMatch![1];

      const newOtpAttempt = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: resendEmail, otp: newOtp, purpose: 'EMAIL_VERIFICATION' });
      expect(newOtpAttempt.status).toBe(200);
    });
  });

  // ---------------------------------------------------------------------------
  // 4. Attempt Limit & Expiration
  // ---------------------------------------------------------------------------
  describe('4. Attempt Protection & Expiration', () => {
    it('8: should invalidate OTP after 5 failed verification attempts', async () => {
      const bruteEmail = `brute_lockout_${Date.now()}_${Math.floor(Math.random() * 10000)}@campusverse.edu`;
      await otpService.sendOtp(bruteEmail, 'EMAIL_VERIFICATION');

      // Attempt 1-4 with wrong OTP
      for (let i = 1; i <= 4; i++) {
        const res = await request(app)
          .post('/api/v1/auth/verify-otp')
          .send({ email: bruteEmail, otp: '111111', purpose: 'EMAIL_VERIFICATION' });
        expect(res.status).toBe(400);
      }

      // Attempt 5 (Reaches max attempts -> permanently invalidates)
      const fifthRes = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: bruteEmail, otp: '111111', purpose: 'EMAIL_VERIFICATION' });
      expect(fifthRes.status).toBe(400);
      expect(fifthRes.body.error.message).toContain('Maximum verification attempts exceeded');

      // Even correct OTP now FAILS
      const sent = emailService.getSentEmails().filter(e => e.to === bruteEmail);
      const match = sent[sent.length - 1].text.match(/verification code:\s*(\d{6})/i);
      const correctOtp = match![1];

      const correctAttemptAfterLockout = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: bruteEmail, otp: correctOtp, purpose: 'EMAIL_VERIFICATION' });
      expect(correctAttemptAfterLockout.status).toBe(400);
    });

    it('6: should reject expired OTP', async () => {
      const expireEmail = `expire_test_${Date.now()}_${Math.floor(Math.random() * 10000)}@campusverse.edu`;
      await otpService.sendOtp(expireEmail, 'EMAIL_VERIFICATION');

      // Backdate expiration in DB to simulate expired state
      await prisma.otpToken.updateMany({
        where: { email: expireEmail },
        data: { expiresAt: new Date(Date.now() - 10000) }
      });

      const sent = emailService.getSentEmails().filter(e => e.to === expireEmail);
      const match = sent[sent.length - 1].text.match(/verification code:\s*(\d{6})/i);
      const otp = match![1];

      const res = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({ email: expireEmail, otp, purpose: 'EMAIL_VERIFICATION' });

      expect(res.status).toBe(400);
      expect(res.body.error.message).toContain('expired');
    });
  });

  // ---------------------------------------------------------------------------
  // 5. Password Reset Workflow
  // ---------------------------------------------------------------------------
  describe('5. Password Reset via Secure OTP', () => {
    const pwEmail = `pw_reset_${Date.now()}@campusverse.edu`;

    beforeAll(async () => {
      await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Password Reset Tester',
          email: pwEmail,
          password: 'OldPassword123',
          role: 'STUDENT'
        });
    });

    it('19: should handle forgot-password and reset-password with secure OTP', async () => {
      // 1. Request password reset
      const forgotRes = await request(app)
        .post('/api/v1/auth/forgot-password')
        .send({ email: pwEmail });
      expect(forgotRes.status).toBe(200);
      expect(forgotRes.body.success).toBe(true);

      // 2. Extract OTP from sent email
      const sent = emailService.getSentEmails().filter(e => e.to === pwEmail);
      const match = sent[sent.length - 1].text.match(/verification code:\s*(\d{6})/i);
      expect(match).not.toBeNull();
      const resetOtp = match![1];

      // 3. Reset password with OTP
      const resetRes = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          email: pwEmail,
          otp: resetOtp,
          newPassword: 'NewSecurePassword456'
        });
      expect(resetRes.status).toBe(200);
      expect(resetRes.body.success).toBe(true);

      // 4. Verify login with old password FAILS
      const oldLogin = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: pwEmail, password: 'OldPassword123' });
      expect(oldLogin.status).toBe(401);

      // 5. Verify login with new password SUCCEEDS
      const newLogin = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: pwEmail, password: 'NewSecurePassword456' });
      expect(newLogin.status).toBe(200);
      expect(newLogin.body.data.token).toBeDefined();
    });

    it('21: should block mock provider and fail safely in production mode', async () => {
      const originalEnv = env.NODE_ENV;
      const originalProvider = env.OTP_EMAIL_PROVIDER;
      try {
        (env as any).NODE_ENV = 'production';
        (env as any).OTP_EMAIL_PROVIDER = 'mock';

        const result = await emailService.sendEmail({
          to: 'prod_test@campusverse.edu',
          subject: 'Production Test',
          text: 'Test message'
        });

        expect(result.success).toBe(false);
        expect(result.error).toContain('Mock email provider is disabled in production');
      } finally {
        (env as any).NODE_ENV = originalEnv;
        (env as any).OTP_EMAIL_PROVIDER = originalProvider;
      }
    });

    it('22: should safely fail when RESEND provider is configured without RESEND_API_KEY', async () => {
      const originalEnv = env.NODE_ENV;
      const originalProvider = env.OTP_EMAIL_PROVIDER;
      const originalKey = env.RESEND_API_KEY;
      try {
        (env as any).NODE_ENV = 'production';
        (env as any).OTP_EMAIL_PROVIDER = 'resend';
        (env as any).RESEND_API_KEY = '';

        const result = await emailService.sendEmail({
          to: 'resend_test@campusverse.edu',
          subject: 'Resend Test',
          text: 'Test message'
        });

        expect(result.success).toBe(false);
        expect(result.error).toContain('RESEND_API_KEY is not configured');
      } finally {
        (env as any).NODE_ENV = originalEnv;
        (env as any).OTP_EMAIL_PROVIDER = originalProvider;
        (env as any).RESEND_API_KEY = originalKey;
      }
    });
  });
});
