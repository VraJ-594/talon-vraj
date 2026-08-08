import { describe, expect, it, vi } from 'vitest';

import { ApiClient } from '../../lib/apiClient';
import { CandidateGatewayError } from './candidateGateway';
import { HttpCandidateGateway } from './httpCandidateGateway';

const SUMMARY = {
  applicationId: '40000000-0000-0000-0000-000000000001',
  candidateId: '30000000-0000-0000-0000-000000000001',
  candidateName: 'Asha Mehta',
  jobTitle: 'Senior Platform Engineer',
  stage: 'SCREENING',
  location: 'Pune',
  totalExperienceMonths: 96,
  currentCompany: 'Finch Labs',
  currentTitle: 'Senior Java Engineer',
  skills: ['Java', 'Spring Boot', 'PostgreSQL'],
  currentCompensation: { currency: 'INR', minorUnits: 320_000_000 },
  expectedCompensation: { currency: 'INR', minorUnits: 380_000_000 },
  noticePeriodDays: 30,
  applicationDate: '2026-08-06',
  resumeStatus: 'NO_RESUME',
} as const;

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('HttpCandidateGateway', () => {
  it('loads an authenticated opaque application page', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ items: [SUMMARY], nextCursor: 'opaque-next' }));
    const apiClient = new ApiClient('http://localhost:8080', fetcher);
    apiClient.setAccessToken('header.payload.signature');
    const gateway = new HttpCandidateGateway(apiClient);

    const page = await gateway.listApplications('opaque-current');

    expect(page).toEqual({ items: [SUMMARY], nextCursor: 'opaque-next' });
    const [url, request] = fetcher.mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/v1/applications?limit=50&cursor=opaque-current');
    expect(request).toMatchObject({ method: 'GET', credentials: 'include' });
    expect(new Headers(request?.headers).get('Authorization')).toBe(
      'Bearer header.payload.signature',
    );
  });

  it('loads flat detail and downloads only the authorized response body', async () => {
    const detail = {
      ...SUMMARY,
      email: 'search-demo@example.test',
      maskedPhone: '••••••1234',
      source: 'SEARCH_DEMO',
      availableFrom: '2026-09-15',
      additionalAnswers: [{ question: 'Preferred shift', answer: 'Day' }],
      resumeFileName: 'synthetic-resume.pdf',
      resumeDownloadAllowed: true,
      resumeStatus: 'CLEAN',
    } as const;
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(detail))
      .mockResolvedValueOnce(
        new Response('%PDF-1.7 synthetic', {
          headers: { 'Content-Type': 'application/pdf' },
        }),
      );
    const apiClient = new ApiClient('', fetcher);
    apiClient.setAccessToken('header.payload.signature');
    const gateway = new HttpCandidateGateway(apiClient);

    await expect(gateway.getApplication(SUMMARY.applicationId)).resolves.toEqual(detail);
    await expect(gateway.downloadResume(SUMMARY.applicationId)).resolves.toMatchObject({
      type: 'application/pdf',
    });
    expect(fetcher.mock.calls[1][0]).toBe(
      `/api/v1/applications/${SUMMARY.applicationId}/resume-download`,
    );
  });

  it.each([
    ['CANDIDATE_FORBIDDEN', 'FORBIDDEN', 403],
    ['CANDIDATE_APPLICATION_NOT_FOUND', 'NOT_FOUND', 404],
    ['RESUME_NOT_CLEAN', 'RESUME_NOT_CLEAN', 409],
  ] as const)('maps %s to the safe frontend code %s', async (backendCode, frontendCode, status) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          code: backendCode,
          detail: 'private database or storage detail',
        },
        status,
      ),
    );
    const gateway = new HttpCandidateGateway(new ApiClient('', fetcher));

    const error = await gateway.listApplications().catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(CandidateGatewayError);
    expect(error).toMatchObject({ code: frontendCode });
    expect((error as Error).message).not.toContain('private database');
  });
});
