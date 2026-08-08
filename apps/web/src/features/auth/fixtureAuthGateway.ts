import type { AuthenticatedSession, AuthGateway, AuthProblem } from './authGateway';

export const FIXTURE_ADMIN_EMAIL = 'admin@talon.demo';

const FIXTURE_ADMIN_SESSION: AuthenticatedSession = {
  userId: 'fixture-workspace-admin',
  workspaceId: 'fixture-talon-workspace',
  displayName: 'Maya Reyes',
  workspaceName: 'Talon Demo',
  role: 'WORKSPACE_ADMIN',
};

function invalidCredentialsProblem(): AuthProblem {
  return Object.assign(new Error('The supplied credentials were not accepted'), {
    code: 'INVALID_CREDENTIALS' as const,
  });
}

export function createFixtureAuthGateway(): AuthGateway {
  let currentSession: AuthenticatedSession | null = null;

  return {
    async restoreSession() {
      return currentSession;
    },
    async login(credentials) {
      const normalizedEmail = credentials.email.trim().toLowerCase();

      if (normalizedEmail !== FIXTURE_ADMIN_EMAIL || credentials.password.length === 0) {
        throw invalidCredentialsProblem();
      }

      currentSession = FIXTURE_ADMIN_SESSION;
      return currentSession;
    },
    async logout() {
      currentSession = null;
    },
  };
}
