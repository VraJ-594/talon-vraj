import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import App from './App';
import type { AuthGateway } from '../features/auth/authGateway';
import { createFixtureJobGateway } from '../features/jobs/fixtureJobGateway';
import { createFixtureImportGateway } from '../features/imports/fixtureImportGateway';

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
    render(
      <App
        authGateway={authenticatedGateway}
        importGateway={createFixtureImportGateway()}
        jobGateway={createFixtureJobGateway()}
      />,
    );

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

  it('requires an import target job before showing CSV upload', async () => {
    window.history.replaceState({}, '', '/imports');

    render(
      <App
        authGateway={authenticatedGateway}
        importGateway={createFixtureImportGateway()}
        jobGateway={createFixtureJobGateway()}
      />,
    );

    expect(
      await screen.findByRole('heading', { name: 'Select the target job' }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('radio', { name: /Senior Backend Engineer/ }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Application CSV')).not.toBeInTheDocument();
  });

  it('opens the constrained CSV upload only after a job is selected', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports');

    render(<App authGateway={authenticatedGateway} jobGateway={createFixtureJobGateway()} />);

    await user.click(await screen.findByRole('radio', { name: /Senior Backend Engineer/ }));
    await user.click(screen.getByRole('button', { name: 'Continue to CSV upload' }));

    expect(screen.getByRole('heading', { name: 'Upload application CSV' })).toBeInTheDocument();
    expect(screen.getByLabelText('Application CSV')).toHaveAttribute('accept', '.csv,text/csv');
    expect(screen.getByRole('button', { name: 'Download CSV template' })).toBeInTheDocument();
    expect(screen.getByText(/10 MB/)).toBeInTheDocument();
    expect(screen.getByText(/2,000 data rows/)).toBeInTheDocument();
  });

  it('turns arbitrary CSV headers into editable canonical mappings', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports');
    const csv = new File(
      [
        'First Name,Last Name,Email Address,Resume Link,Expected CTC,Unit,Currency\n',
        'Sample,Applicant,sample@example.test,https://drive.google.com/file/d/demo/view,40,LPA,INR',
      ],
      'google-form-responses.csv',
      { type: 'text/csv' },
    );

    render(
      <App
        authGateway={authenticatedGateway}
        importGateway={createFixtureImportGateway()}
        jobGateway={createFixtureJobGateway()}
      />,
    );

    await user.click(await screen.findByRole('radio', { name: /Senior Backend Engineer/ }));
    await user.click(screen.getByRole('button', { name: 'Continue to CSV upload' }));
    await user.upload(screen.getByLabelText('Application CSV'), csv);

    expect(await screen.findByRole('heading', { name: 'Map CSV columns' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Map First Name' })).toHaveValue('first_name');
    expect(screen.getByRole('combobox', { name: 'Map Resume Link' })).toHaveValue(
      'resume_drive_url',
    );
    expect(screen.getByText(/LPA means annual INR/)).toBeInTheDocument();
  });

  it('validates, confirms, and completes an import with safe recovery actions', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports');
    const csv = new File(
      [
        'First Name,Last Name,Email,Resume Link\n',
        'One,Row,one@example.test,https://drive.google.com/file/d/one/view\n',
        'Two,Row,two@example.test,https://drive.google.com/file/d/two/view\n',
        'Three,Row,three@example.test,https://drive.google.com/file/d/three/view\n',
        'Four,Row,four@example.test,https://drive.google.com/file/d/four/view\n',
        'Five,Row,five@example.test,https://drive.google.com/file/d/five/view\n',
        'Six,Row,six@example.test,https://drive.google.com/file/d/six/view\n',
        'Seven,Row,seven@example.test,https://drive.google.com/file/d/seven/view\n',
        'Eight,Row,eight@example.test,https://drive.google.com/file/d/eight/view',
      ],
      'applications.csv',
      { type: 'text/csv' },
    );

    render(
      <App
        authGateway={authenticatedGateway}
        importGateway={createFixtureImportGateway()}
        jobGateway={createFixtureJobGateway()}
      />,
    );

    await user.click(await screen.findByRole('radio', { name: /Senior Backend Engineer/ }));
    await user.click(screen.getByRole('button', { name: 'Continue to CSV upload' }));
    await user.upload(screen.getByLabelText('Application CSV'), csv);
    await user.click(await screen.findByRole('button', { name: 'Validate mapped rows' }));

    expect(await screen.findByRole('heading', { name: 'Review validation' })).toBeInTheDocument();
    expect(screen.getByText('6 valid')).toBeInTheDocument();
    expect(screen.getByText('1 invalid')).toBeInTheDocument();
    expect(screen.getByText('1 duplicate')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Continue to confirmation' }));
    await user.click(screen.getByRole('button', { name: 'Confirm import once' }));

    expect(await screen.findByRole('heading', { name: 'Import in progress' })).toBeInTheDocument();
    expect(window.location.search).toContain('importId=fixture-import-001');
    await user.click(screen.getByRole('button', { name: 'Refresh progress' }));

    expect(await screen.findByRole('heading', { name: 'Import results' })).toBeInTheDocument();
    expect(screen.getByText('Fetching resume')).toBeInTheDocument();
    expect(screen.getByText('Quarantined')).toBeInTheDocument();
    expect(screen.getByText('Scan pending')).toBeInTheDocument();
    expect(screen.getByText('Extracting text')).toBeInTheDocument();
    expect(screen.getByText('Clean')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry row 8' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download error CSV' })).toBeInTheDocument();
  });

  it('restores durable import progress from the URL after refresh', async () => {
    window.history.replaceState({}, '', '/imports?importId=fixture-import-001');

    render(
      <App
        authGateway={authenticatedGateway}
        importGateway={createFixtureImportGateway()}
        jobGateway={createFixtureJobGateway()}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'Import results' })).toBeInTheDocument();
    expect(screen.getByText('Completed with row errors')).toBeInTheDocument();
  });
});
