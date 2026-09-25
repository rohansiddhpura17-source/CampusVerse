import { Router } from 'express';
import {
  register,
  login,
  logout,
  refreshToken,
  getCurrentUser,
  sendOtp,
  verifyOtp,
  forgotPassword,
  resetPassword,
  registerSchema,
  loginSchema,
  sendOtpSchema,
  verifyOtpSchema,
  forgotPasswordSchema,
  resetPasswordSchema
} from '../controllers/auth.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';
import { otpRateLimiter } from '../services/rate-limiter.service';

const router = Router();

router.post('/register', validateBody(registerSchema), register);
router.post('/login', validateBody(loginSchema), login);
router.post('/logout', requireAuth, logout);
router.post('/refresh', requireAuth, refreshToken);
router.get('/me', requireAuth, getCurrentUser);

// Production OTP Endpoints
router.post('/send-otp', otpRateLimiter, validateBody(sendOtpSchema), sendOtp);
router.post('/verify-otp', validateBody(verifyOtpSchema), verifyOtp);
router.post('/forgot-password', otpRateLimiter, validateBody(forgotPasswordSchema), forgotPassword);
router.post('/reset-password', validateBody(resetPasswordSchema), resetPassword);

export default router;
