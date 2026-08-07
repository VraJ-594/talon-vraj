import {
  ArrowRight,
  BriefcaseBusiness,
  CircleCheck,
  Download,
  FileSpreadsheet,
  MapPin,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'wouter';

import type { ImportTargetJob, JobGateway } from '../jobs/jobGateway';
import type {
  CanonicalField,
  ColumnMapping,
  ImportDraft,
  ImportGateway,
  ImportPreview,
  ImportProgress,
  ImportRowStatus,
  ImportStatus,
} from './importGateway';
import { isImportProblem, isOpaqueImportId } from './importGateway';

const CANONICAL_FIELDS: readonly {
  readonly value: CanonicalField;
  readonly label: string;
  readonly required?: boolean;
}[] = [
  { value: 'first_name', label: 'First name', required: true },
  { value: 'last_name', label: 'Last name', required: true },
  { value: 'email', label: 'Email', required: true },
  { value: 'resume_drive_url', label: 'Public Drive resume URL', required: true },
  { value: 'phone', label: 'Phone' },
  { value: 'location', label: 'Location' },
  { value: 'total_experience_years', label: 'Total experience (years)' },
  { value: 'current_company', label: 'Current company' },
  { value: 'current_title', label: 'Current title' },
  { value: 'skills', label: 'Skills' },
  { value: 'current_ctc', label: 'Current CTC' },
  { value: 'expected_ctc', label: 'Expected CTC' },
  { value: 'ctc_unit', label: 'CTC unit' },
  { value: 'ctc_currency', label: 'CTC currency' },
  { value: 'notice_period_days', label: 'Notice period (days)' },
  { value: 'availability_date', label: 'Availability date' },
  { value: 'source', label: 'Source' },
  { value: 'application_date', label: 'Application date' },
];

const ROW_STATUS_LABELS: Readonly<Record<ImportRowStatus, string>> = {
  PENDING: 'Pending',
  VALIDATED: 'Validated',
  FETCHING_RESUME: 'Fetching resume',
  RESUME_QUARANTINED: 'Quarantined',
  APPLICATION_CREATED: 'Application created',
  SCAN_PENDING: 'Scan pending',
  EXTRACTING_TEXT: 'Extracting text',
  COMPLETED: 'Clean',
  INVALID: 'Invalid',
  DUPLICATE_APPLICATION: 'Duplicate application',
  SOURCE_AUTH_REQUIRED: 'Drive access required',
  RESUME_FETCH_FAILED: 'Failed',
  UNSAFE_FILE: 'Unsafe file',
  PERSISTENCE_FAILED: 'Save failed',
  CANCELLED: 'Cancelled',
};

const RESULT_STATUS_LABELS: Readonly<
  Record<'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED' | 'CANCELLED', string>
> = {
  COMPLETED: 'Import completed',
  COMPLETED_WITH_ERRORS: 'Completed with row errors',
  FAILED: 'Import failed',
  CANCELLED: 'Import cancelled',
};

type ImportOperation =
  'restore' | 'upload' | 'validate' | 'confirm' | 'refresh' | 'retry' | 'download';

const OPERATION_FAILURE_MESSAGES: Readonly<Record<ImportOperation, string>> = {
  restore: 'Import progress couldn’t be loaded. Try again.',
  upload: 'The CSV couldn’t be uploaded. Check the file and try again.',
  validate: 'The rows couldn’t be validated. Review the CSV and try again.',
  confirm: 'The import couldn’t be confirmed. Try again.',
  refresh: 'Import progress couldn’t be refreshed. Try again.',
  retry: 'The row couldn’t be retried. Try again.',
  download: 'The error CSV couldn’t be downloaded. Try again.',
};

function isTerminalStatus(
  status: ImportStatus,
): status is 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED' | 'CANCELLED' {
  return ['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED'].includes(status);
}

