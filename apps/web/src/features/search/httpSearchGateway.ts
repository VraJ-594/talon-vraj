import type { ApiClient } from '../../lib/apiClient';
import {
  SearchGatewayError,
  type CandidateSearchCriteria,
  type CandidateSearchPage,
  type CommandSearchItem,
  type InterpretedCandidateSearch,
  type SearchGateway,
  type SearchProblemCode,
} from './searchGateway';

const KNOWN_CODES: readonly SearchProblemCode[] = [
  'INTERPRETER_DISABLED',
  'INTERPRETER_UNAVAILABLE',
  'INTERPRETER_QUOTA_EXCEEDED',
  'INTERPRETATION_INVALID',
  'INTERPRETATION_RATE_LIMITED',
  'AMBIGUOUS_CURRENCY',
  'SEARCH_INVALID',
];

async function ensureOk(response: Response) {
  if (response.ok) return response;
  let code: unknown;
  try {
    code = ((await response.json()) as { code?: unknown }).code;
  } catch {
    code = undefined;
  }
  throw new SearchGatewayError(
    typeof code === 'string' && KNOWN_CODES.includes(code as SearchProblemCode)
      ? (code as SearchProblemCode)
      : 'SEARCH_INVALID',
  );
}

function jsonRequest(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}

export class HttpSearchGateway implements SearchGateway {
  constructor(private readonly apiClient: ApiClient) {}

  async command(query: string): Promise<readonly CommandSearchItem[]> {
    const response = await ensureOk(
      await this.apiClient.request(
        `/api/v1/search/command?q=${encodeURIComponent(query)}&limit=8`,
        { method: 'GET' },
        true,
      ),
    );
    const body: unknown = await response.json();
    if (!Array.isArray(body)) throw new SearchGatewayError('SEARCH_INVALID');
    return body as readonly CommandSearchItem[];
  }

  async interpret(input: {
    readonly query: string;
    readonly locale: string;
    readonly timezone: string;
  }): Promise<InterpretedCandidateSearch> {
    const response = await ensureOk(
      await this.apiClient.request('/api/v1/candidate-search/interpret', jsonRequest(input), true),
    );
    return (await response.json()) as InterpretedCandidateSearch;
  }

  async query(criteria: CandidateSearchCriteria): Promise<CandidateSearchPage> {
    const response = await ensureOk(
      await this.apiClient.request('/api/v1/candidate-search/query', jsonRequest(criteria), true),
    );
    return (await response.json()) as CandidateSearchPage;
  }
}
