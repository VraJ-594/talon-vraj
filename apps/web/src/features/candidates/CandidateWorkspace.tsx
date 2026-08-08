import { BriefcaseBusiness, FileCheck2, MapPin } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'wouter';

import type { WorkspaceRole } from '../auth/authGateway';
import type {
  AnnualCompensation,
  CandidateApplicationSummary,
  CandidateGateway,
  ResumeFileStatus,
} from './candidateGateway';
import { CandidateGatewayError } from './candidateGateway';

const RESUME_STATUS_LABELS: Readonly<Record<ResumeFileStatus, string>> = {
  NO_RESUME: 'No resume uploaded',
  QUARANTINED: 'Resume quarantined',
  SCAN_PENDING: 'Resume scan pending',
  CLEAN: 'Resume clean',
  FAILED: 'Resume failed',
  UNSAFE: 'Resume unsafe',
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
}: {
  readonly application: CandidateApplicationSummary;
  readonly compensationAccessAllowed: boolean;
}) {
  const hasCompensation =
    application.currentCompensation != null && application.expectedCompensation != null;

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
          <Link
            aria-label={`Open ${application.candidateName} application`}
            href={`/candidates/applications/${encodeURIComponent(application.applicationId)}`}
          >
            {application.candidateName}
          </Link>
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
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState(false);
  const [loadError, setLoadError] = useState<'FORBIDDEN' | 'UNAVAILABLE' | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setApplications(null);
    setLoadError(null);
    void candidateGateway
      .listApplications()
      .then((loaded) => {
        if (active) {
          setApplications(loaded.items);
          setNextCursor(loaded.nextCursor);
        }
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

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    setLoadMoreError(false);
    try {
      const page = await candidateGateway.listApplications(nextCursor);
      setApplications((current) => {
        const existing = new Set((current ?? []).map((item) => item.applicationId));
        return [
          ...(current ?? []),
          ...page.items.filter((item) => !existing.has(item.applicationId)),
        ];
      });
      setNextCursor(page.nextCursor);
    } catch {
      setLoadMoreError(true);
    } finally {
      setLoadingMore(false);
    }
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
        <>
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
              />
            ))}
          </div>
          {nextCursor ? (
            <div className="candidate-pagination">
              <button
                type="button"
                className="secondary-button"
                disabled={loadingMore}
                onClick={() => void loadMore()}
              >
                {loadingMore ? 'Loading more…' : 'Load more applications'}
              </button>
              {loadMoreError ? <p role="alert">More applications could not be loaded.</p> : null}
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}
