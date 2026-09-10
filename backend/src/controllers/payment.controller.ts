import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { sendSuccess, sendError } from '../utils/response';
import {
  paymentProvider,
  isValidOrderTransition
} from '../services/payment/payment.service';

export const createOrderSchema = z.object({
  productId: z.string().uuid(),
  idempotencyKey: z.string().min(8).max(128)
});

export const verifyPaymentSchema = z.object({
  orderId: z.string().uuid(),
  providerPaymentId: z.string().min(4),
  providerSignature: z.string().min(10),
  paymentMethod: z.enum(['UPI', 'CARD', 'NETBANKING', 'WALLET']).optional()
});

/**
 * 1. GET /api/v1/payments/products
 * Retrieves active products filtered by user role eligibility.
 */
export async function getProducts(req: Request, res: Response): Promise<void> {
  const userRole = req.user?.role;
  const requestedRole = req.query.targetRole as string | undefined;

  const where: any = { isActive: true };

  if (requestedRole) {
    where.OR = [
      { targetRole: 'ALL' },
      { targetRole: requestedRole.toUpperCase() }
    ];
  } else if (userRole && userRole !== 'ADMIN') {
    where.OR = [
      { targetRole: 'ALL' },
      { targetRole: userRole }
    ];
  }

  const products = await prisma.product.findMany({
    where,
    orderBy: { amountPaise: 'asc' }
  });

  sendSuccess(res, products, 'Products retrieved successfully');
}

/**
 * 2. POST /api/v1/payments/orders
 * Creates a server-authoritative payment order.
 * Client CANNOT control amount or currency.
 */
export async function createPaymentOrder(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const userRole = req.user!.role;
  const { productId, idempotencyKey } = req.body;

  // 1. Idempotency check: Return existing order if key was already submitted by this user
  const existingOrder = await prisma.paymentOrder.findUnique({
    where: { idempotencyKey },
    include: { product: true }
  });

  if (existingOrder) {
    if (existingOrder.userId !== userId) {
      sendError(res, 'Idempotency key already in use by another session.', 409, 'IDEMPOTENCY_CONFLICT');
      return;
    }

    sendSuccess(
      res,
      {
        orderId: existingOrder.id,
        providerOrderId: existingOrder.providerOrderId,
        amountPaise: existingOrder.amountPaise,
        currency: existingOrder.currency,
        keyId: paymentProvider.getKeyId(),
        status: existingOrder.status,
        product: {
          id: existingOrder.product.id,
          title: existingOrder.product.title,
          sku: existingOrder.product.sku
        }
      },
      'Order retrieved via idempotency',
      200
    );
    return;
  }

  // 2. Load Product & verify existence and active status
  const product = await prisma.product.findUnique({
    where: { id: productId }
  });

  if (!product || !product.isActive) {
    sendError(res, 'Product not found or currently unavailable.', 404, 'PRODUCT_NOT_FOUND');
    return;
  }

  // 3. Verify Role Eligibility
  if (product.targetRole !== 'ALL' && product.targetRole !== userRole && userRole !== 'ADMIN') {
    sendError(
      res,
      `This product is restricted to ${product.targetRole} accounts.`,
      403,
      'ROLE_INELIGIBLE'
    );
    return;
  }

  // 4. Server-enforced amount in integer paise
  const amountPaise = product.amountPaise;

  // 5. Create provider order
  const providerResult = await paymentProvider.createOrder({
    amountPaise,
    currency: product.currency,
    receipt: idempotencyKey,
    notes: {
      userId,
      productId: product.id,
      sku: product.sku
    }
  });

  // 6. Persist PaymentOrder in Database
  const order = await prisma.paymentOrder.create({
    data: {
      userId,
      productId: product.id,
      amountPaise,
      currency: product.currency,
      provider: 'RAZORPAY',
      providerOrderId: providerResult.providerOrderId,
      idempotencyKey,
      status: 'PENDING'
    },
    include: {
      product: {
        select: { id: true, title: true, sku: true }
      }
    }
  });

  sendSuccess(
    res,
    {
      orderId: order.id,
      providerOrderId: order.providerOrderId,
      amountPaise: order.amountPaise,
      currency: order.currency,
      keyId: paymentProvider.getKeyId(),
      status: order.status,
      product: order.product
    },
    'Payment order initialized successfully',
    201
  );
}

