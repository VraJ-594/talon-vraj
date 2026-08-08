export type ResumeFileStatus =
  | 'FETCHING_RESUME'
  | 'RESUME_QUARANTINED'
  | 'SCAN_PENDING'
  | 'EXTRACTING_TEXT'
  | 'CLEAN'
  | 'FAILED'
  | 'UNSAFE_FILE';

export type AnnualCompensation = {
  readonly currency: 'INR' | 'USD';
  readonly minorUnits: number;
};

export type CandidateApplicationSummary = {
  readonly applicationId: string;
  readonly candidateId: string;
  readonly candidateName: string;
  readonly jobTitle: string;
  readonly stage: string;
  readonly location: string;
  readonly totalExperienceMonths: number;
  readonly currentCompany: string;
  readonly currentTitle: string;
  readonly skills: readonly string[];
  readonly currentCompensation?: AnnualCompensation;
  readonly expectedCompensation?: AnnualCompensation;
  readonly noticePeriodDays: number;
  readonly applicationDate: string;
  readonly resumeStatus: ResumeFileStatus;
};

export type AdditionalApplicationAnswer = {
  readonly question: string;
  readonly answer: string;
};

export type CandidateApplicationDetail = CandidateApplicationSummary & {
  readonly email: string;
  readonly maskedPhone: string;
  readonly source: string;
  readonly availableFrom: string;
  readonly additionalAnswers: readonly AdditionalApplicationAnswer[];
  readonly resumeFileName: string;
  readonly resumeDownloadAllowed: boolean;
};

export type CandidateGatewayErrorCode =
  'FORBIDDEN' | 'NOT_FOUND' | 'RESUME_DOWNLOAD_FORBIDDEN' | 'RESUME_NOT_CLEAN' | 'UNAVAILABLE';

export class CandidateGatewayError extends Error {
  constructor(
    readonly code: CandidateGatewayErrorCode,
    message: string,
  ) {
    super(message);
    this.name = 'CandidateGatewayError';
  }
}

export interface CandidateGateway {
  listApplications(): Promise<readonly CandidateApplicationSummary[]>;
  getApplication(applicationId: string): Promise<CandidateApplicationDetail>;
  downloadResume(applicationId: string): Promise<Blob>;
}
