/**
 * CampusVerse Payment Types and Provider Abstraction
 * All monetary amounts are in integer paise (minor units, 1 INR = 100 paise).
 */

export interface CreateOrderParams {
  amountPaise: number;
  currency: string;
  receipt: string;
  notes?: Record<string, string>;
}

export interface CreateOrderResult {
  providerOrderId: string;
  amountPaise: number;
  currency: string;
}

export interface VerifyPaymentParams {
  providerOrderId: string;
  providerPaymentId: string;
  providerSignature: string;
}

export interface ProcessRefundParams {
  providerPaymentId: string;
  amountPaise: number;
  reason?: string;
  notes?: Record<string, string>;
}

export interface ProcessRefundResult {
  providerRefundId: string;
  amountPaise: number;
  status: 'PROCESSED' | 'PENDING' | 'FAILED';
}

export interface PaymentProvider {
  createOrder(params: CreateOrderParams): Promise<CreateOrderResult>;
  verifyPaymentSignature(params: VerifyPaymentParams): boolean;
  verifyWebhookSignature(rawBody: string | Buffer, signature: string): boolean;
  processRefund(params: ProcessRefundParams): Promise<ProcessRefundResult>;
}

export type PaymentOrderStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED' | 'EXPIRED';
export type TransactionStatus = 'PENDING' | 'AUTHORIZED' | 'PAID' | 'FAILED' | 'REFUNDED' | 'PARTIALLY_REFUNDED';
export type RefundStatus = 'PENDING' | 'PROCESSED' | 'FAILED';
export type EntitlementStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';
export type RoleTarget = 'ALL' | 'STUDENT' | 'ASPIRANT' | 'ALUMNI';
export type ProductType = 
  | 'STUDENT_PREMIUM'
  | 'ASPIRANT_PREMIUM'
  | 'ALUMNI_PREMIUM'
  | 'AI_CREDITS'
  | 'MENTORSHIP'
  | 'CAREER_SERVICE'
  | 'PAID_EVENT';
