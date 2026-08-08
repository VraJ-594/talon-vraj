import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { createFixtureCandidateGateway } from './fixtureCandidateGateway';
import { CandidateGatewayError } from './candidateGateway';
import { CandidateApplicationProfilePage } from './CandidateApplicationProfilePage';

describe('CandidateApplicationProfilePage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    Reflect.deleteProperty(URL, 'createObjectURL');
    Reflect.deleteProperty(URL, 'revokeObjectURL');
  });

  it('loads a direct application profile and provides a back action', async () => {
    const user = userEvent.setup();
    const onBack = vi.fn();

    render(
      <CandidateApplicationProfilePage
        applicationId="application-nila-backend"
        candidateGateway={createFixtureCandidateGateway()}
        role="WORKSPACE_ADMIN"
        onBack={onBack}
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Loading candidate profile…');
    expect(await screen.findByRole('heading', { name: 'Nila Raman' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Back to candidates' }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it('retries a safe transient detail failure', async () => {
    const user = userEvent.setup();
    const fixture = createFixtureCandidateGateway();
    const getApplication = vi
      .fn()
      .mockRejectedValueOnce(new CandidateGatewayError('UNAVAILABLE', 'private database detail'))
      .mockImplementation((applicationId: string) => fixture.getApplication(applicationId));

    render(
      <CandidateApplicationProfilePage
        applicationId="application-nila-backend"
        candidateGateway={{ ...fixture, getApplication }}
        role="WORKSPACE_ADMIN"
        onBack={vi.fn()}
      />,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Candidate profile could not be loaded.',
    );
    expect(screen.queryByText(/private database detail/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry candidate profile' }));
    expect(await screen.findByRole('heading', { name: 'Nila Raman' })).toBeInTheDocument();
    expect(getApplication).toHaveBeenCalledTimes(2);
  });

  it('downloads a resume only when the application exposes a clean file', async () => {
    const user = userEvent.setup();
    const fixture = createFixtureCandidateGateway();
    const createObjectURL = vi.fn(() => 'blob:fixture-resume');
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });
    const anchorClick = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    const downloadResume = vi
      .fn()
      .mockImplementation((applicationId: string) => fixture.downloadResume(applicationId));

    render(
      <CandidateApplicationProfilePage
        applicationId="application-nila-backend"
        candidateGateway={{ ...fixture, downloadResume }}
        role="WORKSPACE_ADMIN"
        onBack={vi.fn()}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Download clean resume' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Resume download started.');
    expect(downloadResume).toHaveBeenCalledWith('application-nila-backend');
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    expect(anchorClick).toHaveBeenCalledOnce();
    expect(anchorClick.mock.contexts[0]).toHaveProperty('download', 'nila-raman-resume.pdf');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:fixture-resume');
  });

  it('keeps a scan-pending resume unavailable for download', async () => {
    render(
      <CandidateApplicationProfilePage
        applicationId="application-kai-product"
        candidateGateway={createFixtureCandidateGateway()}
        role="WORKSPACE_ADMIN"
        onBack={vi.fn()}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Kai Sen' })).toBeInTheDocument();
    expect(screen.getByText('File status: scan pending')).toBeInTheDocument();
    expect(
      screen.getByText('Download unlocks after the file is verified clean.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download clean resume' })).not.toBeInTheDocument();
  });

  it('removes a stale download action after an authorization rejection', async () => {
    const user = userEvent.setup();
    const fixture = createFixtureCandidateGateway();

    render(
      <CandidateApplicationProfilePage
        applicationId="application-nila-backend"
        candidateGateway={{
          ...fixture,
          downloadResume: async () => {
            throw new CandidateGatewayError(
              'RESUME_DOWNLOAD_FORBIDDEN',
              'private authorization detail',
            );
          },
        }}
        role="WORKSPACE_ADMIN"
        onBack={vi.fn()}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Download clean resume' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Resume download is no longer available.',
    );
    expect(screen.queryByText(/private authorization detail/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download clean resume' })).not.toBeInTheDocument();
  });
});