function safeImportError(error: unknown, operation: ImportOperation): string {
  if (isImportProblem(error)) {
    if (error.code === 'FILE_TOO_LARGE') return 'Choose a CSV no larger than 10 MB.';
    if (error.code === 'TOO_MANY_ROWS') return 'Choose a CSV with no more than 2,000 data rows.';
    if (error.code === 'DUPLICATE_SOURCE_COLUMN') {
      return 'Each CSV source column needs a unique header.';
    }
    if (error.code === 'UNSUPPORTED_SOURCE_COLUMN') {
      return 'Use only column names from the Talon CSV template. Capitalization does not matter.';
    }
    if (error.code === 'MISSING_REQUIRED_COLUMN') {
      return 'The CSV must include first_name, last_name, email, and resume_drive_url.';
    }
    if (error.code === 'DUPLICATE_MAPPING') return 'Map each canonical field only once.';
    if (error.code === 'MISSING_REQUIRED_MAPPING') {
      return 'Map first name, last name, email, and public Drive resume URL.';
    }
    if (error.code === 'IMPORT_ALREADY_CONFIRMED') {
      return 'This import was already confirmed. Refresh its progress.';
    }
    if (error.code === 'ROW_NOT_RETRYABLE') return 'This row can’t be retried.';
    if (error.code === 'ERROR_CSV_UNAVAILABLE') return 'This import has no error CSV.';
  }
  return OPERATION_FAILURE_MESSAGES[operation];
}

type WizardStep = 'TARGET' | 'UPLOAD' | 'MAP' | 'PREVIEW' | 'CONFIRM' | 'PROGRESS' | 'RESULTS';

const STEP_NUMBER: Readonly<Record<WizardStep, number>> = {
  TARGET: 1,
  UPLOAD: 2,
  MAP: 3,
  PREVIEW: 4,
  CONFIRM: 5,
  PROGRESS: 6,
  RESULTS: 7,
};

const STEP_TITLE: Readonly<Record<WizardStep, string>> = {
  TARGET: 'Select the target job',
  UPLOAD: 'Upload application CSV',
  MAP: 'Review recognized columns',
  PREVIEW: 'Review validation',
  CONFIRM: 'Confirm application import',
  PROGRESS: 'Import in progress',
  RESULTS: 'Import results',
};

