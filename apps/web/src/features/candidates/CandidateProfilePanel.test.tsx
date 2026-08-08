import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { CandidateApplicationDetail } from './candidateGateway';
import { CandidateProfilePanel } from './CandidateProfilePanel';

const importedApplication = {
  applicationId: '40000000-0000-0000-0000-000000000001',
  candidateId: '30000000-0000-0000-0000-000000000001',
  candidateName: 'Drive Smoke',
  jobTitle: 'Import Smoke Engineer',
  stage: 'APPLIED',
  location: 'Pune',
  totalExperienceMonths: 66,
  currentCompany: 'Talon',
  currentTitle: 'Engineer',
  skills: ['Java', 'PostgreSQL'],
  currentCompensation: null,
  expectedCompensation: null,
  noticePeriodDays: 30,
  applicationDate: '2026-08-08',
  resumeStatus: 'QUARANTINED',
  email: 'drive-smoke@example.test',
  maskedPhone: 'Not provided',
  source: '',
  availableFrom: null,
  additionalAnswers: [],
  resumeFileName: 'resume.pdf',
  resumeDownloadAllowed: false,
} as unknown as CandidateApplicationDetail;

describe('CandidateProfilePanel', () => {
  it('renders an imported profile when optional application fields are absent', () => {
    render(
      <CandidateProfilePanel
        application={importedApplication}
        role="WORKSPACE_ADMIN"
        onClose={vi.fn()}
        onDownloadResume={vi.fn()}
        downloadState="IDLE"
      />,
    );

    expect(screen.getByRole('heading', { name: 'Drive Smoke' })).toBeInTheDocument();
    expect(screen.getByText('Availability not provided')).toBeInTheDocument();
    expect(screen.getByText('Source not provided')).toBeInTheDocument();
    expect(
      screen.getByText('Compensation was not included in this application.'),
    ).toBeInTheDocument();
  });
});
