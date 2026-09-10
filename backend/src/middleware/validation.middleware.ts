import { Request, Response, NextFunction } from 'express';
import { ZodSchema, ZodError } from 'zod';
import { sendError } from '../utils/response';

export function validateBody(schema: ZodSchema) {
  return (req: Request, res: Response, next: NextFunction): void => {
    try {
      req.body = schema.parse(req.body);
      next();
    } catch (error) {
      if (error instanceof ZodError) {
        const issues = error.issues.map(i => ({
          field: i.path.join('.'),
          message: i.message
        }));
        sendError(res, 'Validation failed for request payload.', 400, 'VALIDATION_ERROR', issues);
        return;
      }
      sendError(res, 'Invalid request format.', 400, 'BAD_REQUEST');
    }
  };
}

export function validateQuery(schema: ZodSchema) {
  return (req: Request, res: Response, next: NextFunction): void => {
    try {
      req.query = schema.parse(req.query);
      next();
    } catch (error) {
      if (error instanceof ZodError) {
        const issues = error.issues.map(i => ({
          field: i.path.join('.'),
          message: i.message
        }));
        sendError(res, 'Validation failed for query parameters.', 400, 'VALIDATION_ERROR', issues);
        return;
      }
      sendError(res, 'Invalid query parameters.', 400, 'BAD_REQUEST');
    }
  };
}
