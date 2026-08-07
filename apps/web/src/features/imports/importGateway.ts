export type CanonicalField =
  | 'first_name'
  | 'last_name'
  | 'email'
  | 'resume_drive_url'
  | 'phone'
  | 'location'
  | 'total_experience_years'
  | 'current_company'
  | 'current_title'
  | 'skills'
  | 'current_ctc'
  | 'expected_ctc'
  | 'ctc_unit'
  | 'ctc_currency'
  | 'notice_period_days'
  | 'availability_date'
  | 'source'
  | 'application_date';

export type ColumnMapping = Readonly<Record<string, CanonicalField | ''>>;

export type ImportDraft = {
  readonly id: string;
  readonly jobId: string;
  readonly fileName: string;
  readonly rowCount: number;
  readonly sourceColumns: readonly string[];
  readonly suggestedMapping: ColumnMapping;
};

export type ImportProblemCode =
  | 'FILE_TOO_LARGE'
  | 'TOO_MANY_ROWS'
  | 'INVALID_CSV'
  | 'DUPLICATE_SOURCE_COLUMN'
  | 'UNSUPPORTED_SOURCE_COLUMN'
  | 'MISSING_REQUIRED_COLUMN'
  | 'DUPLICATE_MAPPING'
  | 'MISSING_REQUIRED_MAPPING'
  | 'IMPORT_NOT_FOUND'
  | 'IMPORT_ALREADY_CONFIRMED'
  | 'ROW_NOT_RETRYABLE'
  | 'ERROR_CSV_UNAVAILABLE'
  | 'API_UNAVAILABLE';

export type ImportProblem = Error & { readonly code: ImportProblemCode };

export function isImportProblem(error: unknown): error is ImportProblem {
  return (
    error instanceof Error &&
    'code' in error &&
    typeof (error as { readonly code?: unknown }).code === 'string'
  );
}

export function isOpaqueImportId(value: string | null): value is string {
  return value !== null && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(value);
}

export type ImportPreview = {
  readonly validCount: number;
  readonly invalidCount: number;
  readonly duplicateCount: number;
  readonly issues: readonly {
    readonly rowNumber: number;
    readonly kind: 'INVALID' | 'DUPLICATE';
    readonly message: string;
  }[];
};

export type ImportRowStatus =
  | 'PENDING'
  | 'VALIDATED'
  | 'FETCHING_RESUME'
  | 'RESUME_QUARANTINED'
  | 'APPLICATION_CREATED'
  | 'SCAN_PENDING'
  | 'EXTRACTING_TEXT'
  | 'COMPLETED'
  | 'INVALID'
  | 'DUPLICATE_APPLICATION'
  | 'SOURCE_AUTH_REQUIRED'
  | 'RESUME_FETCH_FAILED'
  | 'UNSAFE_FILE'
  | 'PERSISTENCE_FAILED'
  | 'CANCELLED';

export type ImportStatus =
  | 'UPLOADED'
  | 'MAPPED'
  | 'VALIDATING'
  | 'PREVIEW_READY'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED'
  | 'CANCELLED';

export type ImportProgress = {
  readonly importId: string;
  readonly status: ImportStatus;
  readonly processedCount: number;
  readonly totalCount: number;
  readonly errorCsvAvailable: boolean;
  readonly rows: readonly {
    readonly rowNumber: number;
    readonly status: ImportRowStatus;
    readonly retryable: boolean;
    readonly message?: string;
  }[];
};

export interface ImportGateway {
  uploadCsv(input: { readonly jobId: string; readonly file: File }): Promise<ImportDraft>;
  validate(input: {
    readonly importId: string;
    readonly mapping: ColumnMapping;
    readonly retainUnmapped: boolean;
  }): Promise<ImportPreview>;
  confirm(input: {
    readonly importId: string;
    readonly idempotencyKey: string;
  }): Promise<ImportProgress>;
  getImport(importId: string): Promise<ImportProgress>;
  retryRow(input: { readonly importId: string; readonly rowNumber: number }): Promise<void>;
  downloadErrors(importId: string): Promise<Blob>;
}
