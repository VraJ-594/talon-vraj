import type {
  CandidateApplicationDetail,
  CandidateApplicationSummary,
  CandidateGateway,
} from './candidateGateway';
import { CandidateGatewayError } from './candidateGateway';

const APPLICATIONS: readonly CandidateApplicationSummary[] = [
  {
    applicationId: 'application-nila-backend',
    candidateId: 'candidate-nila',
    candidateName: 'Nila Raman',
    jobTitle: 'Senior Backend Engineer',
    stage: 'Screening',
    location: 'Pune',
    totalExperienceMonths: 90,
    currentCompany: 'Atlas Systems',
    currentTitle: 'Staff Engineer',
    skills: ['TypeScript', 'Java', 'PostgreSQL'],
    currentCompensation: { currency: 'INR', minorUnits: 320_000_000 },
    expectedCompensation: { currency: 'INR', minorUnits: 400_000_000 },
    noticePeriodDays: 30,
    applicationDate: '2026-08-06',
    resumeStatus: 'CLEAN',
  },
  {
    applicationId: 'application-kai-product',
    candidateId: 'candidate-kai',
    candidateName: 'Kai Sen',
    jobTitle: 'Product Designer',
    stage: 'Application review',
    location: 'Bengaluru',
    totalExperienceMonths: 62,
    currentCompany: 'Northstar Studio',
    currentTitle: 'Product Designer',
    skills: ['Figma', 'Research', 'Design systems'],
    currentCompensation: { currency: 'INR', minorUnits: 220_000_000 },
    expectedCompensation: { currency: 'INR', minorUnits: 280_000_000 },
    noticePeriodDays: 45,
    applicationDate: '2026-08-05',
    resumeStatus: 'SCAN_PENDING',
  },
];

const APPLICATION_DETAILS: Readonly<Record<string, CandidateApplicationDetail>> = {
  'application-nila-backend': {
    ...APPLICATIONS[0],
    email: 'nila.raman@example.test',
    maskedPhone: '+91 ••••• 0184',
    source: 'Google Forms',
    availableFrom: '2026-09-01',
    additionalAnswers: [
      {
        question: 'Why are you interested in this role?',
        answer: 'Building reliable hiring systems at product scale.',
      },
      {
        question: 'Preferred working arrangement',
        answer: 'Hybrid in Pune.',
      },
    ],
    resumeFileName: 'nila-raman-resume.pdf',
    resumeDownloadAllowed: true,
  },
  'application-kai-product': {
    ...APPLICATIONS[1],
    email: 'kai.sen@example.test',
    maskedPhone: '+91 ••••• 7421',
    source: 'Careers page',
    availableFrom: '2026-09-20',
    additionalAnswers: [],
    resumeFileName: 'kai-sen-portfolio.pdf',
    resumeDownloadAllowed: false,
  },
};

type FixtureCandidatePermissions = {
  readonly viewCompensation: boolean;
  readonly downloadCleanResumes: boolean;
};

const ADMIN_PERMISSIONS: FixtureCandidatePermissions = {
  viewCompensation: true,
  downloadCleanResumes: true,
};

function applyPermissions<T extends CandidateApplicationSummary>(
  application: T,
  permissions: FixtureCandidatePermissions,
): T {
  const projected = {
    ...application,
    resumeDownloadAllowed:
      'resumeDownloadAllowed' in application
        ? application.resumeDownloadAllowed && permissions.downloadCleanResumes
        : undefined,
  };
  if (permissions.viewCompensation) return projected;
  return Object.fromEntries(
    Object.entries(projected).filter(
      ([key]) => key !== 'currentCompensation' && key !== 'expectedCompensation',
    ),
  ) as T;
}

export function createFixtureCandidateGateway(
  permissions: FixtureCandidatePermissions = ADMIN_PERMISSIONS,
): CandidateGateway {
  return {
    async listApplications() {
      return APPLICATIONS.map((application) => applyPermissions(application, permissions));
    },
    async getApplication(applicationId) {
      const detail = APPLICATION_DETAILS[applicationId];
      if (!detail) throw new CandidateGatewayError('NOT_FOUND', 'Application not found');
      return applyPermissions(detail, permissions);
    },
    async downloadResume(applicationId) {
      const detail = APPLICATION_DETAILS[applicationId];
      if (!permissions.downloadCleanResumes) {
        throw new CandidateGatewayError(
          'RESUME_DOWNLOAD_FORBIDDEN',
          'Resume download is not allowed',
        );
      }
      if (!detail || detail.resumeStatus !== 'CLEAN' || !detail.resumeDownloadAllowed) {
        throw new CandidateGatewayError('RESUME_NOT_CLEAN', 'Resume download is not allowed');
      }
      return new Blob(['Synthetic fixture resume.'], { type: 'application/pdf' });
    },
  };
}
