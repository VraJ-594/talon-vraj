import type {
  CanonicalField,
  ColumnMapping,
  ImportDraft,
  ImportGateway,
  ImportProblem,
  ImportProblemCode,
  ImportProgress,
  ImportRowStatus,
} from './importGateway';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_DATA_ROWS = 2_000;

const HEADER_ALIASES: Readonly<Record<string, CanonicalField>> = {
  firstname: 'first_name',
  lastname: 'last_name',
  email: 'email',
  emailaddress: 'email',
  resume: 'resume_drive_url',
  resumelink: 'resume_drive_url',
  resumedriveurl: 'resume_drive_url',
  phone: 'phone',
  location: 'location',
  totalexperienceyears: 'total_experience_years',
  currentcompany: 'current_company',
  currenttitle: 'current_title',
  skills: 'skills',
  currentctc: 'current_ctc',
  expectedctc: 'expected_ctc',
  unit: 'ctc_unit',
  ctcunit: 'ctc_unit',
  currency: 'ctc_currency',
  ctccurrency: 'ctc_currency',
  noticeperioddays: 'notice_period_days',
  availabilitydate: 'availability_date',
  source: 'source',
  applicationdate: 'application_date',
};

type SafeImportRecord = {
  readonly id: string;
  readonly rowCount: number;
  confirmationKey?: string;
};

function problem(code: ImportProblemCode, message: string): ImportProblem {
  return Object.assign(new Error(message), { code });
}

function readFile(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(problem('INVALID_CSV', 'The CSV could not be read'));
    reader.readAsText(file, 'utf-8');
  });
}

function parseCsv(text: string): readonly (readonly string[])[] {
  const records: string[][] = [];
  let record: string[] = [];
  let value = '';
  let quoted = false;
  let closedQuote = false;

  const commitValue = () => {
    record.push(value);
    value = '';
    closedQuote = false;
  };
  const commitRecord = () => {
    commitValue();
    records.push(record);
    record = [];
  };

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];

    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        value += '"';
        index += 1;
      } else if (character === '"') {
        quoted = false;
        closedQuote = true;
      } else {
        value += character;
      }
      continue;
    }

    if (character === '"') {
      if (value.length > 0 || closedQuote) {
        throw problem('INVALID_CSV', 'The CSV contains an invalid quoted value.');
      }
      quoted = true;
    } else if (character === ',') {
      commitValue();
    } else if (character === '\r' || character === '\n') {
      if (character === '\r' && text[index + 1] === '\n') {
        index += 1;
      }
      commitRecord();
    } else if (closedQuote) {
      throw problem('INVALID_CSV', 'The CSV contains characters after a closing quote.');
    } else {
      value += character;
    }
  }

  if (quoted) {
    throw problem('INVALID_CSV', 'The CSV contains an unclosed quoted value.');
  }
  if (value.length > 0 || record.length > 0 || closedQuote) {
    commitRecord();
  }

  return records.filter((candidate) => candidate.some((field) => field.trim().length > 0));
}

function suggestMapping(columns: readonly string[]): ColumnMapping {
  const selected = new Set<CanonicalField>();
  return Object.fromEntries(
    columns.map((column) => {
      const normalized = column.toLowerCase().replace(/[^a-z0-9]/g, '');
      const suggestion = HEADER_ALIASES[normalized];
      if (!suggestion || selected.has(suggestion)) {
        return [column, ''];
      }
      selected.add(suggestion);
      return [column, suggestion];
    }),
  );
}

function resultRows(rowCount: number): ImportProgress['rows'] {
  const showcase: readonly ImportRowStatus[] = [
    'FETCHING_RESUME',
    'RESUME_QUARANTINED',
    'SCAN_PENDING',
    'EXTRACTING_TEXT',
    'COMPLETED',
    'RESUME_FETCH_FAILED',
  ];
  const validRowNumbers = Array.from({ length: rowCount }, (_, index) => index + 1).filter(
    (rowNumber) => rowNumber !== 2 && rowNumber !== 3,
  );
  const showcaseStatuses = new Map<number, ImportRowStatus>(
    validRowNumbers.length >= showcase.length
      ? showcase.map((status, index) => [validRowNumbers[index], status])
      : validRowNumbers.map((rowNumber) => [rowNumber, 'COMPLETED']),
  );

  return Array.from({ length: rowCount }, (_, index) => {
    const rowNumber = index + 1;
    if (rowNumber === 2) {
      return {
        rowNumber,
        status: 'INVALID' as const,
        retryable: false,
        message: 'Resume URL is not an anonymously readable Drive PDF.',
      };
    }
    if (rowNumber === 3) {
      return {
        rowNumber,
        status: 'DUPLICATE_APPLICATION' as const,
        retryable: false,
        message: 'An application already exists for this job.',
      };
    }

    const status = showcaseStatuses.get(rowNumber) ?? 'COMPLETED';
    const failed = status === 'RESUME_FETCH_FAILED';
    return {
      rowNumber,
      status,
      retryable: failed,
      ...(failed
        ? { message: 'The public Drive PDF could not be fetched. Check sharing and retry.' }
        : {}),
    };
  });
}

function completedProgress(record: SafeImportRecord): ImportProgress {
  const rows = resultRows(record.rowCount);
  const hasErrors = rows.some((row) =>
    ['INVALID', 'DUPLICATE_APPLICATION', 'RESUME_FETCH_FAILED'].includes(row.status),
  );
  return {
    importId: record.id,
    status: hasErrors ? 'COMPLETED_WITH_ERRORS' : 'COMPLETED',
    processedCount: record.rowCount,
    totalCount: record.rowCount,
    errorCsvAvailable: hasErrors,
    rows,
  };
}

