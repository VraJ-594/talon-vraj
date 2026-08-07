export type ImportTargetJob = {
  readonly id: string;
  readonly title: string;
  readonly department: string;
  readonly location: string;
  readonly status: 'OPEN' | 'ON_HOLD';
};

export interface JobGateway {
  listImportTargets(): Promise<readonly ImportTargetJob[]>;
}
