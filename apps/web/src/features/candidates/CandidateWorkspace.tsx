import { BriefcaseBusiness, FileCheck2, MapPin } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'wouter';

import type { WorkspaceRole } from '../auth/authGateway';
import type {
  AnnualCompensation,
  CandidateApplicationDetail,
  CandidateApplicationSummary,
  CandidateGateway,
  ResumeFileStatus,
} from './candidateGateway';
import { CandidateGatewayError } from './candidateGateway';
import { CandidateProfilePanel } from './CandidateProfilePanel';

const RESUME_STATUS_LABELS: Readonly<Record<ResumeFileStatus, string>> = {
  FETCHING_RESUME: 'Resume fetching',
  RESUME_QUARANTINED: 'Resume quarantined',
  SCAN_PENDING: 'Resume scan pending',
  EXTRACTING_TEXT: 'Resume extracting text',
  CLEAN: 'Resume clean',
  FAILED: 'Resume failed',
  UNSAFE_FILE: 'Resume unsafe',
};

function canViewCompensation(role: WorkspaceRole) {
  return role === 'WORKSPACE_ADMIN' || role === 'RECRUITER';
}

function formatExperience(months: number) {
  const years = Math.floor(months / 12);
  const remainingMonths = months % 12;
  return `${years}y${remainingMonths ? ` ${remainingMonths}m` : ''}`;
}

function formatApplicationDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`));
}

function formatCompensation(value: AnnualCompensation) {
  if (value.currency === 'INR') {
    return `₹${(value.minorUnits / 100 / 100_000).toFixed(2)} LPA`;
  }
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: value.currency,
    maximumFractionDigits: 0,
  }).format(value.minorUnits / 100);
}

function CandidateApplicationRow({
  application,
  compensationAccessAllowed,
  expanded,
  onOpen,
}: {
  readonly application: CandidateApplicationSummary;
  readonly compensationAccessAllowed: boolean;
  readonly expanded: boolean;
  readonly onOpen: (trigger: HTMLButtonElement) => void;
}) {
  const hasCompensation =
    application.currentCompensation !== undefined && application.expectedCompensation !== undefined;

  return (
    <article className="candidate-application-row">
      <div className="candidate-identity-cell">
        <span className="candidate-initials" aria-hidden="true">
          {application.candidateName
            .split(' ')
            .map((part) => part[0])
            .join('')}
        </span>
        <div>
          <button
            type="button"
            aria-label={`Open ${application.candidateName} application`}
            aria-expanded={expanded}
            aria-controls={expanded ? 'candidate-profile-panel' : undefined}
            onClick={(event) => onOpen(event.currentTarget)}
          >
            {application.candidateName}
          </button>
          <span>
            <MapPin aria-hidden="true" size={13} />
            {application.location}
          </span>
          <small>
            {application.currentCompany} · {application.currentTitle}
          </small>
        </div>
      </div>

      <div className="candidate-job-cell" role="group" aria-label="Job and application stage">
        <strong>
          <BriefcaseBusiness aria-hidden="true" size={14} />
          {application.jobTitle}
        </strong>
        <span>{application.stage}</span>
        <small>{formatApplicationDate(application.applicationDate)}</small>
      </div>

      <div className="candidate-experience-cell" role="group" aria-label="Experience and skills">
        <strong>{formatExperience(application.totalExperienceMonths)}</strong>
        <div className="candidate-skill-list">
          {application.skills.map((skill) => (
            <span key={skill}>{skill}</span>
          ))}
        </div>
      </div>

      <div
        className="candidate-compensation-cell"
        role="group"
        aria-label="Compensation and notice period"
      >
        {compensationAccessAllowed && hasCompensation ? (
          <>
            <span>Current {formatCompensation(application.currentCompensation!)}</span>
            <span>Expected {formatCompensation(application.expectedCompensation!)}</span>
          </>
        ) : compensationAccessAllowed ? (
          <span>Compensation unavailable</span>
        ) : (
          <span>Compensation restricted</span>
        )}
        <small>{application.noticePeriodDays} days</small>
      </div>

      <div className={`candidate-resume-state status-${application.resumeStatus.toLowerCase()}`}>
        <FileCheck2 aria-hidden="true" size={15} />
        {RESUME_STATUS_LABELS[application.resumeStatus]}
      </div>
    </article>
  );
}

export function CandidateWorkspace({
  candidateGateway,
  role,
}: {
  readonly candidateGateway: CandidateGateway;
  readonly role: WorkspaceRole;
}) {
  const [applications, setApplications] = useState<readonly CandidateApplicationSummary[] | null>(
    null,
  );
  const [selectedApplication, setSelectedApplication] = useState<CandidateApplicationDetail | null>(
    null,
  );
  const [loadError, setLoadError] = useState<'FORBIDDEN' | 'UNAVAILABLE' | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [downloadState, setDownloadState] = useState<
    'IDLE' | 'DOWNLOADING' | 'COMPLETE' | 'ERROR' | 'BLOCKED'
  >('IDLE');
  const [profileRequest, setProfileRequest] = useState<{
    readonly applicationId: string;
    readonly candidateName: string;
    readonly status: 'LOADING' | 'ERROR';
  } | null>(null);
  const profileRequestSequence = useRef(0);
  const downloadRequestSequence = useRef(0);
  const profileTrigger = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    let active = true;
    setApplications(null);
    setLoadError(null);
    void candidateGateway
      .listApplications()
      .then((loaded) => {
        if (active) setApplications(loaded);
      })
      .catch((error: unknown) => {
        if (!active) return;
        setLoadError(
          error instanceof CandidateGatewayError && error.code === 'FORBIDDEN'
            ? 'FORBIDDEN'
            : 'UNAVAILABLE',
        );
      });
    return () => {
      active = false;
    };
  }, [candidateGateway, loadAttempt]);

  async function openApplication(
    applicationId: string,
    candidateName: string,
    trigger?: HTMLButtonElement,
  ) {
    if (trigger) profileTrigger.current = trigger;
    const requestSequence = ++profileRequestSequence.current;
    downloadRequestSequence.current += 1;
    setSelectedApplication(null);
    setDownloadState('IDLE');
    setProfileRequest({ applicationId, candidateName, status: 'LOADING' });
    try {
      const loaded = await candidateGateway.getApplication(applicationId);
      if (requestSequence !== profileRequestSequence.current) return;
      setSelectedApplication(loaded);
      setProfileRequest(null);
    } catch {
      if (requestSequence !== profileRequestSequence.current) return;
      setProfileRequest({ applicationId, candidateName, status: 'ERROR' });
    }
  }

  async function downloadResume() {
    if (!selectedApplication) return;
    const requestSequence = ++downloadRequestSequence.current;
    const applicationId = selectedApplication.applicationId;
    setDownloadState('DOWNLOADING');
    try {
      const file = await candidateGateway.downloadResume(applicationId);
      if (requestSequence !== downloadRequestSequence.current) return;
      if (typeof URL.createObjectURL === 'function') {
        const objectUrl = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = selectedApplication.resumeFileName;
        link.click();
        URL.revokeObjectURL(objectUrl);
      }
      setDownloadState('COMPLETE');
    } catch (error: unknown) {
      if (requestSequence !== downloadRequestSequence.current) return;
      setDownloadState(
        error instanceof CandidateGatewayError &&
          (error.code === 'RESUME_DOWNLOAD_FORBIDDEN' || error.code === 'RESUME_NOT_CLEAN')
          ? 'BLOCKED'
          : 'ERROR',
      );
    }
  }

  function closeApplication() {
    const trigger = profileTrigger.current;
    profileRequestSequence.current += 1;
    downloadRequestSequence.current += 1;
    setProfileRequest(null);
    setSelectedApplication(null);
    setDownloadState('IDLE');
    profileTrigger.current = null;
    queueMicrotask(() => trigger?.focus());
  }

  return (
    <section className="candidate-workspace" aria-label="Candidate applications">
      <header className="candidate-workspace-heading">
        <div>
          <p className="eyebrow">Active applications</p>
          <h2>Application pipeline</h2>
        </div>
        <span>
          {loadError
            ? 'Unavailable'
            : applications === null
              ? 'Loading…'
              : `${applications.length} records`}
        </span>
      </header>

      {loadError === 'FORBIDDEN' ? (
        <div
          className="candidate-state-card forbidden-state"
          role="alert"
          aria-label="You don't have access to candidate applications."
        >
          <h3>You don&apos;t have access to candidate applications.</h3>
          <p>Ask a workspace administrator to review your candidate access.</p>
        </div>
      ) : loadError === 'UNAVAILABLE' ? (
        <div
          className="candidate-state-card error-state"
          role="alert"
          aria-label="Candidate applications could not be loaded."
        >
          <h3>Candidate applications could not be loaded.</h3>
          <p>Check your connection and try again.</p>
          <button
            type="button"
            className="secondary-button"
            onClick={() => setLoadAttempt((attempt) => attempt + 1)}
          >
            Retry candidate applications
          </button>
        </div>
      ) : applications === null ? (
        <p role="status">Loading candidate applications…</p>
      ) : applications.length === 0 ? (
        <div className="candidate-state-card empty-state">
          <h3>No candidate applications yet.</h3>
          <p>Import candidate records to start reviewing applications.</p>
          <Link className="secondary-button" href="/imports">
            Import candidates
          </Link>
        </div>
      ) : (
        <div
          className="candidate-application-list"
          role="region"
          aria-label="Candidate application roster"
          tabIndex={0}
        >
          {applications.map((application) => (
            <CandidateApplicationRow
              key={application.applicationId}
              application={application}
              compensationAccessAllowed={canViewCompensation(role)}
              expanded={selectedApplication?.applicationId === application.applicationId}
              onOpen={(trigger) =>
                void openApplication(application.applicationId, application.candidateName, trigger)
              }
            />
          ))}
        </div>
      )}
      {profileRequest?.status === 'LOADING' ? (
        <p className="candidate-state-card" role="status">
          Loading {profileRequest.candidateName} profile…
        </p>
      ) : profileRequest?.status === 'ERROR' ? (
        <div
          className="candidate-state-card error-state"
          role="alert"
          aria-label="Candidate profile could not be loaded."
        >
          <h3>Candidate profile could not be loaded.</h3>
          <p>Try the selected application again.</p>
          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              void openApplication(profileRequest.applicationId, profileRequest.candidateName)
            }
          >
            Retry candidate profile
          </button>
        </div>
      ) : null}
      {selectedApplication ? (
        <CandidateProfilePanel
          application={selectedApplication}
          role={role}
          onClose={closeApplication}
          onDownloadResume={() => void downloadResume()}
          downloadState={downloadState}
        />
      ) : null}
    </section>
  );
}
