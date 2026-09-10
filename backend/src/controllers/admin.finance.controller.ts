import { Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '../services/prisma.service';
import { logAudit } from '../services/audit.service';
import { sendSuccess, sendError } from '../utils/response';
import { paymentProvider } from '../services/payment/payment.service';

export const refundTransactionSchema = z.object({
  amountPaise: z.number().int().positive(),
  reason: z.string().min(3).max(500),
  adminNotes: z.string().max(1000).optional()
});

/**
 * 1. GET /api/v1/admin/finance/overview
 * Platform-wide financial KPIs calculated from integer paise.
 */
export async function getFinanceOverview(req: Request, res: Response): Promise<void> {
  // Aggregate Gross Revenue from all settled transactions (PAID, REFUNDED, PARTIALLY_REFUNDED)
  const settledTransactions = await prisma.transaction.findMany({
    where: {
      status: { in: ['PAID', 'REFUNDED', 'PARTIALLY_REFUNDED'] }
    },
    select: { amountPaise: true }
  });

  const grossRevenuePaise = settledTransactions.reduce((sum, tx) => sum + tx.amountPaise, 0);

  // Aggregate Total Processed Refunds
  const processedRefunds = await prisma.refund.findMany({
    where: { status: 'PROCESSED' },
    select: { amountPaise: true }
  });

  const refundedAmountPaise = processedRefunds.reduce((sum, rf) => sum + rf.amountPaise, 0);
  const netRevenuePaise = Math.max(0, grossRevenuePaise - refundedAmountPaise);

  // Status Counts
  const [
    totalOrders,
    paidTransactions,
    failedTransactions,
    pendingTransactions
  ] = await Promise.all([
    prisma.paymentOrder.count(),
    prisma.transaction.count({ where: { status: { in: ['PAID', 'REFUNDED', 'PARTIALLY_REFUNDED'] } } }),
    prisma.transaction.count({ where: { status: 'FAILED' } }),
    prisma.paymentOrder.count({ where: { status: 'PENDING' } })
  ]);

  const settledCount = paidTransactions + failedTransactions;
  const successRate = settledCount > 0 ? parseFloat(((paidTransactions / settledCount) * 100).toFixed(2)) : 100.0;

  sendSuccess(res, {
    currency: 'INR',
    grossRevenuePaise,
    grossRevenueInr: grossRevenuePaise / 100,
    refundedAmountPaise,
    refundedAmountInr: refundedAmountPaise / 100,
    netRevenuePaise,
    netRevenueInr: netRevenuePaise / 100,
    counts: {
      totalOrders,
      paidTransactions,
      failedTransactions,
      pendingTransactions,
      successRate
    }
  }, 'Finance overview retrieved successfully');
}

/**
 * 2. GET /api/v1/admin/finance/transactions
 * Paginated platform transaction ledger with advanced filtering.
 */
export async function getFinanceTransactions(req: Request, res: Response): Promise<void> {
  const page = parseInt(req.query.page as string || '1', 10);
  const limit = parseInt(req.query.limit as string || '20', 10);
  const skip = (page - 1) * limit;

  const status = req.query.status as string | undefined;
  const role = req.query.role as string | undefined;
  const productId = req.query.productId as string | undefined;
  const startDate = req.query.startDate as string | undefined;
  const endDate = req.query.endDate as string | undefined;
  const search = req.query.search as string | undefined;

  const where: any = {};

  if (status) {
    where.status = status.toUpperCase();
  }

  if (productId) {
    where.productId = productId;
  }

  if (role) {
    where.user = { role: role.toUpperCase() };
  }

  if (startDate || endDate) {
    where.createdAt = {};
    if (startDate) where.createdAt.gte = new Date(startDate);
    if (endDate) where.createdAt.lte = new Date(endDate);
  }

  if (search) {
    where.OR = [
      { providerPaymentId: { contains: search } },
      { paymentOrder: { providerOrderId: { contains: search } } },
      { user: { email: { contains: search } } },
      { user: { profile: { fullName: { contains: search } } } }
    ];
  }

  const [transactions, total] = await Promise.all([
    prisma.transaction.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            role: true,
            profile: { select: { fullName: true } }
          }
        },
        product: {
          select: { id: true, sku: true, title: true, productType: true }
        },
        paymentOrder: {
          select: { id: true, providerOrderId: true, idempotencyKey: true }
        },
        refunds: {
          orderBy: { createdAt: 'desc' }
        },
        entitlement: {
          select: { id: true, status: true, validUntil: true }
        }
      }
    }),
    prisma.transaction.count({ where })
  ]);

  sendSuccess(res, transactions, 'Admin finance transactions retrieved', 200, {
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit)
  });
}

