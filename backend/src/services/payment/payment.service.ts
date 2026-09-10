import crypto from 'crypto';
import { env } from '../../config/env';
import { RazorpayPaymentProvider } from './razorpay.provider';
import {
  PaymentOrderStatus,
  TransactionStatus,
  RefundStatus,
  EntitlementStatus
} from './payment.types';

export const paymentProvider = new RazorpayPaymentProvider({
  keyId: env.RAZORPAY_KEY_ID,
  keySecret: env.RAZORPAY_KEY_SECRET,
  webhookSecret: env.RAZORPAY_WEBHOOK_SECRET,
  isSandbox: env.PAYMENT_SANDBOX_MODE
});

/**
 * Generates an HMAC-SHA256 signature for client payment verification.
 * Useful for automated tests and sandbox test harnesses.
 */
export function generateTestPaymentSignature(
  orderId: string,
  paymentId: string,
  secret: string = env.RAZORPAY_KEY_SECRET
): string {
  return crypto
    .createHmac('sha256', secret)
    .update(`${orderId}|${paymentId}`)
    .digest('hex');
}

/**
 * Generates an HMAC-SHA256 signature for webhook verification.
 * Useful for testing webhook endpoints.
 */
export function generateTestWebhookSignature(
  payload: string | Buffer,
  secret: string = env.RAZORPAY_WEBHOOK_SECRET
): string {
  const body = typeof payload === 'string' ? Buffer.from(payload, 'utf8') : payload;
  return crypto.createHmac('sha256', secret).update(body).digest('hex');
}

/**
 * State Machine Validation: PaymentOrder
 */
const VALID_ORDER_TRANSITIONS: Record<PaymentOrderStatus, PaymentOrderStatus[]> = {
  PENDING: ['PROCESSING', 'PAID', 'FAILED', 'EXPIRED'],
  PROCESSING: ['PAID', 'FAILED', 'EXPIRED'],
  PAID: [], // Terminal state
  FAILED: [], // Terminal state
  EXPIRED: [] // Terminal state
};

export function isValidOrderTransition(
  current: PaymentOrderStatus,
  next: PaymentOrderStatus
): boolean {
  if (current === next) return true; // Idempotent no-op
  return VALID_ORDER_TRANSITIONS[current]?.includes(next) ?? false;
}

/**
 * State Machine Validation: Transaction
 */
const VALID_TRANSACTION_TRANSITIONS: Record<TransactionStatus, TransactionStatus[]> = {
  PENDING: ['AUTHORIZED', 'PAID', 'FAILED'],
  AUTHORIZED: ['PAID', 'FAILED'],
  PAID: ['PARTIALLY_REFUNDED', 'REFUNDED'],
  PARTIALLY_REFUNDED: ['REFUNDED'],
  FAILED: [], // Terminal
  REFUNDED: [] // Terminal
};

export function isValidTransactionTransition(
  current: TransactionStatus,
  next: TransactionStatus
): boolean {
  if (current === next) return true; // Idempotent no-op
  return VALID_TRANSACTION_TRANSITIONS[current]?.includes(next) ?? false;
}

/**
 * State Machine Validation: Refund
 */
const VALID_REFUND_TRANSITIONS: Record<RefundStatus, RefundStatus[]> = {
  PENDING: ['PROCESSED', 'FAILED'],
  PROCESSED: [],
  FAILED: []
};

export function isValidRefundTransition(
  current: RefundStatus,
  next: RefundStatus
): boolean {
  if (current === next) return true;
  return VALID_REFUND_TRANSITIONS[current]?.includes(next) ?? false;
}

/**
 * State Machine Validation: Entitlement
 */
const VALID_ENTITLEMENT_TRANSITIONS: Record<EntitlementStatus, EntitlementStatus[]> = {
  ACTIVE: ['EXPIRED', 'REVOKED'],
  EXPIRED: [],
  REVOKED: []
};

export function isValidEntitlementTransition(
  current: EntitlementStatus,
  next: EntitlementStatus
): boolean {
  if (current === next) return true;
  return VALID_ENTITLEMENT_TRANSITIONS[current]?.includes(next) ?? false;
}
