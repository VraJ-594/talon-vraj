import type { ApiClient } from '../../lib/apiClient';
import {
  CandidateGatewayError,
  type CandidateApplicationDetail,
  type CandidateApplicationPage,
  type CandidateGateway,
  type CandidateGatewayErrorCode,
} from './candidateGateway';

const ERROR_CODES: Readonly<Record<string, CandidateGatewayErrorCode>> = {
  CANDIDATE_FORBIDDEN: 'FORBIDDEN',
  CANDIDATE_APPLICATION_NOT_FOUND: 'NOT_FOUND',
  RESUME_DOWNLOAD_FORBIDDEN: 'RESUME_DOWNLOAD_FORBIDDEN',
  RESUME_NOT_CLEAN: 'RESUME_NOT_CLEAN',
};

async function ensureOk(response: Response) {
  if (response.ok) return response;
  let backendCode: unknown;
  try {
    backendCode = ((await response.json()) as { code?: unknown }).code;
  } catch {
    backendCode = undefined;
  }
  const code =
    typeof backendCode === 'string' && ERROR_CODES[backendCode]
      ? ERROR_CODES[backendCode]
      : response.status === 403
        ? 'FORBIDDEN'
        : response.status === 404
          ? 'NOT_FOUND'
          : 'UNAVAILABLE';
  throw new CandidateGatewayError(code, 'Candidate request could not be completed');
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function candidatePage(value: unknown): CandidateApplicationPage {
  if (
    !isObject(value) ||
    !Array.isArray(value.items) ||
    !(typeof value.nextCursor === 'string' || value.nextCursor === null)
  ) {
    throw new CandidateGatewayError('UNAVAILABLE', 'Candidate response was invalid');
  }
  return value as CandidateApplicationPage;
}

function candidateDetail(value: unknown): CandidateApplicationDetail {
  if (
    !isObject(value) ||
    typeof value.applicationId !== 'string' ||
    typeof value.candidateId !== 'string' ||
    typeof value.candidateName !== 'string' ||
    !Array.isArray(value.skills) ||
    !Array.isArray(value.additionalAnswers)
  ) {
    throw new CandidateGatewayError('UNAVAILABLE', 'Candidate response was invalid');
  }
  return value as CandidateApplicationDetail;
}

export class HttpCandidateGateway implements CandidateGateway {
  constructor(private readonly apiClient: ApiClient) {}

  async listApplications(cursor?: string | null): Promise<CandidateApplicationPage> {
    const query = new URLSearchParams({ limit: '50' });
    if (cursor) query.set('cursor', cursor);
    try {
      const response = await ensureOk(
        await this.apiClient.request(
          `/api/v1/applications?${query.toString()}`,
          { method: 'GET' },
          true,
        ),
      );
      return candidatePage(await response.json());
    } catch (error) {
      if (error instanceof CandidateGatewayError) throw error;
      throw new CandidateGatewayError('UNAVAILABLE', 'Candidate request could not be completed');
    }
  }

  async getApplication(applicationId: string): Promise<CandidateApplicationDetail> {
    try {
      const response = await ensureOk(
        await this.apiClient.request(
          `/api/v1/applications/${encodeURIComponent(applicationId)}`,
          { method: 'GET' },
          true,
        ),
      );
      return candidateDetail(await response.json());
    } catch (error) {
      if (error instanceof CandidateGatewayError) throw error;
      throw new CandidateGatewayError('UNAVAILABLE', 'Candidate request could not be completed');
    }
  }

  async downloadResume(applicationId: string): Promise<Blob> {
    try {
      const response = await ensureOk(
        await this.apiClient.request(
          `/api/v1/applications/${encodeURIComponent(applicationId)}/resume-download`,
          { method: 'GET' },
          true,
        ),
      );
      return await response.blob();
    } catch (error) {
      if (error instanceof CandidateGatewayError) throw error;
      throw new CandidateGatewayError('UNAVAILABLE', 'Candidate request could not be completed');
    }
  }
}
