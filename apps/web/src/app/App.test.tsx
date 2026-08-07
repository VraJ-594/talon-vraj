import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';

import App from './App';
import type { AuthGateway } from '../features/auth/authGateway';

const authenticatedGateway: AuthGateway = {
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
};

describe('Talon application shell', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/candidates');
  });

  it('gives administrators the three priority destinations', async () => {
    render(<App authGateway={authenticatedGateway} />);

    expect(await screen.findByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Candidates' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Import applications' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Search' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Reports' })).not.toBeInTheDocument();
  });

  it('marks the current priority destination in the navigation', async () => {
    render(<App authGateway={authenticatedGateway} />);

    expect(await screen.findByRole('heading', { name: 'Candidates' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Candidates' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });
});
