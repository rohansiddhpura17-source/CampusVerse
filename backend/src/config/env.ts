import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const nodeEnv = process.env.NODE_ENV || 'development';
const isProduction = nodeEnv === 'production';

// Strict validation: Required production secrets must cause startup failure when missing
if (isProduction) {
  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.trim() === '') {
    throw new Error('FATAL CONFIGURATION ERROR: JWT_SECRET environment variable is missing in production environment.');
  }
  if (!process.env.DATABASE_URL || process.env.DATABASE_URL.startsWith('file:')) {
    throw new Error('FATAL CONFIGURATION ERROR: Production environment requires a managed PostgreSQL DATABASE_URL.');
  }
}

// Ensure JWT_SECRET is supplied from environment in all modes (no hardcoded secret fallbacks)
const jwtSecret = process.env.JWT_SECRET;
if (!jwtSecret && !isProduction && nodeEnv !== 'test') {
  throw new Error('FATAL CONFIGURATION ERROR: JWT_SECRET must be defined in your environment or .env file.');
}

export const env = {
  PORT: parseInt(process.env.PORT || '4000', 10),
  NODE_ENV: nodeEnv,
  DATABASE_URL: process.env.DATABASE_URL || 'file:./dev.db',
  DIRECT_URL: process.env.DIRECT_URL || '',
  JWT_SECRET: jwtSecret || (nodeEnv === 'test' ? 'test_transient_secret' : ''),
  JWT_EXPIRES_IN: process.env.JWT_EXPIRES_IN || '7d',
  CORS_ORIGIN: process.env.CORS_ORIGIN || '',

  // OTP Configuration
  OTP_EXPIRY_MINUTES: parseInt(process.env.OTP_EXPIRY_MINUTES || '5', 10),
  OTP_MAX_ATTEMPTS: parseInt(process.env.OTP_MAX_ATTEMPTS || '5', 10),
  OTP_RESEND_COOLDOWN_SECONDS: parseInt(process.env.OTP_RESEND_COOLDOWN_SECONDS || '60', 10),

  // Email Delivery Configuration
  OTP_EMAIL_PROVIDER: process.env.OTP_EMAIL_PROVIDER || (process.env.NODE_ENV === 'test' ? 'mock' : 'mock'),
  OTP_FROM_EMAIL: process.env.OTP_FROM_EMAIL || 'noreply@campusverse.edu',
  OTP_FROM_NAME: process.env.OTP_FROM_NAME || 'CampusVerse',
  SMTP_HOST: process.env.SMTP_HOST || '',
  SMTP_PORT: parseInt(process.env.SMTP_PORT || '587', 10),
  SMTP_USER: process.env.SMTP_USER || '',
  SMTP_PASSWORD: process.env.SMTP_PASSWORD || '',
  SMTP_SECURE: process.env.SMTP_SECURE === 'true',
  RESEND_API_KEY: process.env.RESEND_API_KEY || '',
  SENDGRID_API_KEY: process.env.SENDGRID_API_KEY || '',

  // Payment Gateway Configuration
  PAYMENT_PROVIDER: process.env.PAYMENT_PROVIDER || 'RAZORPAY',
  PAYMENT_CURRENCY: process.env.PAYMENT_CURRENCY || 'INR',
  PAYMENT_SANDBOX_MODE: process.env.PAYMENT_SANDBOX_MODE !== 'false',
  RAZORPAY_KEY_ID: process.env.RAZORPAY_KEY_ID || (isProduction ? '' : 'rzp_test_campusverse_dev'),
  RAZORPAY_KEY_SECRET: process.env.RAZORPAY_KEY_SECRET || (isProduction ? '' : 'rzp_test_secret_dev_campusverse_2026'),
  RAZORPAY_WEBHOOK_SECRET: process.env.RAZORPAY_WEBHOOK_SECRET || (isProduction ? '' : 'rzp_webhook_secret_dev_campusverse_2026')
};

