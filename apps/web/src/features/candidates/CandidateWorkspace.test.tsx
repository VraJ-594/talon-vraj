import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { CandidateWorkspace } from './CandidateWorkspace';
import { CandidateGatewayError } from './candidateGateway';
import { createFixtureCandidateGateway } from './fixtureCandidateGateway';

describe('CandidateWorkspace', () => {
  it('shows the approved candidate and application projection for an administrator', async () => {
    render(
      <CandidateWorkspace
        candidateGateway={createFixtureCandidateGateway()}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(
      await screen.findByRole('heading', { name: 'Application pipeline' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open Nila Raman application' })).toHaveAttribute(
      'href',
      '/candidates/applications/application-nila-backend',
    );
    expect(screen.getByText('Senior Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Screening')).toBeInTheDocument();
    expect(screen.getByText('Pune')).toBeInTheDocument();
    expect(screen.getByText('7y 6m')).toBeInTheDocument();
    expect(screen.getByText('Atlas Systems · Staff Engineer')).toBeInTheDocument();
    expect(screen.getByText('TypeScript')).toBeInTheDocument();
    expect(screen.getByText('Current ₹32.00 LPA')).toBeInTheDocument();
    expect(screen.getByText('Expected ₹40.00 LPA')).toBeInTheDocument();
    expect(screen.getByText('30 days')).toBeInTheDocument();
    expect(screen.getByText('6 Aug 2026')).toBeInTheDocument();
    expect(screen.getByText('Resume clean')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Candidate application roster' })).toHaveAttribute(
      'tabindex',
      '0',
    );
  });

  it('opens an application on a dedicated candidate profile route', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/candidates');
    render(
      <CandidateWorkspace
        candidateGateway={createFixtureCandidateGateway()}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('link', { name: 'Open Nila Raman application' }));

    expect(window.location.pathname).toBe('/candidates/applications/application-nila-backend');
  });

  it('shows when an application has no uploaded resume', async () => {
    const fixtureGateway = createFixtureCandidateGateway();
    const fixturePage = await fixtureGateway.listApplications();
    render(
      <CandidateWorkspace
        candidateGateway={{
          ...fixtureGateway,
          listApplications: async () => ({
            items: [{ ...fixturePage.items[0], resumeStatus: 'NO_RESUME' }],
            nextCursor: null,
          }),
        }}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(await screen.findByText('No resume uploaded')).toBeInTheDocument();
  });

  it('loads the next application page without replacing existing rows', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    const fixturePage = await fixtureGateway.listApplications();
    const listApplications = vi.fn(async (cursor?: string | null) =>
      cursor
        ? { items: [fixturePage.items[1]], nextCursor: null }
        : { items: [fixturePage.items[0]], nextCursor: 'opaque-page-2' },
    );
    render(
      <CandidateWorkspace
        candidateGateway={{ ...fixtureGateway, listApplications }}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(await screen.findByText('Nila Raman')).toBeInTheDocument();
    expect(screen.queryByText('Kai Sen')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Load more applications' }));

    expect(await screen.findByText('Kai Sen')).toBeInTheDocument();
    expect(screen.getByText('Nila Raman')).toBeInTheDocument();
    expect(listApplications).toHaveBeenNthCalledWith(2, 'opaque-page-2');
  });

  it('keeps application compensation out of the list for hiring managers', async () => {
    render(
      <CandidateWorkspace
        candidateGateway={createFixtureCandidateGateway({
          viewCompensation: false,
          downloadCleanResumes: false,
        })}
        role="HIRING_MANAGER"
      />,
    );

    expect(await screen.findByText('Nila Raman')).toBeInTheDocument();
    expect(screen.queryByText('Current ₹32.00 LPA')).not.toBeInTheDocument();
    expect(screen.queryByText('Expected ₹40.00 LPA')).not.toBeInTheDocument();
    expect(screen.getAllByText('Compensation restricted')).toHaveLength(2);
  });

  it('announces that candidate applications are loading', () => {
    const fixtureGateway = createFixtureCandidateGateway();
    render(
      <CandidateWorkspace
        candidateGateway={{
          ...fixtureGateway,
          listApplications: () => new Promise(() => undefined),
        }}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Loading candidate applications…');
    expect(screen.queryByText('0 records')).not.toBeInTheDocument();
  });

  it('offers the import path when there are no candidate applications', async () => {
    const fixtureGateway = createFixtureCandidateGateway();
    render(
      <CandidateWorkspace
        candidateGateway={{
          ...fixtureGateway,
          listApplications: async () => ({ items: [], nextCursor: null }),
        }}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(await screen.findByText('No candidate applications yet.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Import candidates' })).toHaveAttribute(
      'href',
      '/imports',
    );
  });

  it('shows a safe recoverable error and retries the candidate list', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    const listApplications = vi
      .fn()
      .mockRejectedValueOnce(
        new CandidateGatewayError('UNAVAILABLE', 'database endpoint secret should stay hidden'),
      )
      .mockImplementation(() => fixtureGateway.listApplications());

    render(
      <CandidateWorkspace
        candidateGateway={{ ...fixtureGateway, listApplications }}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(
      await screen.findByRole('alert', {
        name: 'Candidate applications could not be loaded.',
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/database endpoint secret/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry candidate applications' }));

    expect(await screen.findByText('Nila Raman')).toBeInTheDocument();
    expect(listApplications).toHaveBeenCalledTimes(2);
  });

  it('shows a safe forbidden state without a retry action', async () => {
    const fixtureGateway = createFixtureCandidateGateway();
    render(
      <CandidateWorkspace
        candidateGateway={{
          ...fixtureGateway,
          listApplications: async () => {
            throw new CandidateGatewayError(
              'FORBIDDEN',
              'workspace membership record missing from provider',
            );
          },
        }}
        role="WORKSPACE_ADMIN"
      />,
    );

    expect(
      await screen.findByRole('alert', {
        name: "You don't have access to candidate applications.",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/membership record/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Retry candidate applications' }),
    ).not.toBeInTheDocument();
  });
});
