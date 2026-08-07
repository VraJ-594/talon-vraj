import type {
  AuthenticatedSession,
  AuthGateway,
  AuthProblem,
  AuthProblemCode,
  LoginCredentials,
  WorkspaceRole,
} from './authGateway';
import type { ApiClient } from '../../lib/apiClient';

const AUTH_PROBLEM_CODES: readonly AuthProblemCode[] = [
  'INVALID_CREDENTIALS',
  'ACCOUNT_LOCKED',
  'RATE_LIMITED',
  'API_UNAVAILABLE',
  'SESSION_EXPIRED',
];

const AUTHENTICATED_ROLES: readonly WorkspaceRole[] = ['WORKSPACE_ADMIN', 'RECRUITER'];

type LoginResponse = AuthenticatedSession & {
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
};

function authProblem(code: AuthProblemCode): AuthProblem {
  return Object.assign(new Error('Authentication request could not be completed'), { code });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isAuthenticatedRole(value: unknown): value is WorkspaceRole {
  return typeof value === 'string' && AUTHENTICATED_ROLES.some((role) => role === value);
}

function isIsoInstant(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?Z$/.exec(value);
  if (!match) return false;

  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) return false;
  return (
    instant.getUTCFullYear() === Number(match[1]) &&
    instant.getUTCMonth() + 1 === Number(match[2]) &&
    instant.getUTCDate() === Number(match[3]) &&
    instant.getUTCHours() === Number(match[4]) &&
    instant.getUTCMinutes() === Number(match[5]) &&
    instant.getUTCSeconds() === Number(match[6])
  );
}

function readSession(value: unknown): AuthenticatedSession | null {
  if (
    !isRecord(value) ||
    !isNonEmptyString(value.userId) ||
    !isNonEmptyString(value.workspaceId) ||
    !isNonEmptyString(value.workspaceName) ||
    !isNonEmptyString(value.displayName) ||
    !isAuthenticatedRole(value.role)
  ) {
    return null;
  }

  return {
    userId: value.userId,
    workspaceId: value.workspaceId,
    workspaceName: value.workspaceName,
    role: value.role,
    displayName: value.displayName,
  };
}

function readLoginResponse(value: unknown): LoginResponse | null {
  const session = readSession(value);
  if (
    !session ||
    !isRecord(value) ||
    !isNonEmptyString(value.accessToken) ||
    !isIsoInstant(value.accessTokenExpiresAt)
  ) {
    return null;
  }

  return {
    ...session,
    accessToken: value.accessToken,
    accessTokenExpiresAt: value.accessTokenExpiresAt,
  };
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function fallbackProblemCode(response: Response, request: 'LOGIN' | 'SESSION'): AuthProblemCode {
  if (response.status === 429) return 'RATE_LIMITED';
  if (response.status === 423) return 'ACCOUNT_LOCKED';
  if (response.status === 401) {
    return request === 'LOGIN' ? 'INVALID_CREDENTIALS' : 'SESSION_EXPIRED';
  }
  return 'API_UNAVAILABLE';
}

async function responseProblem(
  response: Response,
  request: 'LOGIN' | 'SESSION',
): Promise<AuthProblem> {
  const body = await readJson(response);
  const code = isRecord(body) ? body.code : undefined;
  if (typeof code === 'string' && AUTH_PROBLEM_CODES.some((candidate) => candidate === code)) {
    return authProblem(code as AuthProblemCode);
  }
  return authProblem(fallbackProblemCode(response, request));
}

export class HttpAuthGateway implements AuthGateway {
  private currentSession: AuthenticatedSession | null = null;

  constructor(private readonly apiClient: ApiClient) {}

  async login(credentials: LoginCredentials): Promise<AuthenticatedSession> {
    let response: Response;
    try {
      response = await this.apiClient.request('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
      });
    } catch {
      throw authProblem('API_UNAVAILABLE');
    }

    if (!response.ok) throw await responseProblem(response, 'LOGIN');

    const result = readLoginResponse(await readJson(response));
    if (!result) throw authProblem('API_UNAVAILABLE');

    this.apiClient.setAccessToken(result.accessToken);
    const session: AuthenticatedSession = {
      userId: result.userId,
      workspaceId: result.workspaceId,
      workspaceName: result.workspaceName,
      role: result.role,
      displayName: result.displayName,
    };
    this.currentSession = session;
    return session;
  }

  async restoreSession(): Promise<AuthenticatedSession | null> {
    if (!this.apiClient.hasAccessToken()) return null;

    let response: Response;
    try {
      response = await this.apiClient.request('/api/v1/session', { method: 'GET' }, true);
    } catch {
      throw authProblem('API_UNAVAILABLE');
    }

    if (!response.ok) {
      const problem = await responseProblem(response, 'SESSION');
      if (problem.code === 'SESSION_EXPIRED') this.clearSession();
      throw problem;
    }

    const session = readSession(await readJson(response));
    if (!session) throw authProblem('API_UNAVAILABLE');
    this.currentSession = session;
    return session;
  }

  async logout(): Promise<void> {
    this.clearSession();
  }

  private clearSession() {
    this.apiClient.clearAccessToken();
    this.currentSession = null;
  }
}
