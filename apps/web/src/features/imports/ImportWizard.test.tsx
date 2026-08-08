import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { JobGateway } from '../jobs/jobGateway';
import { ImportWizard } from './ImportWizard';
import type { ImportGateway, ImportProgress } from './importGateway';

const cleanProgress: ImportProgress = {
  importId: 'fixture-import-clean',
  status: 'COMPLETED',
  processedCount: 1,
  totalCount: 1,
  errorCsvAvailable: false,
  rows: [{ rowNumber: 1, status: 'COMPLETED', retryable: false }],
};

const failedRowProgress: ImportProgress = {
  importId: 'fixture-import-errors',
  status: 'COMPLETED_WITH_ERRORS',
  processedCount: 1,
  totalCount: 1,
  errorCsvAvailable: true,
  rows: [
    {
      rowNumber: 1,
      status: 'RESUME_FETCH_FAILED',
      retryable: true,
      message: 'The public Drive PDF could not be fetched. Check sharing and retry.',
    },
  ],
};

function importGateway(overrides: Partial<ImportGateway> = {}): ImportGateway {
  return {
    uploadCsv: async () => {
      throw new Error('Upload is not used in this test');
    },
    validate: async () => ({
      validCount: 0,
      invalidCount: 0,
      duplicateCount: 0,
      issues: [],
    }),
    confirm: async () => cleanProgress,
    getImport: async () => cleanProgress,
    retryRow: async () => undefined,
    downloadErrors: async () => new Blob(),
    ...overrides,
  };
}

const openJobGateway: JobGateway = {
  listImportTargets: async () => [
    {
      id: 'job-1',
      title: 'Backend Engineer',
      department: 'Engineering',
      location: 'Pune',
      status: 'OPEN',
    },
  ],
};

describe('ImportWizard recovery and lifecycle states', () => {
  it('renders on an HTTP origin where crypto.randomUUID is unavailable', async () => {
    const browserCrypto = globalThis.crypto;
    vi.stubGlobal('crypto', {
      getRandomValues: browserCrypto.getRandomValues.bind(browserCrypto),
    });

    try {
      expect(() =>
        render(<ImportWizard importGateway={importGateway()} jobGateway={openJobGateway} />),
      ).not.toThrow();
      expect(await screen.findByText('Backend Engineer')).toBeInTheDocument();
    } finally {
      vi.stubGlobal('crypto', browserCrypto);
    }
  });

  it('shows safe restoration recovery without exposing rejected gateway detail', async () => {
    const user = userEvent.setup();
    const getImport = vi
      .fn<ImportGateway['getImport']>()
      .mockRejectedValueOnce(new Error('private provider response'))
      .mockResolvedValueOnce(cleanProgress);
    window.history.replaceState({}, '', '/imports?importId=fixture-import-clean');

    render(
      <ImportWizard importGateway={importGateway({ getImport })} jobGateway={openJobGateway} />,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Import progress couldn’t be loaded. Try again.',
    );
    expect(screen.getByRole('alert')).not.toHaveTextContent('private provider response');

    await user.click(screen.getByRole('button', { name: 'Retry loading import' }));

    expect(await screen.findByRole('heading', { name: 'Import results' })).toBeInTheDocument();
  });

  it('renders clean completion without offering an error CSV', async () => {
    window.history.replaceState({}, '', '/imports?importId=fixture-import-clean');

    render(
      <ImportWizard
        importGateway={importGateway({ getImport: async () => cleanProgress })}
        jobGateway={openJobGateway}
      />,
    );

    expect(await screen.findByText('Import completed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download error CSV' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Review candidate applications' })).toHaveAttribute(
      'href',
      '/candidates',
    );
  });

  it('maps retry and download failures to safe operation-specific messages', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports?importId=fixture-import-errors');

    render(
      <ImportWizard
        importGateway={importGateway({
          getImport: async () => failedRowProgress,
          retryRow: async () => {
            throw new Error('private retry detail');
          },
          downloadErrors: async () => {
            throw new Error('signed storage URL');
          },
        })}
        jobGateway={openJobGateway}
      />,
    );

    await user.click(await screen.findByRole('button', { name: 'Retry row 1' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The row couldn’t be retried. Try again.',
    );
    expect(screen.getByRole('alert')).not.toHaveTextContent('private retry detail');

    await user.click(screen.getByRole('button', { name: 'Download error CSV' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The error CSV couldn’t be downloaded. Try again.',
    );
    expect(screen.getByRole('alert')).not.toHaveTextContent('signed storage URL');
  });

  it('guards zero-row progress and disables refresh while the request is pending', async () => {
    const user = userEvent.setup();
    let finishRefresh: ((progress: ImportProgress) => void) | undefined;
    const refreshResult = new Promise<ImportProgress>((resolve) => {
      finishRefresh = resolve;
    });
    const processing: ImportProgress = {
      importId: 'fixture-import-empty',
      status: 'PROCESSING',
      processedCount: 0,
      totalCount: 0,
      errorCsvAvailable: false,
      rows: [],
    };
    const getImport = vi
      .fn<ImportGateway['getImport']>()
      .mockResolvedValueOnce(processing)
      .mockReturnValueOnce(refreshResult);
    window.history.replaceState({}, '', '/imports?importId=fixture-import-empty');

    render(
      <ImportWizard importGateway={importGateway({ getImport })} jobGateway={openJobGateway} />,
    );

    const progressbar = await screen.findByRole('progressbar');
    expect(progressbar.firstElementChild).toHaveStyle({ width: '0%' });

    await user.click(screen.getByRole('button', { name: 'Refresh progress' }));
    expect(screen.getByRole('button', { name: 'Refreshing…' })).toBeDisabled();

    finishRefresh?.(cleanProgress);
    expect(await screen.findByText('Import completed')).toBeInTheDocument();
  });

  it('uses the job gateway status instead of labeling every job open', async () => {
    window.history.replaceState({}, '', '/imports');
    render(
      <ImportWizard
        importGateway={importGateway()}
        jobGateway={{
          listImportTargets: async () => [
            {
              id: 'job-hold',
              title: 'Product Designer',
              department: 'Product',
              location: 'Bengaluru',
              status: 'ON_HOLD',
            },
          ],
        }}
      />,
    );

    expect(await screen.findByText('On hold')).toBeInTheDocument();
  });

  it('does not expose upload adapter errors in the interface', async () => {
    const user = userEvent.setup();
    window.history.replaceState({}, '', '/imports');
    render(
      <ImportWizard
        importGateway={importGateway({
          uploadCsv: async () => {
            throw new Error('candidate source URL leaked here');
          },
        })}
        jobGateway={openJobGateway}
      />,
    );

    await user.click(await screen.findByRole('radio', { name: /Backend Engineer/ }));
    await user.click(screen.getByRole('button', { name: 'Continue to CSV upload' }));
    await user.upload(
      screen.getByLabelText('Application CSV'),
      new File(['First Name,Last Name,Email,Resume\r\n'], 'applications.csv', {
        type: 'text/csv',
      }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The CSV couldn’t be uploaded. Check the file and try again.',
    );
    expect(screen.getByRole('alert')).not.toHaveTextContent('candidate source URL leaked here');
  });
});
