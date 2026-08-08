import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { CandidateWorkspace } from './CandidateWorkspace';
import { CandidateGatewayError } from './candidateGateway';
import { createFixtureCandidateGateway } from './fixtureCandidateGateway';

describe('CandidateWorkspace', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    Reflect.deleteProperty(URL, 'createObjectURL');
    Reflect.deleteProperty(URL, 'revokeObjectURL');
  });

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
    expect(screen.getByRole('button', { name: 'Open Nila Raman application' })).toBeInTheDocument();
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

  it('opens the selected application profile with additional answers and clean resume access', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    const createObjectURL = vi.fn(() => 'blob:fixture-resume');
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });
    const anchorClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    const downloadResume = vi
      .fn()
      .mockImplementation((applicationId: string) => fixtureGateway.downloadResume(applicationId));
    render(
      <CandidateWorkspace
        candidateGateway={{ ...fixtureGateway, downloadResume }}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Nila Raman application' }));

    expect(await screen.findByRole('heading', { name: 'Nila Raman' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Nila Raman' })).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Open Nila Raman application' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    expect(screen.getByText('nila.raman@example.test')).toBeInTheDocument();
    expect(screen.getByText('+91 ••••• 0184')).toBeInTheDocument();
    expect(screen.getByText('Google Forms')).toBeInTheDocument();
    expect(screen.getByText('1 Sep 2026')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Additional form answers' })).toBeInTheDocument();
    expect(screen.getByText('Why are you interested in this role?')).toBeInTheDocument();
    expect(
      screen.getByText('Building reliable hiring systems at product scale.'),
    ).toBeInTheDocument();
    expect(screen.getByText('nila-raman-resume.pdf')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Download clean resume' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Resume download started.');
    expect(downloadResume).toHaveBeenCalledWith('application-nila-backend');
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    expect(anchorClick).toHaveBeenCalledOnce();
    expect(anchorClick.mock.contexts[0]).toHaveProperty('download', 'nila-raman-resume.pdf');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:fixture-resume');

    await user.click(screen.getByRole('button', { name: 'Close profile' }));
    expect(screen.getByRole('button', { name: 'Open Nila Raman application' })).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Open Nila Raman application' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.queryByRole('heading', { name: 'Nila Raman' })).not.toBeInTheDocument();
  });

  it('does not offer resume download until the file is clean', async () => {
    const user = userEvent.setup();
    render(
      <CandidateWorkspace
        candidateGateway={createFixtureCandidateGateway()}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Kai Sen application' }));

    expect(await screen.findByRole('heading', { name: 'Kai Sen' })).toBeInTheDocument();
    expect(screen.getByText('Resume scan pending')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download clean resume' })).not.toBeInTheDocument();
  });

  it('removes a stale resume download action after an authorization rejection', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    render(
      <CandidateWorkspace
        candidateGateway={{
          ...fixtureGateway,
          downloadResume: async () => {
            throw new CandidateGatewayError(
              'RESUME_DOWNLOAD_FORBIDDEN',
              'private authorization detail',
            );
          },
        }}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Nila Raman application' }));
    await user.click(await screen.findByRole('button', { name: 'Download clean resume' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Resume download is no longer available.',
    );
    expect(screen.queryByText(/private authorization detail/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download clean resume' })).not.toBeInTheDocument();
  });

  it('announces profile loading before showing the selected application', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    let resolveDetail:
      ((detail: Awaited<ReturnType<typeof fixtureGateway.getApplication>>) => void) | undefined;
    const detailPromise = new Promise<Awaited<ReturnType<typeof fixtureGateway.getApplication>>>(
      (resolve) => {
        resolveDetail = resolve;
      },
    );

    render(
      <CandidateWorkspace
        candidateGateway={{ ...fixtureGateway, getApplication: () => detailPromise }}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Nila Raman application' }));
    expect(screen.getByRole('status')).toHaveTextContent('Loading Nila Raman profile…');
    expect(screen.getByRole('button', { name: 'Open Nila Raman application' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );

    resolveDetail?.(await fixtureGateway.getApplication('application-nila-backend'));
    expect(await screen.findByRole('heading', { name: 'Nila Raman' })).toBeInTheDocument();
  });

  it('shows a safe candidate profile error and retries the selected application', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    const getApplication = vi
      .fn()
      .mockRejectedValueOnce(
        new CandidateGatewayError('UNAVAILABLE', 'provider token should stay hidden'),
      )
      .mockImplementation((applicationId: string) => fixtureGateway.getApplication(applicationId));

    render(
      <CandidateWorkspace
        candidateGateway={{ ...fixtureGateway, getApplication }}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Nila Raman application' }));
    expect(
      await screen.findByRole('alert', { name: 'Candidate profile could not be loaded.' }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/provider token/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry candidate profile' }));
    expect(await screen.findByRole('heading', { name: 'Nila Raman' })).toBeInTheDocument();
    expect(getApplication).toHaveBeenCalledTimes(2);
  });

  it('keeps the newest profile selection when requests resolve out of order', async () => {
    const user = userEvent.setup();
    const fixtureGateway = createFixtureCandidateGateway();
    const details = new Map<
      string,
      (detail: Awaited<ReturnType<typeof fixtureGateway.getApplication>>) => void
    >();
    const getApplication = vi.fn(
      (applicationId: string) =>
        new Promise<Awaited<ReturnType<typeof fixtureGateway.getApplication>>>((resolve) => {
          details.set(applicationId, resolve);
        }),
    );

    render(
      <CandidateWorkspace
        candidateGateway={{ ...fixtureGateway, getApplication }}
        role="WORKSPACE_ADMIN"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Nila Raman application' }));
    await user.click(screen.getByRole('button', { name: 'Open Kai Sen application' }));

    await act(async () => {
      details.get('application-kai-product')?.(
        await fixtureGateway.getApplication('application-kai-product'),
      );
    });
    expect(await screen.findByRole('heading', { name: 'Kai Sen' })).toBeInTheDocument();

    await act(async () => {
      details.get('application-nila-backend')?.(
        await fixtureGateway.getApplication('application-nila-backend'),
      );
    });
    expect(screen.getByRole('heading', { name: 'Kai Sen' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Nila Raman' })).not.toBeInTheDocument();
  });

  it('keeps application compensation out of both list and profile for hiring managers', async () => {
    const user = userEvent.setup();
    render(
      <CandidateWorkspace
        candidateGateway={createFixtureCandidateGateway({
          viewCompensation: false,
          downloadCleanResumes: false,
        })}
        role="HIRING_MANAGER"
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Open Nila Raman application' }));

    expect(await screen.findByRole('heading', { name: 'Nila Raman' })).toBeInTheDocument();
    expect(screen.queryByText('Current ₹32.00 LPA')).not.toBeInTheDocument();
    expect(screen.queryByText('Expected ₹40.00 LPA')).not.toBeInTheDocument();
    expect(
      screen.getByText('Compensation is available only to Admin and Recruiter roles.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download clean resume' })).not.toBeInTheDocument();
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
        candidateGateway={{ ...fixtureGateway, listApplications: async () => [] }}
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