/**
 * 3. POST /api/v1/payments/verify
 * Cryptographically verifies payment completion and activates entitlements atomically.
 */
export async function verifyPayment(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const { orderId, providerPaymentId, providerSignature, paymentMethod } = req.body;

  // 1. Fetch PaymentOrder
  const order = await prisma.paymentOrder.findUnique({
    where: { id: orderId },
    include: { product: true, transaction: { include: { entitlement: true } } }
  });

  if (!order) {
    sendError(res, 'Payment order not found.', 404, 'ORDER_NOT_FOUND');
    return;
  }

  // 2. Ownership verification
  if (order.userId !== userId) {
    sendError(res, 'You are not authorized to verify this payment order.', 403, 'FORBIDDEN');
    return;
  }

  // 3. Idempotent check: If already paid, return settled state
  if (order.status === 'PAID' && order.transaction && order.transaction.status === 'PAID') {
    sendSuccess(
      res,
      {
        transactionId: order.transaction.id,
        status: 'PAID',
        entitlement: order.transaction.entitlement
      },
      'Payment already verified.'
    );
    return;
  }

  // 4. Verify Cryptographic Signature
  const isValidSignature = paymentProvider.verifyPaymentSignature({
    providerOrderId: order.providerOrderId,
    providerPaymentId,
    providerSignature
  });

  if (!isValidSignature) {
    // Record failure in DB
    await prisma.paymentOrder.update({
      where: { id: orderId },
      data: { status: 'FAILED' }
    });

    sendError(res, 'Payment verification failed: Invalid provider signature.', 400, 'INVALID_SIGNATURE');
    return;
  }

  // 5. Atomic database transaction: Settle Order, Create Transaction, and Grant Entitlement
  const result = await prisma.$transaction(async (tx) => {
    // Re-check concurrency within transaction
    const freshOrder = await tx.paymentOrder.findUnique({
      where: { id: orderId },
      include: { transaction: { include: { entitlement: true } } }
    });

    if (freshOrder?.status === 'PAID' && freshOrder.transaction?.status === 'PAID') {
      return {
        transaction: freshOrder.transaction,
        entitlement: freshOrder.transaction.entitlement
      };
    }

    // Update Order to PAID
    await tx.paymentOrder.update({
      where: { id: orderId },
      data: { status: 'PAID' }
    });

    // Create or Update Transaction
    const transaction = await tx.transaction.upsert({
      where: { paymentOrderId: orderId },
      create: {
        userId,
        productId: order.productId,
        paymentOrderId: orderId,
        amountPaise: order.amountPaise,
        currency: order.currency,
        provider: 'RAZORPAY',
        providerPaymentId,
        paymentMethod: paymentMethod || 'UPI',
        status: 'PAID',
        paidAt: new Date()
      },
      update: {
        providerPaymentId,
        status: 'PAID',
        paidAt: new Date()
      }
    });

    // Calculate entitlement validity (30 days default for subscriptions, null for consumables)
    const validUntil = new Date();
    validUntil.setDate(validUntil.getDate() + 30);

    const entitlement = await tx.entitlement.upsert({
      where: { sourceTransactionId: transaction.id },
      create: {
        userId,
        productId: order.productId,
        status: 'ACTIVE',
        validFrom: new Date(),
        validUntil,
        sourceTransactionId: transaction.id
      },
      update: {
        status: 'ACTIVE',
        validUntil
      }
    });

    return { transaction, entitlement };
  });

  await logAudit(userId, 'USER_PAYMENT_COMPLETED', 'TRANSACTION', result.transaction.id, {
    orderId,
    amountPaise: order.amountPaise,
    productId: order.productId
  });

  sendSuccess(
    res,
    {
      transactionId: result.transaction.id,
      status: 'PAID',
      entitlement: result.entitlement
    },
    'Payment verified successfully and entitlement granted.'
  );
}

/**
 * 4. POST /api/v1/payments/webhook
 * Ingests external provider events with HMAC signature verification and strict idempotency.
 */
