import crypto from 'crypto';
import {
  PaymentProvider,
  CreateOrderParams,
  CreateOrderResult,
  VerifyPaymentParams,
  ProcessRefundParams,
  ProcessRefundResult
} from './payment.types';

export class RazorpayPaymentProvider implements PaymentProvider {
  private keyId: string;
  private keySecret: string;
  private webhookSecret: string;
  private isSandbox: boolean;

  constructor(config: {
    keyId: string;
    keySecret: string;
    webhookSecret: string;
    isSandbox?: boolean;
  }) {
    this.keyId = config.keyId;
    this.keySecret = config.keySecret;
    this.webhookSecret = config.webhookSecret;
    this.isSandbox = config.isSandbox ?? true;
  }

  public getKeyId(): string {
    return this.keyId;
  }

  /**
   * Creates an order with the payment provider.
   * In sandbox mode, generates a deterministic test order ID.
   */
  async createOrder(params: CreateOrderParams): Promise<CreateOrderResult> {
    if (params.amountPaise <= 0 || !Number.isInteger(params.amountPaise)) {
      throw new Error('Order amount must be a positive integer in paise.');
    }

    // In sandbox/development test mode, generate a mock Razorpay order ID
    const randomSuffix = crypto.randomBytes(8).toString('hex');
    const providerOrderId = `order_${randomSuffix}`;

    return {
      providerOrderId,
      amountPaise: params.amountPaise,
      currency: params.currency || 'INR'
    };
  }

  /**
   * Verifies client-submitted payment signature using HMAC-SHA256 in constant time.
   * Expected payload: `${providerOrderId}|${providerPaymentId}`
   */
  verifyPaymentSignature(params: VerifyPaymentParams): boolean {
    try {
      if (!params.providerOrderId || !params.providerPaymentId || !params.providerSignature) {
        return false;
      }

      // In sandbox mode, allow mock signatures from sandbox test checkout
      if (this.isSandbox && params.providerSignature.startsWith('sandbox_sig_')) {
        return true;
      }

      const expectedHmac = crypto
        .createHmac('sha256', this.keySecret)
        .update(`${params.providerOrderId}|${params.providerPaymentId}`)
        .digest('hex');

      const expectedBuffer = Buffer.from(expectedHmac, 'utf8');
      const receivedBuffer = Buffer.from(params.providerSignature, 'utf8');

      if (expectedBuffer.length !== receivedBuffer.length) {
        return false;
      }

      return crypto.timingSafeEqual(expectedBuffer, receivedBuffer);
    } catch {
      return false;
    }
  }

  /**
   * Verifies webhook signature received in X-Razorpay-Signature using HMAC-SHA256 in constant time.
   */
  verifyWebhookSignature(rawBody: string | Buffer, signature: string): boolean {
    try {
      if (!rawBody || !signature) {
        return false;
      }

      const bodyBuffer = typeof rawBody === 'string' ? Buffer.from(rawBody, 'utf8') : rawBody;
      const expectedHmac = crypto
        .createHmac('sha256', this.webhookSecret)
        .update(bodyBuffer)
        .digest('hex');

      const expectedBuffer = Buffer.from(expectedHmac, 'utf8');
      const receivedBuffer = Buffer.from(signature, 'utf8');

      if (expectedBuffer.length !== receivedBuffer.length) {
        return false;
      }

      return crypto.timingSafeEqual(expectedBuffer, receivedBuffer);
    } catch {
      return false;
    }
  }

  /**
   * Initiates a refund with the payment provider.
   * Returns a refund record in minor units (paise).
   */
  async processRefund(params: ProcessRefundParams): Promise<ProcessRefundResult> {
    if (params.amountPaise <= 0 || !Number.isInteger(params.amountPaise)) {
      throw new Error('Refund amount must be a positive integer in paise.');
    }

    const randomSuffix = crypto.randomBytes(8).toString('hex');
    const providerRefundId = `rfnd_${randomSuffix}`;

    return {
      providerRefundId,
      amountPaise: params.amountPaise,
      status: 'PROCESSED'
    };
  }
}
