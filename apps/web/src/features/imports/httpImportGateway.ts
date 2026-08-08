import type { ApiClient } from '../../lib/apiClient';
import type {
  ColumnMapping,
  ImportDraft,
  ImportGateway,
  ImportPreview,
  ImportProblem,
  ImportProblemCode,
} from './importGateway';

const PROBLEM_CODES: readonly ImportProblemCode[] = [
  'FILE_TOO_LARGE',
  'TOO_MANY_ROWS',
  'INVALID_CSV',
  'DUPLICATE_SOURCE_COLUMN',
  'UNSUPPORTED_SOURCE_COLUMN',
  'MISSING_REQUIRED_COLUMN',
  'DUPLICATE_MAPPING',
  'MISSING_REQUIRED_MAPPING',
  'IMPORT_NOT_FOUND',
  'IMPORT_ALREADY_CONFIRMED',
  'ROW_NOT_RETRYABLE',
  'ERROR_CSV_UNAVAILABLE',
  'API_UNAVAILABLE',
];

function problem(code: ImportProblemCode): ImportProblem {
  return Object.assign(new Error('Import request could not be completed'), { code });
}

async function ensureOk(response: Response): Promise<Response> {
  if (response.ok) return response;
  let code: unknown;
  try {
    code = ((await response.json()) as { code?: unknown }).code;
  } catch {
    code = undefined;
  }
  throw problem(
    typeof code === 'string' && PROBLEM_CODES.includes(code as ImportProblemCode)
      ? (code as ImportProblemCode)
      : 'API_UNAVAILABLE',
  );
}

function asDraft(value: unknown): ImportDraft {
  const draft = value as ImportDraft;
  if (
    typeof draft?.id !== 'string' ||
    typeof draft.jobId !== 'string' ||
    typeof draft.fileName !== 'string' ||
    typeof draft.rowCount !== 'number' ||
    !Array.isArray(draft.sourceColumns) ||
    typeof draft.suggestedMapping !== 'object' ||
    draft.suggestedMapping === null
  ) {
    throw problem('API_UNAVAILABLE');
  }
  return draft;
}

function asPreview(value: unknown): ImportPreview {
  const preview = value as ImportPreview;
  if (
    typeof preview?.validCount !== 'number' ||
    typeof preview.invalidCount !== 'number' ||
    typeof preview.duplicateCount !== 'number' ||
    !Array.isArray(preview.issues)
  ) {
    throw problem('API_UNAVAILABLE');
  }
  return preview;
}

function unavailable(): never {
  throw problem('API_UNAVAILABLE');
}

export class HttpImportGateway implements ImportGateway {
  readonly processingAvailable = false;

  constructor(private readonly apiClient: ApiClient) {}

  async downloadTemplate(): Promise<Blob> {
    return (
      await ensureOk(
        await this.apiClient.request('/api/v1/imports/template', { method: 'GET' }, true),
      )
    ).blob();
  }

  async uploadCsv({ jobId, file }: { readonly jobId: string; readonly file: File }) {
    const body = new FormData();
    body.set('jobId', jobId);
    body.set('file', file);
    const response = await ensureOk(
      await this.apiClient.request('/api/v1/imports', { method: 'POST', body }, true),
    );
    return asDraft(await response.json());
  }

  async validate(input: {
    readonly importId: string;
    readonly mapping: ColumnMapping;
    readonly retainUnmapped: boolean;
  }) {
    const response = await ensureOk(
      await this.apiClient.request(
        `/api/v1/imports/${encodeURIComponent(input.importId)}/validate`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ mapping: input.mapping, retainUnmapped: input.retainUnmapped }),
        },
        true,
      ),
    );
    return asPreview(await response.json());
  }

  async getPreview(importId: string) {
    const response = await ensureOk(
      await this.apiClient.request(
        `/api/v1/imports/${encodeURIComponent(importId)}/preview`,
        { method: 'GET' },
        true,
      ),
    );
    return asPreview(await response.json());
  }

  async confirm(): Promise<never> {
    return unavailable();
  }

  async getImport(): Promise<never> {
    return unavailable();
  }

  async retryRow(): Promise<never> {
    return unavailable();
  }

  async downloadErrors(): Promise<never> {
    return unavailable();
  }
}
