import type { ImportTargetJob, JobGateway } from './jobGateway';

const IMPORT_TARGETS: readonly ImportTargetJob[] = [
  {
    id: 'job-backend-2026',
    title: 'Senior Backend Engineer',
    department: 'Engineering',
    location: 'Pune · Hybrid',
    status: 'OPEN',
  },
  {
    id: 'job-product-2026',
    title: 'Product Designer',
    department: 'Product',
    location: 'Bengaluru · Hybrid',
    status: 'OPEN',
  },
];

export function createFixtureJobGateway(): JobGateway {
  return {
    async listImportTargets() {
      return IMPORT_TARGETS;
    },
  };
}
