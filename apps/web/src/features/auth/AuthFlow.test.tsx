import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import App from '../../app/App';
import type { AuthenticatedSession, AuthProblemCode } from './authGateway';
import { createFixtureImportGateway } from '../imports/fixtureImportGateway';
import { createFixtureJobGateway } from '../jobs/fixtureJobGateway';
import { SignInPage } from './SignInPage';

describe('authentication routing', () => {
  it('redirects an unauthenticated candidate route visit to sign in', async () => {
    window.history.replaceState({}, '', '/candidates');

    render(
      <App
        authGateway={{
          login: async () => {
            throw new Error('Login is not used while restoring this route');
          },
          logout: async () => undefined,
          restoreSession: async () => null,
        }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/sign-in');
    expect(screen.queryByRole('heading', { name: 'Candidates' })).not.toBeInTheDocument();
  });

  it('returns to the requested priority route after sign in', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports');

    render(
      <App
        authGateway={{
          login: async () => ({
            userId: 'user-demo-admin',
            displayName: 'Maya Reyes',
            workspaceName: 'Talon Demo',
            role: 'WORKSPACE_ADMIN',
          }),
          logout: async () => undefined,
          restoreSession: async () => null,
        }}
      />,
    );

    await user.type(
      await screen.findByRole('textbox', { name: 'Work email' }),
      'admin@example.test',
    );
    await user.type(screen.getByLabelText('Password'), 'temporary-input');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Import applications' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/imports');
  });

  it('preserves a validated opaque import ID while requiring sign in', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports?importId=fixture-import-001');

    render(
      <App
        authGateway={{
          login: async () => ({
            userId: 'user-demo-admin',
            displayName: 'Maya Reyes',
            workspaceName: 'Talon Demo',
            role: 'WORKSPACE_ADMIN',
          }),
          logout: async () => undefined,
          restoreSession: async () => null,
        }}
        importGateway={createFixtureImportGateway()}
        jobGateway={createFixtureJobGateway()}
      />,
    );

    await user.type(
      await screen.findByRole('textbox', { name: 'Work email' }),
      'admin@example.test',
    );
    await user.type(screen.getByLabelText('Password'), 'temporary-input');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Import results' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/imports');
    expect(window.location.search).toBe('?importId=fixture-import-001');
  });

  it('restores an authenticated session into the requested protected route', async () => {
    window.history.replaceState({}, '', '/search');

    render(
      <App
        authGateway={{
          login: async () => {
            throw new Error('Login is not used for a restored session');
          },
          logout: async () => undefined,
          restoreSession: async () => ({
            userId: 'user-demo-admin',
            displayName: 'Maya Reyes',
            workspaceName: 'Talon Demo',
            role: 'WORKSPACE_ADMIN',
          }),
        }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Search' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/search');
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument();
  });

  it('waits for logout before returning to sign in', async () => {
    const user = userEvent.setup();
    let finishLogout: (() => void) | undefined;
    const logoutResult = new Promise<void>((resolve) => {
      finishLogout = resolve;
    });
    window.history.replaceState({}, '', '/candidates');

    render(
      <App
        authGateway={{
          login: async () => {
            throw new Error('Login is not used for a restored session');
          },
          logout: async () => logoutResult,
          restoreSession: async () => ({
            userId: 'user-demo-admin',
            displayName: 'Maya Reyes',
            workspaceName: 'Talon Demo',
            role: 'WORKSPACE_ADMIN',
          }),
        }}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(screen.getByRole('button', { name: 'Signing out…' })).toBeDisabled();
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument();

    finishLogout?.();

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/sign-in');
  });

  it('explains when session restoration expired', async () => {
    const expiredSession = Object.assign(new Error('Refresh token rejected'), {
      code: 'SESSION_EXPIRED',
    });
    window.history.replaceState({}, '', '/imports');

    render(
      <App
        authGateway={{
          login: async () => {
            throw new Error('Login is not used while restoring this route');
          },
          logout: async () => undefined,
          restoreSession: async () => {
            throw expiredSession;
          },
        }}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Your session expired. Sign in again to continue.',
    );
    expect(screen.getByRole('status')).not.toHaveTextContent('Refresh token rejected');
    expect(window.location.pathname).toBe('/sign-in');
  });

  it('provides labeled credentials and an accessible password visibility control', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/sign-in');

    render(
      <App
        authGateway={{
          login: async () => {
            throw new Error('Login is not used in this test');
          },
          logout: async () => undefined,
          restoreSession: async () => null,
        }}
      />,
    );

    const email = await screen.findByRole('textbox', { name: 'Work email' });
    const password = screen.getByLabelText('Password');

    expect(email).toHaveAttribute('type', 'email');
    expect(password).toHaveAttribute('type', 'password');

    await user.click(screen.getByRole('button', { name: 'Show password' }));

    expect(password).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Hide password' })).toBeInTheDocument();
  });

  it('disables submit while login is pending before entering candidates', async () => {
    const user = userEvent.setup();
    let finishLogin: ((session: AuthenticatedSession) => void) | undefined;
    const loginResult = new Promise<AuthenticatedSession>((resolve) => {
      finishLogin = resolve;
    });
    window.history.replaceState({}, '', '/sign-in');

    render(
      <App
        authGateway={{
          login: async () => loginResult,
          logout: async () => undefined,
          restoreSession: async () => null,
        }}
      />,
    );

    await user.type(
      await screen.findByRole('textbox', { name: 'Work email' }),
      'admin@example.test',
    );
    await user.type(screen.getByLabelText('Password'), 'temporary-input');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled();

    finishLogin?.({
      userId: 'user-demo-admin',
      displayName: 'Maya Reyes',
      workspaceName: 'Talon Demo',
      role: 'WORKSPACE_ADMIN',
    });

    expect(await screen.findByRole('heading', { name: 'Candidates' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/candidates');
  });

  it('does not reveal whether an account exists when credentials are invalid', async () => {
    const user = userEvent.setup();
    const invalidCredentials = Object.assign(new Error('No user matched this email'), {
      code: 'INVALID_CREDENTIALS',
    });
    window.history.replaceState({}, '', '/sign-in');

    render(
      <App
        authGateway={{
          login: async () => {
            throw invalidCredentials;
          },
          logout: async () => undefined,
          restoreSession: async () => null,
        }}
      />,
    );

    await user.type(
      await screen.findByRole('textbox', { name: 'Work email' }),
      'unknown@example.test',
    );
    await user.type(screen.getByLabelText('Password'), 'incorrect-input');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We couldn’t sign you in with those credentials. Check your email and password and try again.',
    );
    expect(screen.getByRole('alert')).not.toHaveTextContent('No user matched this email');
  });

  it.each<readonly [AuthProblemCode, string]>([
    ['RATE_LIMITED', 'Too many sign-in attempts. Wait a few minutes and try again.'],
    [
      'ACCOUNT_LOCKED',
      'Sign-in is temporarily locked. Try again later or contact your workspace administrator.',
    ],
    ['API_UNAVAILABLE', 'Talon can’t reach the sign-in service right now. Try again.'],
  ])('shows actionable recovery for %s', async (code, expectedMessage) => {
    const user = userEvent.setup();
    const problem = Object.assign(new Error('Unsafe provider detail'), { code });

    render(
      <SignInPage
        onLogin={async () => {
          throw problem;
        }}
      />,
    );

    await user.type(screen.getByRole('textbox', { name: 'Work email' }), 'admin@example.test');
    await user.type(screen.getByLabelText('Password'), 'temporary-input');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(expectedMessage);
    expect(screen.getByRole('alert')).not.toHaveTextContent('Unsafe provider detail');
  });
});
