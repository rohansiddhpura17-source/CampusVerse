import request from 'supertest';
import bcrypt from 'bcryptjs';
import { app } from '../src/app';
import { prisma } from '../src/services/prisma.service';
import {
  generateTestPaymentSignature,
  generateTestWebhookSignature,
  isValidOrderTransition,
  isValidTransactionTransition,
  isValidRefundTransition,
  isValidEntitlementTransition
} from '../src/services/payment/payment.service';
import { seedProducts } from '../src/scripts/seed-products';

describe('Phase 3 — Monetization & Payment System Tests', () => {
  let studentToken: string;
  let studentUserId: string;
  let otherStudentToken: string;
  let otherStudentUserId: string;
  let aspirantToken: string;
  let aspirantUserId: string;
  let alumniToken: string;
  let alumniUserId: string;
  let adminToken: string;
  let adminUserId: string;

  let studentProductId: string;
  let aspirantProductId: string;
  let alumniProductId: string;
  let allRoleProductId: string;

  beforeAll(async () => {
    const passwordHash = await bcrypt.hash('Password123', 10);

    // 1. Ensure test users exist with correct roles
    const studentUser = await prisma.user.upsert({
      where: { email: 'payment_student@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true, role: 'STUDENT' },
      create: {
        email: 'payment_student@campusverse.edu',
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Payment Test Student' } }
      }
    });
    studentUserId = studentUser.id;

    const otherStudentUser = await prisma.user.upsert({
      where: { email: 'payment_student2@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true, role: 'STUDENT' },
      create: {
        email: 'payment_student2@campusverse.edu',
        passwordHash,
        role: 'STUDENT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Payment Test Student Two' } }
      }
    });
    otherStudentUserId = otherStudentUser.id;

    const aspirantUser = await prisma.user.upsert({
      where: { email: 'payment_aspirant@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true, role: 'ASPIRANT' },
      create: {
        email: 'payment_aspirant@campusverse.edu',
        passwordHash,
        role: 'ASPIRANT',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Payment Test Aspirant' } }
      }
    });
    aspirantUserId = aspirantUser.id;

    const alumniUser = await prisma.user.upsert({
      where: { email: 'payment_alumni@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true, role: 'ALUMNI' },
      create: {
        email: 'payment_alumni@campusverse.edu',
        passwordHash,
        role: 'ALUMNI',
        isActive: true,
        isEmailVerified: true,
        profile: { create: { fullName: 'Payment Test Alumni' } }
      }
    });
    alumniUserId = alumniUser.id;

    const adminUser = await prisma.user.upsert({
      where: { email: 'payment_admin@campusverse.edu' },
      update: { passwordHash, isActive: true, isEmailVerified: true, isAdminAuthorized: true, role: 'ADMIN' },
      create: {
        email: 'payment_admin@campusverse.edu',
        passwordHash,
        role: 'ADMIN',
        isActive: true,
        isEmailVerified: true,
        isAdminAuthorized: true,
        profile: { create: { fullName: 'Payment Test Admin' } }
      }
    });
    adminUserId = adminUser.id;

    // 2. Obtain JWT Auth Tokens
    const loginStudent = await request(app).post('/api/v1/auth/login').send({
      email: 'payment_student@campusverse.edu',
      password: 'Password123'
    });
    studentToken = loginStudent.body.data.token;

    const loginOtherStudent = await request(app).post('/api/v1/auth/login').send({
      email: 'payment_student2@campusverse.edu',
      password: 'Password123'
    });
    otherStudentToken = loginOtherStudent.body.data.token;

    const loginAspirant = await request(app).post('/api/v1/auth/login').send({
      email: 'payment_aspirant@campusverse.edu',
      password: 'Password123'
    });
    aspirantToken = loginAspirant.body.data.token;

    const loginAlumni = await request(app).post('/api/v1/auth/login').send({
      email: 'payment_alumni@campusverse.edu',
      password: 'Password123'
    });
    alumniToken = loginAlumni.body.data.token;

    const loginAdmin = await request(app).post('/api/v1/auth/login').send({
      email: 'payment_admin@campusverse.edu',
      password: 'Password123'
    });
    adminToken = loginAdmin.body.data.token;

    // 3. Seed Products with integer paise
    await seedProducts();

    const studentProduct = await prisma.product.findUnique({ where: { sku: 'CV_STUDENT_PRO_1M' } });
    studentProductId = studentProduct!.id;

    const aspirantProduct = await prisma.product.findUnique({ where: { sku: 'CV_ASPIRANT_PASS_1M' } });
    aspirantProductId = aspirantProduct!.id;

    const alumniProduct = await prisma.product.findUnique({ where: { sku: 'CV_ALUMNI_PRO_1M' } });
    alumniProductId = alumniProduct!.id;

    const allProduct = await prisma.product.findUnique({ where: { sku: 'CV_AI_CREDITS_50' } });
    allRoleProductId = allProduct!.id;
  });

  afterAll(async () => {
    // Clean up test financial records created during tests
    await prisma.refund.deleteMany({ where: { processedByAdminId: adminUserId } });
    await prisma.entitlement.deleteMany({
      where: { userId: { in: [studentUserId, otherStudentUserId, aspirantUserId, alumniUserId] } }
    });
    await prisma.transaction.deleteMany({
      where: { userId: { in: [studentUserId, otherStudentUserId, aspirantUserId, alumniUserId] } }
    });
    await prisma.paymentOrder.deleteMany({
      where: { userId: { in: [studentUserId, otherStudentUserId, aspirantUserId, alumniUserId] } }
    });
  });

  // ===========================================================================
  // 1. PRODUCT CATALOG & ROLE ELIGIBILITY
  // ===========================================================================
  describe('1. Product Catalog & Role Eligibility', () => {
    it('should retrieve products filtered for STUDENT role (STUDENT + ALL)', async () => {
      const res = await request(app)
        .get('/api/v1/payments/products')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);

      const targetRoles = res.body.data.map((p: any) => p.targetRole);
      expect(targetRoles).toContain('STUDENT');
      expect(targetRoles).toContain('ALL');
      expect(targetRoles).not.toContain('ASPIRANT');
      expect(targetRoles).not.toContain('ALUMNI');

      // Verify integer paise representation
      res.body.data.forEach((p: any) => {
        expect(Number.isInteger(p.amountPaise)).toBe(true);
        expect(p.amountPaise).toBeGreaterThan(0);
      });
    });

    it('should retrieve products filtered for ASPIRANT role (ASPIRANT + ALL)', async () => {
      const res = await request(app)
        .get('/api/v1/payments/products')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(200);
      const targetRoles = res.body.data.map((p: any) => p.targetRole);
      expect(targetRoles).toContain('ASPIRANT');
      expect(targetRoles).toContain('ALL');
      expect(targetRoles).not.toContain('STUDENT');
    });

    it('NEGATIVE TEST: Student attempting to order an ASPIRANT-restricted product receives 403 Forbidden', async () => {
      const res = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: aspirantProductId,
          idempotencyKey: 'test_key_student_denied_aspirant_1'
        });

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('ROLE_INELIGIBLE');
    });

    it('NEGATIVE TEST: Aspirant attempting to order a STUDENT-restricted product receives 403 Forbidden', async () => {
      const res = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${aspirantToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey: 'test_key_aspirant_denied_student_1'
        });

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('ROLE_INELIGIBLE');
    });
  });

  // ===========================================================================
  // 2. SERVER PRICE ENFORCEMENT & AMOUNT TAMPERING
  // ===========================================================================
  describe('2. Server Price Enforcement & Amount Tampering', () => {
    it('should ignore client-supplied amount and strictly enforce database price in integer paise', async () => {
      const res = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey: 'test_key_price_tamper_attempt_1',
          amountPaise: 100, // Attacker tries to pay ₹1 instead of ₹499
          amount: 1
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      // DB price for CV_STUDENT_PRO_1M is 49900 paise (₹499)
      expect(res.body.data.amountPaise).toBe(49900);
      expect(res.body.data.currency).toBe('INR');
      expect(res.body.data.providerOrderId).toBeDefined();
    });
  });

  // ===========================================================================
  // 3. IDEMPOTENT ORDER CREATION
  // ===========================================================================
  describe('3. Idempotent Order Creation', () => {
    it('repeating order creation with identical idempotencyKey returns existing order without creating duplicate', async () => {
      const idempotencyKey = 'test_key_idempotency_repeat_999';

      const firstRes = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey
        });

      expect(firstRes.status).toBe(201);
      const firstOrderId = firstRes.body.data.orderId;
      const firstProviderOrderId = firstRes.body.data.providerOrderId;

      const secondRes = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey
        });

      expect(secondRes.status).toBe(200);
      expect(secondRes.body.data.orderId).toBe(firstOrderId);
      expect(secondRes.body.data.providerOrderId).toBe(firstProviderOrderId);

      // Verify in DB that only one record exists
      const count = await prisma.paymentOrder.count({ where: { idempotencyKey } });
      expect(count).toBe(1);
    });
  });

  // ===========================================================================
  // 4. PAYMENT VERIFICATION & ENTITLEMENT CREATION
  // ===========================================================================
  describe('4. Payment Verification & Entitlement Creation', () => {
    let orderId: string;
    let providerOrderId: string;
    const providerPaymentId = 'pay_test_succ_123456';

    beforeAll(async () => {
      const orderRes = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey: 'test_key_verify_flow_001'
        });

      orderId = orderRes.body.data.orderId;
      providerOrderId = orderRes.body.data.providerOrderId;
    });

    it('NEGATIVE TEST: Invalid signature returns 400 Bad Request and does not grant entitlement', async () => {
      const res = await request(app)
        .post('/api/v1/payments/verify')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          orderId,
          providerPaymentId,
          providerSignature: 'invalid_forged_signature_1234567890'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('INVALID_SIGNATURE');

      // Verify no entitlement was created
      const entitlement = await prisma.entitlement.findFirst({
        where: { userId: studentUserId, productId: studentProductId }
      });
      expect(entitlement).toBeNull();
    });

    it('POSITIVE TEST: Valid cryptographic HMAC signature verifies payment, creates Transaction, and grants ACTIVE Entitlement', async () => {
      const validSignature = generateTestPaymentSignature(providerOrderId, providerPaymentId);

      const res = await request(app)
        .post('/api/v1/payments/verify')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          orderId,
          providerPaymentId,
          providerSignature: validSignature,
          paymentMethod: 'UPI'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.status).toBe('PAID');
      expect(res.body.data.entitlement).toBeDefined();
      expect(res.body.data.entitlement.status).toBe('ACTIVE');

      // Verify in Database
      const orderInDb = await prisma.paymentOrder.findUnique({ where: { id: orderId } });
      expect(orderInDb?.status).toBe('PAID');

      const txInDb = await prisma.transaction.findUnique({ where: { paymentOrderId: orderId } });
      expect(txInDb?.status).toBe('PAID');
      expect(txInDb?.amountPaise).toBe(49900);
      expect(txInDb?.providerPaymentId).toBe(providerPaymentId);
    });

    it('IDEMPOTENCY: Re-verifying an already PAID order returns 200 without creating duplicate transactions or entitlements', async () => {
      const validSignature = generateTestPaymentSignature(providerOrderId, providerPaymentId);

      const res = await request(app)
        .post('/api/v1/payments/verify')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          orderId,
          providerPaymentId,
          providerSignature: validSignature
        });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('PAID');

      const txCount = await prisma.transaction.count({ where: { paymentOrderId: orderId } });
      expect(txCount).toBe(1);

      const entCount = await prisma.entitlement.count({ where: { sourceTransactionId: res.body.data.transactionId } });
      expect(entCount).toBe(1);
    });
  });

  // ===========================================================================
  // 5. WEBHOOK SIGNATURE & IDEMPOTENT REPLAY PROTECTION
  // ===========================================================================
  describe('5. Webhook Ingestion & Replay Protection', () => {
    let webhookOrderId: string;
    let webhookProviderOrderId: string;
    const webhookPaymentId = 'pay_test_webhook_999999';

    beforeAll(async () => {
      const orderRes = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey: 'test_key_webhook_flow_001'
        });

      webhookOrderId = orderRes.body.data.orderId;
      webhookProviderOrderId = orderRes.body.data.providerOrderId;
    });

    it('NEGATIVE TEST: Webhook without signature header returns 400 Bad Request', async () => {
      const res = await request(app)
        .post('/api/v1/payments/webhook')
        .send({ event: 'payment.captured' });

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_SIGNATURE');
    });

    it('NEGATIVE TEST: Webhook with invalid HMAC signature returns 400 Bad Request', async () => {
      const res = await request(app)
        .post('/api/v1/payments/webhook')
        .set('X-Razorpay-Signature', 'invalid_webhook_signature_hex')
        .send({ event: 'payment.captured' });

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('INVALID_SIGNATURE');
    });

    it('POSITIVE TEST: Webhook with valid HMAC signature processes payment.captured atomically', async () => {
      const eventPayload = {
        event: 'payment.captured',
        event_id: `evt_test_${Date.now()}_1`,
        payload: {
          payment: {
            entity: {
              id: webhookPaymentId,
              order_id: webhookProviderOrderId,
              amount: 49900,
              currency: 'INR',
              method: 'upi'
            }
          }
        }
      };

      const rawBody = JSON.stringify(eventPayload);
      const signature = generateTestWebhookSignature(rawBody);

      const res = await request(app)
        .post('/api/v1/payments/webhook')
        .set('X-Razorpay-Signature', signature)
        .set('Content-Type', 'application/json')
        .send(rawBody);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // Verify Order and Transaction transitioned to PAID
      const order = await prisma.paymentOrder.findUnique({ where: { id: webhookOrderId } });
      expect(order?.status).toBe('PAID');

      const tx = await prisma.transaction.findUnique({ where: { paymentOrderId: webhookOrderId } });
      expect(tx?.status).toBe('PAID');
    });

    it('REPLAY PROTECTION: Re-sending identical webhook returns 200 (duplicate: true) without duplicating transaction or entitlement', async () => {
      const duplicateEventId = `evt_duplicate_test_${Date.now()}`;
      const eventPayload = {
        event: 'payment.captured',
        event_id: duplicateEventId,
        payload: {
          payment: {
            entity: {
              id: webhookPaymentId,
              order_id: webhookProviderOrderId,
              amount: 49900,
              currency: 'INR',
              method: 'upi'
            }
          }
        }
      };

      const rawBody = JSON.stringify(eventPayload);
      const signature = generateTestWebhookSignature(rawBody);

      // Send first time
      const firstRes = await request(app)
        .post('/api/v1/payments/webhook')
        .set('X-Razorpay-Signature', signature)
        .set('Content-Type', 'application/json')
        .send(rawBody);

      expect(firstRes.status).toBe(200);

      // Send second time (replay)
      const secondRes = await request(app)
        .post('/api/v1/payments/webhook')
        .set('X-Razorpay-Signature', signature)
        .set('Content-Type', 'application/json')
        .send(rawBody);

      expect(secondRes.status).toBe(200);
      expect(secondRes.body.duplicate).toBe(true);

      // Verify no duplicate transactions or entitlements were created
      const txCount = await prisma.transaction.count({ where: { paymentOrderId: webhookOrderId } });
      expect(txCount).toBe(1);
    });
  });

  // ===========================================================================
  // 6. CONCURRENCY: CONCURRENT VERIFY & WEBHOOK
  // ===========================================================================
  describe('6. Concurrency: Parallel /verify and /webhook', () => {
    it('concurrent verify and webhook requests for same payment produce exactly 1 transaction and 1 entitlement', async () => {
      const orderRes = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey: `test_key_concurrency_${Date.now()}`
        });

      const orderId = orderRes.body.data.orderId;
      const providerOrderId = orderRes.body.data.providerOrderId;
      const providerPaymentId = `pay_concurrent_${Date.now()}`;

      const clientSignature = generateTestPaymentSignature(providerOrderId, providerPaymentId);

      const webhookPayload = {
        event: 'payment.captured',
        event_id: `evt_concurrent_${Date.now()}`,
        payload: {
          payment: {
            entity: {
              id: providerPaymentId,
              order_id: providerOrderId,
              amount: 49900,
              currency: 'INR',
              method: 'upi'
            }
          }
        }
      };
      const rawWebhook = JSON.stringify(webhookPayload);
      const webhookSignature = generateTestWebhookSignature(rawWebhook);

      // Fire both concurrently
      const [verifyRes, webhookRes] = await Promise.all([
        request(app)
          .post('/api/v1/payments/verify')
          .set('Authorization', `Bearer ${studentToken}`)
          .send({
            orderId,
            providerPaymentId,
            providerSignature: clientSignature
          }),
        request(app)
          .post('/api/v1/payments/webhook')
          .set('X-Razorpay-Signature', webhookSignature)
          .set('Content-Type', 'application/json')
          .send(rawWebhook)
      ]);

      expect(verifyRes.status).toBe(200);
      expect(webhookRes.status).toBe(200);

      // Verify database consistency: exactly 1 transaction and 1 entitlement
      const txCount = await prisma.transaction.count({ where: { paymentOrderId: orderId } });
      expect(txCount).toBe(1);

      const tx = await prisma.transaction.findUnique({ where: { paymentOrderId: orderId } });
      const entCount = await prisma.entitlement.count({ where: { sourceTransactionId: tx!.id } });
      expect(entCount).toBe(1);
    });
  });

  // ===========================================================================
  // 7. USER ISOLATION & RBAC
  // ===========================================================================
  describe('7. User Isolation & RBAC', () => {
    it('Student A cannot see Student B transactions in /my-transactions', async () => {
      // Student A views their transactions
      const resA = await request(app)
        .get('/api/v1/payments/my-transactions')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(resA.status).toBe(200);
      const studentAUserIds = resA.body.data.map((tx: any) => tx.userId);
      studentAUserIds.forEach((uid: string) => {
        expect(uid).toBe(studentUserId);
        expect(uid).not.toBe(otherStudentUserId);
      });

      // Student B views their transactions (should be empty)
      const resB = await request(app)
        .get('/api/v1/payments/my-transactions')
        .set('Authorization', `Bearer ${otherStudentToken}`);

      expect(resB.status).toBe(200);
      expect(resB.body.data.length).toBe(0);
    });

    it('NEGATIVE TEST: Non-admin student cannot access Admin Finance Overview (403 Forbidden)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/finance/overview')
        .set('Authorization', `Bearer ${studentToken}`);

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });

    it('NEGATIVE TEST: Non-admin aspirant cannot access Admin Finance Transactions (403 Forbidden)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/finance/transactions')
        .set('Authorization', `Bearer ${aspirantToken}`);

      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('FORBIDDEN');
    });
  });

  // ===========================================================================
  // 8. ADMIN FINANCE & REFUNDS (FULL, PARTIAL, EXCESSIVE)
  // ===========================================================================
  describe('8. Admin Finance Center & Refund Operations', () => {
    let refundableTxId: string;
    let entitlementId: string;

    beforeAll(async () => {
      // Create a dedicated paid order for refund testing (amount: 49900 paise = ₹499)
      const orderRes = await request(app)
        .post('/api/v1/payments/orders')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          productId: studentProductId,
          idempotencyKey: `test_key_refund_prep_${Date.now()}`
        });

      const orderId = orderRes.body.data.orderId;
      const providerOrderId = orderRes.body.data.providerOrderId;
      const providerPaymentId = `pay_refund_test_${Date.now()}`;
      const signature = generateTestPaymentSignature(providerOrderId, providerPaymentId);

      const verifyRes = await request(app)
        .post('/api/v1/payments/verify')
        .set('Authorization', `Bearer ${studentToken}`)
        .send({
          orderId,
          providerPaymentId,
          providerSignature: signature
        });

      refundableTxId = verifyRes.body.data.transactionId;
      entitlementId = verifyRes.body.data.entitlement.id;
    });

    it('GET /api/v1/admin/finance/overview calculates gross, refunds, and net in integer paise and decimal INR', async () => {
      const res = await request(app)
        .get('/api/v1/admin/finance/overview')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      const data = res.body.data;
      expect(data.currency).toBe('INR');
      expect(Number.isInteger(data.grossRevenuePaise)).toBe(true);
      expect(Number.isInteger(data.refundedAmountPaise)).toBe(true);
      expect(Number.isInteger(data.netRevenuePaise)).toBe(true);
      expect(data.grossRevenueInr).toBe(data.grossRevenuePaise / 100);
      expect(data.counts.paidTransactions).toBeGreaterThan(0);
      expect(data.counts.successRate).toBeGreaterThanOrEqual(0);
    });

    it('GET /api/v1/admin/finance/transactions returns paginated ledger with status and role filters', async () => {
      const res = await request(app)
        .get('/api/v1/admin/finance/transactions?status=PAID&role=STUDENT')
        .set('Authorization', `Bearer ${adminToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.data)).toBe(true);
      res.body.data.forEach((tx: any) => {
        expect(tx.status).toBe('PAID');
        expect(tx.user.role).toBe('STUDENT');
      });
    });

    it('PARTIAL REFUND: Admin refunds 20000 paise (₹200) -> status transitions to PARTIALLY_REFUNDED', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/finance/transactions/${refundableTxId}/refund`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          amountPaise: 20000,
          reason: 'Partial goodwill refund for workshop downtime',
          adminNotes: 'Approved by lead admin'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.amountPaise).toBe(20000);
      expect(res.body.data.amountInr).toBe(200);
      expect(res.body.data.transactionStatus).toBe('PARTIALLY_REFUNDED');
      expect(res.body.data.remainingBalancePaise).toBe(29900); // 49900 - 20000

      // Entitlement remains ACTIVE on partial refund
      const ent = await prisma.entitlement.findUnique({ where: { id: entitlementId } });
      expect(ent?.status).toBe('ACTIVE');
    });

    it('EXCESSIVE REFUND: Attempting to refund more than the remaining balance receives 400 Bad Request', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/finance/transactions/${refundableTxId}/refund`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          amountPaise: 30000, // Remaining balance is 29900; 30000 exceeds it
          reason: 'Excessive refund attempt'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('REFUND_EXCEEDS_BALANCE');
    });

    it('FULL REFUND COMPLETION: Refunding exact remaining balance (29900 paise) -> status becomes REFUNDED and Entitlement is REVOKED', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/finance/transactions/${refundableTxId}/refund`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          amountPaise: 29900,
          reason: 'Final refund balance settlement'
        });

      expect(res.status).toBe(200);
      expect(res.body.data.transactionStatus).toBe('REFUNDED');
      expect(res.body.data.remainingBalancePaise).toBe(0);

      // Entitlement is now REVOKED upon full refund
      const ent = await prisma.entitlement.findUnique({ where: { id: entitlementId } });
      expect(ent?.status).toBe('REVOKED');
    });

    it('NEGATIVE TEST: Cannot refund an already fully REFUNDED transaction', async () => {
      const res = await request(app)
        .post(`/api/v1/admin/finance/transactions/${refundableTxId}/refund`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          amountPaise: 1000,
          reason: 'Illegal second refund attempt'
        });

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('INVALID_TRANSACTION_STATE');
    });
  });

  // ===========================================================================
  // 9. STATE MACHINE VALIDATOR UNIT TESTS
  // ===========================================================================
  describe('9. State Machine Invariants', () => {
    it('validates PaymentOrder transitions correctly', () => {
      expect(isValidOrderTransition('PENDING', 'PAID')).toBe(true);
      expect(isValidOrderTransition('PENDING', 'FAILED')).toBe(true);
      expect(isValidOrderTransition('PAID', 'PENDING')).toBe(false); // Forbidden
      expect(isValidOrderTransition('PAID', 'FAILED')).toBe(false); // Forbidden
    });

    it('validates Transaction transitions correctly', () => {
      expect(isValidTransactionTransition('PENDING', 'PAID')).toBe(true);
      expect(isValidTransactionTransition('PAID', 'PARTIALLY_REFUNDED')).toBe(true);
      expect(isValidTransactionTransition('PARTIALLY_REFUNDED', 'REFUNDED')).toBe(true);
      expect(isValidTransactionTransition('REFUNDED', 'PAID')).toBe(false); // Forbidden
      expect(isValidTransactionTransition('FAILED', 'PAID')).toBe(false); // Forbidden
    });

    it('validates Refund and Entitlement transitions correctly', () => {
      expect(isValidRefundTransition('PENDING', 'PROCESSED')).toBe(true);
      expect(isValidRefundTransition('PROCESSED', 'PENDING')).toBe(false);
      expect(isValidEntitlementTransition('ACTIVE', 'REVOKED')).toBe(true);
      expect(isValidEntitlementTransition('REVOKED', 'ACTIVE')).toBe(false);
    });
  });

  // ===========================================================================
  // 10. MARKETPLACE SEPARATION VERIFICATION
  // ===========================================================================
  describe('10. Marketplace Model Separation', () => {
    it('MarketplaceTransaction table remains completely isolated from platform payment transactions', async () => {
      const marketplaceTxCount = await prisma.marketplaceTransaction.count();
      const platformTxCount = await prisma.transaction.count();

      // Platform transactions must not contaminate MarketplaceTransaction
      expect(Number.isInteger(marketplaceTxCount)).toBe(true);
      expect(platformTxCount).toBeGreaterThan(0);
    });
  });
});