export function createFixtureImportGateway(): ImportGateway {
  let nextId = 1;
  const imports = new Map<string, SafeImportRecord>();

  const findImport = (importId: string) => {
    const stored = imports.get(importId);
    if (stored) return stored;
    if (importId === 'fixture-import-001') {
      const restored: SafeImportRecord = { id: importId, rowCount: 8 };
      imports.set(importId, restored);
      return restored;
    }
    throw problem('IMPORT_NOT_FOUND', 'The requested import could not be found.');
  };

  return {
    async uploadCsv({ file, jobId }): Promise<ImportDraft> {
      if (file.size > MAX_FILE_BYTES) {
        throw problem('FILE_TOO_LARGE', 'Choose a CSV no larger than 10 MB.');
      }

      const records = parseCsv((await readFile(file)).replace(/^\uFEFF/, ''));
      const sourceColumns = (records[0] ?? []).map((column) => column.trim());
      if (sourceColumns.length === 0 || sourceColumns.some((column) => column.length === 0)) {
        throw problem('INVALID_CSV', 'The CSV needs a complete header row.');
      }

      const normalizedHeaders = sourceColumns.map((column) => column.toLowerCase());
      if (new Set(normalizedHeaders).size !== normalizedHeaders.length) {
        throw problem('DUPLICATE_SOURCE_COLUMN', 'Each CSV source column needs a unique header.');
      }

      const rowCount = Math.max(records.length - 1, 0);
      if (rowCount > MAX_DATA_ROWS) {
        throw problem('TOO_MANY_ROWS', 'Choose a CSV with no more than 2,000 data rows.');
      }

      const id = `fixture-import-${String(nextId).padStart(3, '0')}`;
      nextId += 1;
      imports.set(id, { id, rowCount });

      return {
        id,
        jobId,
        fileName: file.name,
        rowCount,
        sourceColumns,
        suggestedMapping: suggestMapping(sourceColumns),
      };
    },
    async validate({ importId, mapping }) {
      const record = findImport(importId);
      const required: readonly CanonicalField[] = [
        'first_name',
        'last_name',
        'email',
        'resume_drive_url',
      ];
      const mapped = Object.values(mapping).filter(
        (field): field is CanonicalField => field.length > 0,
      );
      if (new Set(mapped).size !== mapped.length) {
        throw problem('DUPLICATE_MAPPING', 'Map each canonical field only once.');
      }
      if (required.some((field) => !mapped.includes(field))) {
        throw problem(
          'MISSING_REQUIRED_MAPPING',
          'Map first name, last name, email, and public Drive resume URL.',
        );
      }

      const invalidCount = record.rowCount > 1 ? 1 : 0;
      const duplicateCount = record.rowCount > 2 ? 1 : 0;
      return {
        validCount: Math.max(record.rowCount - invalidCount - duplicateCount, 0),
        invalidCount,
        duplicateCount,
        issues: [
          ...(invalidCount
            ? [
                {
                  rowNumber: 2,
                  kind: 'INVALID' as const,
                  message: 'Resume URL is not an anonymously readable Drive PDF.',
                },
              ]
            : []),
          ...(duplicateCount
            ? [
                {
                  rowNumber: 3,
                  kind: 'DUPLICATE' as const,
                  message: 'An application already exists for this job.',
                },
              ]
            : []),
        ],
      };
    },
    async confirm({ idempotencyKey, importId }) {
      const record = findImport(importId);
      if (record.confirmationKey && record.confirmationKey !== idempotencyKey) {
        throw problem('IMPORT_ALREADY_CONFIRMED', 'This import was already confirmed.');
      }
      record.confirmationKey = idempotencyKey;
      return {
        importId,
        status: 'PROCESSING',
        processedCount: Math.min(record.rowCount, 2),
        totalCount: record.rowCount,
        errorCsvAvailable: false,
        rows: [],
      };
    },
    async getImport(importId) {
      return completedProgress(findImport(importId));
    },
    async retryRow({ importId, rowNumber }) {
      const row = completedProgress(findImport(importId)).rows.find(
        (candidate) => candidate.rowNumber === rowNumber,
      );
      if (!row?.retryable) {
        throw problem('ROW_NOT_RETRYABLE', 'This row cannot be retried.');
      }
    },
    async downloadErrors(importId) {
      const progress = completedProgress(findImport(importId));
      if (!progress.errorCsvAvailable) {
        throw problem('ERROR_CSV_UNAVAILABLE', 'This import has no error CSV.');
      }
      const errorLines = progress.rows.flatMap((row) => {
        if (row.status === 'INVALID') {
          return [`${row.rowNumber},INVALID,Resume URL is not an anonymously readable Drive PDF.`];
        }
        if (row.status === 'DUPLICATE_APPLICATION') {
          return [`${row.rowNumber},DUPLICATE_APPLICATION,Application already exists for this job`];
        }
        if (row.status === 'RESUME_FETCH_FAILED') {
          return [`${row.rowNumber},RESUME_FETCH_FAILED,Public Drive PDF unavailable`];
        }
        return [];
      });
      return new Blob([`row_number,code,message\r\n${errorLines.join('\r\n')}\r\n`], {
        type: 'text/csv;charset=utf-8',
      });
    },
  };
}
