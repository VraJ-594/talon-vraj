export type AuthProblemCode =
  'INVALID_CREDENTIALS' | 'ACCOUNT_LOCKED' | 'RATE_LIMITED' | 'API_UNAVAILABLE' | 'SESSION_EXPIRED';

export type AuthProblem = Error & {
  readonly code: AuthProblemCode;
};

export function isAuthProblem(error: unknown): error is AuthProblem {
  return error instanceof Error && 'code' in error;
}

export type WorkspaceRole = 'WORKSPACE_ADMIN' | 'RECRUITER' | 'HIRING_MANAGER' | 'INTERVIEWER';

export type AuthenticatedSession = {
  readonly userId: string;
  readonly displayName: string;
  readonly workspaceName: string;
  readonly role: WorkspaceRole;
};

export type LoginCredentials = {
  readonly email: string;
  readonly password: string;
};

export interface AuthGateway {
  restoreSession(): Promise<AuthenticatedSession | null>;
  login(credentials: LoginCredentials): Promise<AuthenticatedSession>;
  logout(): Promise<void>;
}