export function ImportWizard({
  importGateway,
  jobGateway,
}: {
  readonly importGateway: ImportGateway;
  readonly jobGateway: JobGateway;
}) {
  const [jobs, setJobs] = useState<readonly ImportTargetJob[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [step, setStep] = useState<WizardStep>('TARGET');
  const [draft, setDraft] = useState<ImportDraft | null>(null);
  const [mapping, setMapping] = useState<ColumnMapping>({});
  const [operationError, setOperationError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [progress, setProgress] = useState<ImportProgress | null>(null);
  const [pendingOperation, setPendingOperation] = useState<ImportOperation | null>(null);
  const [restoreFailed, setRestoreFailed] = useState(false);
  const [restoreAttempt, setRestoreAttempt] = useState(0);
  const idempotencyKey = useRef(crypto.randomUUID());

  useEffect(() => {
    let active = true;

    void jobGateway
      .listImportTargets()
      .then((targets) => {
        if (active) {
          setJobs(targets);
        }
      })
      .catch(() => {
        if (active) {
          setLoadFailed(true);
        }
      });

    return () => {
      active = false;
    };
  }, [jobGateway]);

  useEffect(() => {
    const importId = new URLSearchParams(window.location.search).get('importId');
    if (!isOpaqueImportId(importId)) {
      return undefined;
    }

    let active = true;
    setPendingOperation('restore');
    setOperationError(null);
    setRestoreFailed(false);
    void importGateway
      .getImport(importId)
      .then((restored) => {
        if (active) {
          setProgress(restored);
          setStep(isTerminalStatus(restored.status) ? 'RESULTS' : 'PROGRESS');
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setOperationError(safeImportError(error, 'restore'));
          setRestoreFailed(true);
        }
      })
      .finally(() => {
        if (active) setPendingOperation(null);
      });

    return () => {
      active = false;
    };
  }, [importGateway, restoreAttempt]);

  const downloadTemplate = () => {
    const header =
      'first_name,last_name,email,resume_drive_url,phone,location,total_experience_years,current_company,current_title,skills,current_ctc,expected_ctc,ctc_unit,ctc_currency,notice_period_days,availability_date,source,application_date\r\n';
    const url = URL.createObjectURL(new Blob([header], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'talon-application-import-template.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <section className="import-flow" aria-label="Application import wizard">
      <header className="import-flow-heading">
        <p className="eyebrow">Step {STEP_NUMBER[step]} of 7</p>
        <h2>{STEP_TITLE[step]}</h2>
        <p>
          {step === 'TARGET'
            ? 'Every row in this CSV will become an application for one job.'
            : step === 'UPLOAD'
              ? 'Use Talon template column names. Capitalization does not matter.'
              : step === 'MAP'
                ? `${draft?.rowCount ?? 0} rows detected in ${draft?.fileName ?? 'your CSV'}.`
                : step === 'PREVIEW'
                  ? 'Review row validation before creating applications.'
                  : step === 'CONFIRM'
                    ? 'This confirmation starts durable background processing.'
                    : step === 'PROGRESS'
                      ? `${progress?.processedCount ?? 0} of ${progress?.totalCount ?? 0} rows processed.`
                      : 'Review resume processing and recoverable row failures.'}
        </p>
      </header>

      {operationError ? (
        <div className="import-notice import-notice-error" role="alert">
          <span>{operationError}</span>
          {restoreFailed ? (
            <button
              className="text-button"
              type="button"
              disabled={pendingOperation !== null}
              onClick={() => setRestoreAttempt((attempt) => attempt + 1)}
            >
              Retry loading import
            </button>
          ) : null}
        </div>
      ) : null}
      {statusMessage ? <p role="status">{statusMessage}</p> : null}

      {step === 'RESULTS' && progress ? (
        <div className="results-step">
          <div className="result-summary">
            <strong>
              {isTerminalStatus(progress.status)
                ? RESULT_STATUS_LABELS[progress.status]
                : 'Import status updated'}
            </strong>
            <span>{progress.processedCount} rows processed</span>
          </div>
          <div className="row-status-list">
            {progress.rows.map((row) => (
              <article key={row.rowNumber} className="row-status-card">
                <span>Row {row.rowNumber}</span>
                <strong>{ROW_STATUS_LABELS[row.status]}</strong>
                {row.message ? <small>{row.message}</small> : null}
                {row.retryable ? (
                  <button
                    type="button"
                    aria-label={`Retry row ${row.rowNumber}`}
                    disabled={pendingOperation !== null}
                    onClick={() => {
                      setPendingOperation('retry');
                      setOperationError(null);
                      setStatusMessage(null);
                      void importGateway
                        .retryRow({ importId: progress.importId, rowNumber: row.rowNumber })
                        .then(() => setStatusMessage(`Row ${row.rowNumber} retry queued.`))
                        .catch((error: unknown) =>
                          setOperationError(safeImportError(error, 'retry')),
                        )
                        .finally(() => setPendingOperation(null));
                    }}
                  >
                    {pendingOperation === 'retry' ? 'Retrying…' : 'Retry'}
                  </button>
                ) : null}
              </article>
            ))}
          </div>
          {progress.errorCsvAvailable ? (
            <button
              className="secondary-button"
              type="button"
              disabled={pendingOperation !== null}
              onClick={() => {
                setPendingOperation('download');
                setOperationError(null);
                void importGateway
                  .downloadErrors(progress.importId)
                  .then((blob) => {
                    const url = URL.createObjectURL(blob);
                    const anchor = document.createElement('a');
                    anchor.href = url;
                    anchor.download = `talon-import-${progress.importId}-errors.csv`;
                    anchor.click();
                    URL.revokeObjectURL(url);
                  })
                  .catch((error: unknown) => setOperationError(safeImportError(error, 'download')))
                  .finally(() => setPendingOperation(null));
              }}
            >
              {pendingOperation === 'download' ? 'Downloading…' : 'Download error CSV'}
            </button>
          ) : null}
          <Link className="primary-button result-review-link" href="/candidates">
            Review candidate applications
          </Link>
        </div>
      ) : step === 'PROGRESS' && progress ? (
        <div className="progress-step">
          <div
            className="progress-meter"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={progress.totalCount}
            aria-valuenow={progress.processedCount}
          >
            <span
              style={{
                width: `${
                  progress.totalCount > 0
                    ? Math.min((progress.processedCount / progress.totalCount) * 100, 100)
                    : 0
                }%`,
              }}
            />
          </div>
          <p>
            Progress is stored by the import service. Keep the import ID in the URL to resume after
            refresh.
          </p>
          <button
            className="primary-button"
            type="button"
            disabled={pendingOperation !== null}
            onClick={() => {
              setPendingOperation('refresh');
              setOperationError(null);
              void importGateway
                .getImport(progress.importId)
                .then((updated) => {
                  setProgress(updated);
                  if (isTerminalStatus(updated.status)) setStep('RESULTS');
                })
                .catch((error: unknown) => setOperationError(safeImportError(error, 'refresh')))
                .finally(() => setPendingOperation(null));
            }}
          >
            {pendingOperation === 'refresh' ? 'Refreshing…' : 'Refresh progress'}
          </button>
        </div>
      ) : step === 'CONFIRM' && preview && draft ? (
        <div className="confirm-step">
          <dl>
            <div>
              <dt>Target job</dt>
              <dd>{jobs?.find((job) => job.id === selectedJobId)?.title}</dd>
            </div>
            <div>
              <dt>CSV rows</dt>
              <dd>{draft.rowCount}</dd>
            </div>
            <div>
              <dt>Ready</dt>
              <dd>{preview.validCount}</dd>
            </div>
          </dl>
          <button
            className="primary-button"
            type="button"
            disabled={pendingOperation !== null}
            onClick={() => {
              setPendingOperation('confirm');
              setOperationError(null);
              void importGateway
                .confirm({ importId: draft.id, idempotencyKey: idempotencyKey.current })
                .then((started) => {
                  setProgress(started);
                  window.history.replaceState(
                    {},
                    '',
                    `/imports?importId=${encodeURIComponent(started.importId)}`,
                  );
                  setStep('PROGRESS');
                })
                .catch((error: unknown) => setOperationError(safeImportError(error, 'confirm')))
                .finally(() => setPendingOperation(null));
            }}
          >
            {pendingOperation === 'confirm' ? 'Confirming…' : 'Confirm import once'}
          </button>
        </div>
      ) : step === 'PREVIEW' && preview ? (
        <div className="preview-step">
          <div className="preview-counts">
            <strong>{preview.validCount} valid</strong>
            <strong>{preview.invalidCount} invalid</strong>
            <strong>{preview.duplicateCount} duplicate</strong>
          </div>
          <div className="preview-issues">
            {preview.issues.map((issue) => (
              <article key={`${issue.rowNumber}-${issue.kind}`}>
                <strong>
                  Row {issue.rowNumber} · {issue.kind === 'INVALID' ? 'Invalid' : 'Duplicate'}
                </strong>
                <p>{issue.message}</p>
              </article>
            ))}
          </div>
          <button className="primary-button" type="button" onClick={() => setStep('CONFIRM')}>
            Continue to confirmation
          </button>
        </div>
      ) : step === 'MAP' && draft ? (
        <div className="mapping-step">
          <div className="recognized-column-summary">
            <CircleCheck aria-hidden="true" size={20} />
            <span>
              <strong>{draft.sourceColumns.length} columns recognized</strong>
              <small>Names matched automatically against the Talon template.</small>
            </span>
          </div>
          <div className="mapping-guidance">
            <strong>Compensation normalization</strong>
            <p>
              LPA means annual INR. ANNUAL uses whole currency units with a supplied ISO currency.
              Experience becomes months; notice becomes days; dates use ISO format.
            </p>
          </div>
          <ul className="mapping-list" aria-label="Recognized CSV columns">
            {draft.sourceColumns.map((sourceColumn) => {
              const canonical = mapping[sourceColumn];
              const field = CANONICAL_FIELDS.find((candidate) => candidate.value === canonical);
              return (
                <li key={sourceColumn} className="mapping-row">
                  <span className="mapping-source">
                    <small>CSV column</small>
                    <strong>{sourceColumn}</strong>
                  </span>
                  <ArrowRight className="mapping-arrow" aria-hidden="true" size={16} />
                  <span className="mapping-target">
                    <span className="mapping-target-label">
                      <small>Talon field</small>
                      {field?.required ? (
                        <small className="mapping-required">Required</small>
                      ) : null}
                    </span>
                    <strong>{field?.label}</strong>
                  </span>
                  <CircleCheck className="mapping-check" aria-hidden="true" size={18} />
                </li>
              );
            })}
          </ul>
          <button
            className="primary-button"
            type="button"
            disabled={pendingOperation !== null}
            onClick={() => {
              setPendingOperation('validate');
              setOperationError(null);
              void importGateway
                .validate({ importId: draft.id, mapping, retainUnmapped: false })
                .then((validated) => {
                  setPreview(validated);
                  setStep('PREVIEW');
                })
                .catch((error: unknown) => setOperationError(safeImportError(error, 'validate')))
                .finally(() => setPendingOperation(null));
            }}
          >
            {pendingOperation === 'validate' ? 'Validating…' : 'Validate rows'}
          </button>
        </div>
      ) : step === 'UPLOAD' ? (
        <div className="upload-step">
          <div className="import-rule-strip" aria-label="CSV limits">
            <span>UTF-8 CSV</span>
            <span>Maximum 10 MB</span>
            <span>Up to 2,000 data rows</span>
          </div>
          <button
            className="secondary-button template-button"
            type="button"
            onClick={downloadTemplate}
          >
            <Download aria-hidden="true" size={17} />
            Download CSV template
          </button>
          <label className="file-drop">
            <FileSpreadsheet aria-hidden="true" size={28} />
            <strong>Choose an application CSV</strong>
            <span>Required resume values must be public Google Drive PDF links.</span>
            <input
              aria-label="Application CSV"
              name="application-csv"
              type="file"
              accept=".csv,text/csv"
              disabled={pendingOperation !== null}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (!file || !selectedJobId) {
                  return;
                }
                setPendingOperation('upload');
                setOperationError(null);
                void importGateway
                  .uploadCsv({ file, jobId: selectedJobId })
                  .then((uploadedDraft) => {
                    setDraft(uploadedDraft);
                    setMapping(uploadedDraft.suggestedMapping);
                    setStep('MAP');
                  })
                  .catch((error: unknown) => setOperationError(safeImportError(error, 'upload')))
                  .finally(() => setPendingOperation(null));
              }}
            />
            <span className="file-drop-action">
              {pendingOperation === 'upload' ? 'Uploading…' : 'Browse files'}
            </span>
          </label>
          <button className="text-button" type="button" onClick={() => setStep('TARGET')}>
            Back to job selection
          </button>
        </div>
      ) : loadFailed ? (
        <div className="import-notice import-notice-error" role="alert">
          Jobs couldn’t be loaded. Refresh this page to try again.
        </div>
      ) : jobs === null ? (
        <p role="status">Loading open jobs…</p>
      ) : jobs.length === 0 ? (
        <div className="import-notice">No open jobs are available for import.</div>
      ) : (
        <>
          <fieldset className="job-selector">
            <legend className="sr-only">Import target job</legend>
            {jobs.map((job) => (
              <label key={job.id} className="job-choice">
                <input
                  type="radio"
                  name="target-job"
                  value={job.id}
                  checked={selectedJobId === job.id}
                  onChange={() => setSelectedJobId(job.id)}
                />
                <span className="job-choice-marker" aria-hidden="true" />
                <span className="job-choice-copy">
                  <strong>{job.title}</strong>
                  <small>
                    <BriefcaseBusiness aria-hidden="true" size={14} />
                    {job.department}
                    <MapPin aria-hidden="true" size={14} />
                    {job.location}
                  </small>
                </span>
                <span
                  className={
                    job.status === 'OPEN' ? 'status status-active' : 'status status-on-hold'
                  }
                >
                  {job.status === 'OPEN' ? 'Open' : 'On hold'}
                </span>
              </label>
            ))}
          </fieldset>
          <div className="import-actions">
            <button
              className="primary-button"
              type="button"
              disabled={!selectedJobId}
              onClick={() => setStep('UPLOAD')}
            >
              Continue to CSV upload
            </button>
          </div>
        </>
      )}
    </section>
  );
}