/**
 * 3. POST /api/v1/admin/finance/transactions/:id/refund
 * Admin-executed full or partial refund with integer paise balance validation and audit logging.
 */
export async function processTransactionRefund(req: Request, res: Response): Promise<void> {
  const adminId = req.user!.userId;
  const { id: transactionId } = req.params;
  const { amountPaise, reason, adminNotes } = req.body;

  // 1. Fetch transaction with previous refunds and entitlement
  const transaction = await prisma.transaction.findUnique({
    where: { id: transactionId },
    include: {
      refunds: true,
      entitlement: true
    }
  });

  if (!transaction) {
    sendError(res, 'Transaction not found.', 404, 'TRANSACTION_NOT_FOUND');
    return;
  }

  // 2. Validate state: Transaction must be in refundable state
  if (transaction.status !== 'PAID' && transaction.status !== 'PARTIALLY_REFUNDED') {
    sendError(
      res,
      `Cannot refund transaction with status ${transaction.status}. Only PAID or PARTIALLY_REFUNDED transactions can be refunded.`,
      400,
      'INVALID_TRANSACTION_STATE'
    );
    return;
  }

  // 3. Validate amounts in integer paise
  const existingRefundedPaise = transaction.refunds
    .filter((r) => r.status === 'PROCESSED')
    .reduce((sum, r) => sum + r.amountPaise, 0);

  const remainingRefundablePaise = transaction.amountPaise - existingRefundedPaise;

  if (amountPaise > remainingRefundablePaise) {
    sendError(
      res,
      `Requested refund amount (${amountPaise} paise) exceeds refundable balance (${remainingRefundablePaise} paise).`,
      400,
      'REFUND_EXCEEDS_BALANCE'
    );
    return;
  }

  // 4. Call provider refund API
  const refundResult = await paymentProvider.processRefund({
    providerPaymentId: transaction.providerPaymentId,
    amountPaise,
    reason,
    notes: {
      transactionId: transaction.id,
      adminId
    }
  });

  // 5. Atomic persistence of Refund and State Updates
  const finalResult = await prisma.$transaction(async (tx) => {
    // Create Refund record
    const refund = await tx.refund.create({
      data: {
        transactionId: transaction.id,
        amountPaise,
        currency: transaction.currency,
        reason,
        status: 'PROCESSED',
        providerRefundId: refundResult.providerRefundId,
        processedByAdminId: adminId,
        adminNotes: adminNotes || null
      }
    });

    const isFullyRefunded = existingRefundedPaise + amountPaise === transaction.amountPaise;
    const newTxStatus = isFullyRefunded ? 'REFUNDED' : 'PARTIALLY_REFUNDED';

    // Update Transaction status
    await tx.transaction.update({
      where: { id: transaction.id },
      data: { status: newTxStatus }
    });

    // If fully refunded, revoke active entitlement
    if (isFullyRefunded && transaction.entitlement) {
      await tx.entitlement.update({
        where: { id: transaction.entitlement.id },
        data: { status: 'REVOKED' }
      });
    }

    return { refund, newTxStatus, isFullyRefunded };
  });

  // 6. Audit Logging
  await logAudit(adminId, 'ADMIN_REFUND_EXECUTED', 'REFUND', finalResult.refund.id, {
    transactionId: transaction.id,
    amountPaise,
    isFullyRefunded: finalResult.isFullyRefunded,
    reason
  });

  sendSuccess(res, {
    refundId: finalResult.refund.id,
    transactionId: transaction.id,
    amountPaise,
    amountInr: amountPaise / 100,
    status: finalResult.refund.status,
    transactionStatus: finalResult.newTxStatus,
    remainingBalancePaise: remainingRefundablePaise - amountPaise
  }, 'Refund processed successfully');
}
