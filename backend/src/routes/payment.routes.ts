import { Router } from 'express';
import {
  getProducts,
  createPaymentOrder,
  verifyPayment,
  handleWebhook,
  getMyTransactions,
  getMyEntitlements,
  createOrderSchema,
  verifyPaymentSchema
} from '../controllers/payment.controller';
import { requireAuth } from '../middleware/auth.middleware';
import { validateBody } from '../middleware/validation.middleware';

const router = Router();

// Webhook endpoint (Signature verified via HMAC header, NO session token)
router.post('/webhook', handleWebhook);

// Customer endpoints (Session authentication required)
router.get('/products', requireAuth, getProducts);
router.post('/orders', requireAuth, validateBody(createOrderSchema), createPaymentOrder);
router.post('/verify', requireAuth, validateBody(verifyPaymentSchema), verifyPayment);
router.get('/my-transactions', requireAuth, getMyTransactions);
router.get('/my-entitlements', requireAuth, getMyEntitlements);

export default router;
