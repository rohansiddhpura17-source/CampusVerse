export interface AuthUserPayload {
  userId: string;
  email: string;
  role: string;
  isAdminAuthorized: boolean;
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthUserPayload;
    }
  }
}