export async function handleWebhook(req: Request, res: Response): Promise<void> {
  const signature = req.headers['x-razorpay-signature'] as string;

  if (!signature) {
    sendError(res, 'Missing signature header.', 400, 'MISSING_SIGNATURE');
    return;
  }

  // Use rawBody captured by express.json verify hook, fallback to stringified body
  const rawBody = (req as any).rawBody || JSON.stringify(req.body);

  const isValid = paymentProvider.verifyWebhookSignature(rawBody, signature);
  if (!isValid) {
    sendError(res, 'Invalid webhook signature.', 400, 'INVALID_SIGNATURE');
    return;
  }

  const payload = req.body;
  const eventType = payload.event;
  const eventId = payload.event_id || payload.id || `${eventType}_${payload.payload?.payment?.entity?.id || Date.now()}`;

  // 1. Check idempotency table
  const existingEvent = await prisma.webhookEvent.findUnique({
    where: { eventId }
  });

  if (existingEvent) {
    res.status(200).json({ success: true, message: 'Event already processed (idempotent)', duplicate: true });
    return;
  }

  // 2. Process event atomically
  await prisma.$transaction(async (tx) => {
    // Record WebhookEvent
    await tx.webhookEvent.create({
      data: {
        eventId,
        eventType,
        provider: 'RAZORPAY',
        status: 'PROCESSED',
        payload: typeof rawBody === 'string' ? rawBody : rawBody.toString('utf8')
      }
    });

    if (eventType === 'payment.captured') {
      const paymentEntity = payload.payload?.payment?.entity;
      const providerOrderId = paymentEntity?.order_id;
      const providerPaymentId = paymentEntity?.id;
      const paymentMethod = (paymentEntity?.method || 'UPI').toUpperCase();

      if (providerOrderId && providerPaymentId) {
        const order = await tx.paymentOrder.findUnique({
          where: { providerOrderId },
          include: { transaction: true }
        });

        if (order && order.status !== 'PAID') {
          await tx.paymentOrder.update({
            where: { id: order.id },
            data: { status: 'PAID' }
          });

          const transaction = await tx.transaction.upsert({
            where: { paymentOrderId: order.id },
            create: {
              userId: order.userId,
              productId: order.productId,
              paymentOrderId: order.id,
              amountPaise: order.amountPaise,
              currency: order.currency,
              provider: 'RAZORPAY',
              providerPaymentId,
              paymentMethod: paymentMethod === 'CARD' ? 'CARD' : 'UPI',
              status: 'PAID',
              paidAt: new Date()
            },
            update: {
              providerPaymentId,
              status: 'PAID',
              paidAt: new Date()
            }
          });

          const validUntil = new Date();
          validUntil.setDate(validUntil.getDate() + 30);

          await tx.entitlement.upsert({
            where: { sourceTransactionId: transaction.id },
            create: {
              userId: order.userId,
              productId: order.productId,
              status: 'ACTIVE',
              validFrom: new Date(),
              validUntil,
              sourceTransactionId: transaction.id
            },
            update: {
              status: 'ACTIVE'
            }
          });
        }
      }
    } else if (eventType === 'payment.failed') {
      const paymentEntity = payload.payload?.payment?.entity;
      const providerOrderId = paymentEntity?.order_id;
      const failureReason = paymentEntity?.error_description || 'Payment failed';

      if (providerOrderId) {
        const order = await tx.paymentOrder.findUnique({
          where: { providerOrderId }
        });

        if (order && order.status !== 'PAID') {
          await tx.paymentOrder.update({
            where: { id: order.id },
            data: { status: 'FAILED' }
          });
        }
      }
    }
  });

  res.status(200).json({ success: true, message: 'Webhook processed successfully' });
}

/**
 * 5. GET /api/v1/payments/my-transactions
 * Retrieves paginated transactions strictly isolated to the authenticated user.
 */
export async function getMyTransactions(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const [transactions, total] = await Promise.all([
    prisma.transaction.findMany({
      where: { userId },
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        product: {
          select: { id: true, sku: true, title: true, productType: true }
        },
        refunds: {
          select: { id: true, amountPaise: true, status: true, createdAt: true }
        }
      }
    }),
    prisma.transaction.count({ where: { userId } })
  ]);

  sendSuccess(res, transactions, 'User transactions retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

/**
 * 6. GET /api/v1/payments/my-entitlements
 * Retrieves active and historical entitlements strictly isolated to the authenticated user.
 */
export async function getMyEntitlements(req: Request, res: Response): Promise<void> {
  const userId = req.user!.userId;

  const entitlements = await prisma.entitlement.findMany({
    where: { userId },
    orderBy: { createdAt: 'desc' },
    include: {
      product: {
        select: { id: true, sku: true, title: true, productType: true }
      }
    }
  });

  sendSuccess(res, entitlements, 'User entitlements retrieved');
}
