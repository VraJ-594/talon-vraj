import { describe, expect, it } from 'vitest';

import { CandidateGatewayError } from './candidateGateway';
import { createFixtureCandidateGateway } from './fixtureCandidateGateway';

describe('fixture candidate permissions', () => {
  it('redacts compensation and resume capability from restricted projections', async () => {
    const gateway = createFixtureCandidateGateway({
      viewCompensation: false,
      downloadCleanResumes: false,
    });

    const [summary] = await gateway.listApplications();
    const detail = await gateway.getApplication('application-nila-backend');

    expect(summary).not.toHaveProperty('currentCompensation');
    expect(summary).not.toHaveProperty('expectedCompensation');
    expect(detail).not.toHaveProperty('currentCompensation');
    expect(detail).not.toHaveProperty('expectedCompensation');
    expect(detail.resumeDownloadAllowed).toBe(false);
    await expect(gateway.downloadResume(detail.applicationId)).rejects.toMatchObject({
      code: 'RESUME_DOWNLOAD_FORBIDDEN',
    } satisfies Partial<CandidateGatewayError>);
  });
});
