import type { ApiClient } from '../../lib/apiClient';
import type { ImportTargetJob, JobGateway } from './jobGateway';

function isImportTargetJob(value: unknown): value is ImportTargetJob {
  if (typeof value !== 'object' || value === null) return false;
  const job = value as Record<string, unknown>;
  return (
    typeof job.id === 'string' &&
    typeof job.title === 'string' &&
    typeof job.department === 'string' &&
    typeof job.location === 'string' &&
    (job.status === 'OPEN' || job.status === 'ON_HOLD')
  );
}

export class HttpJobGateway implements JobGateway {
  constructor(private readonly apiClient: ApiClient) {}

  async listImportTargets(): Promise<readonly ImportTargetJob[]> {
    const response = await this.apiClient.request('/api/v1/jobs', { method: 'GET' }, true);
    if (!response.ok) throw new Error('Jobs request failed');
    const body: unknown = await response.json();
    if (!Array.isArray(body) || !body.every(isImportTargetJob)) {
      throw new Error('Jobs response is invalid');
    }
    return body;
  }
}
