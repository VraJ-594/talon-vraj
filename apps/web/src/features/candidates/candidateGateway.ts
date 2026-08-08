export type ResumeFileStatus =
  'NO_RESUME' | 'QUARANTINED' | 'SCAN_PENDING' | 'CLEAN' | 'FAILED' | 'UNSAFE';

export type AnnualCompensation = {
  readonly currency: string;
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
  readonly currentCompensation?: AnnualCompensation | null;
  readonly expectedCompensation?: AnnualCompensation | null;
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
  readonly availableFrom: string | null;
  readonly additionalAnswers: readonly AdditionalApplicationAnswer[];
  readonly resumeFileName: string;
  readonly resumeDownloadAllowed: boolean;
};

export type CandidateApplicationPage = {
  readonly items: readonly CandidateApplicationSummary[];
  readonly nextCursor: string | null;
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
  listApplications(cursor?: string | null): Promise<CandidateApplicationPage>;
  getApplication(applicationId: string): Promise<CandidateApplicationDetail>;
  downloadResume(applicationId: string): Promise<Blob>;
}
