export interface AuthUserPayload {
  userId: string;
  email: string;
  role: string;
  isAdminAuthorized: boolean;
  sessionVersion?: number;
  jti?: string;
  roles?: string[];
  permissions?: string[];
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthUserPayload;
      token?: string;
      sessionId?: string;
      jti?: string;
    }
  }
}

