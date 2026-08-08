import { CalendarDays, Download, Mail, Phone, X } from 'lucide-react';
import { useEffect, useRef } from 'react';

import type { WorkspaceRole } from '../auth/authGateway';
import type { AnnualCompensation, CandidateApplicationDetail } from './candidateGateway';

function canViewCompensation(role: WorkspaceRole) {
  return role === 'WORKSPACE_ADMIN' || role === 'RECRUITER';
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
    .format(new Date(`${value}T00:00:00Z`))
    .replace('Sept', 'Sep');
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

export function CandidateProfilePanel({
  application,
  role,
  onClose,
  onDownloadResume,
  downloadState,
}: {
  readonly application: CandidateApplicationDetail;
  readonly role: WorkspaceRole;
  readonly onClose: () => void;
  readonly onDownloadResume: () => void;
  readonly downloadState: 'IDLE' | 'DOWNLOADING' | 'COMPLETE' | 'ERROR' | 'BLOCKED';
}) {
  const showCompensation =
    canViewCompensation(role) &&
    application.currentCompensation !== undefined &&
    application.expectedCompensation !== undefined;
  const canDownloadResume =
    canViewCompensation(role) &&
    application.resumeStatus === 'CLEAN' &&
    application.resumeDownloadAllowed &&
    downloadState !== 'BLOCKED';
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, [application.applicationId]);

  return (
    <aside
      id="candidate-profile-panel"
      className="candidate-profile-panel"
      aria-label={`${application.candidateName} profile`}
    >
      <header className="candidate-profile-header">
        <div>
          <p className="eyebrow">Selected application</p>
          <h2 ref={headingRef} tabIndex={-1}>
            {application.candidateName}
          </h2>
          <span>
            {application.jobTitle} · {application.stage}
          </span>
        </div>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Close profile">
          <X aria-hidden="true" size={18} />
        </button>
      </header>

      <div className="candidate-contact-grid">
        <span>
          <Mail aria-hidden="true" size={15} />
          {application.email}
        </span>
        <span>
          <Phone aria-hidden="true" size={15} />
          {application.maskedPhone}
        </span>
        <span>{application.source}</span>
        <span>
          <CalendarDays aria-hidden="true" size={15} />
          Available{' '}
          <time dateTime={application.availableFrom}>{formatDate(application.availableFrom)}</time>
        </span>
      </div>

      <section className="candidate-profile-section" aria-labelledby="application-details-heading">
        <h3 id="application-details-heading">Application details</h3>
        <dl className="candidate-detail-grid">
          <div>
            <dt>Current role</dt>
            <dd>
              {application.currentTitle}, {application.currentCompany}
            </dd>
          </div>
          <div>
            <dt>Notice period</dt>
            <dd>{application.noticePeriodDays} days</dd>
          </div>
          {showCompensation ? (
            <>
              <div>
                <dt>Current compensation</dt>
                <dd>{formatCompensation(application.currentCompensation!)}</dd>
              </div>
              <div>
                <dt>Expected compensation</dt>
                <dd>{formatCompensation(application.expectedCompensation!)}</dd>
              </div>
            </>
          ) : null}
        </dl>
        {!canViewCompensation(role) ? (
          <p className="permission-note">
            Compensation is available only to Admin and Recruiter roles.
          </p>
        ) : !showCompensation ? (
          <p className="permission-note">Compensation was not included in this application.</p>
        ) : null}
      </section>

      <section className="candidate-profile-section" aria-labelledby="additional-answers-heading">
        <h3 id="additional-answers-heading">Additional form answers</h3>
        {application.additionalAnswers.length ? (
          <dl className="candidate-answer-list">
            {application.additionalAnswers.map((item) => (
              <div key={item.question}>
                <dt>{item.question}</dt>
                <dd>{item.answer}</dd>
              </div>
            ))}
          </dl>
        ) : (
          <p>No additional answers were submitted.</p>
        )}
      </section>

      <section className="candidate-profile-section candidate-resume-card" aria-label="Resume file">
        <div>
          <h3>Resume file</h3>
          <strong>{application.resumeFileName}</strong>
          <span>File status: {application.resumeStatus.replaceAll('_', ' ').toLowerCase()}</span>
        </div>
        {downloadState === 'BLOCKED' ? (
          <p role="alert">Resume download is no longer available.</p>
        ) : canDownloadResume ? (
          <button
            type="button"
            className="secondary-button"
            onClick={onDownloadResume}
            disabled={downloadState === 'DOWNLOADING'}
          >
            <Download aria-hidden="true" size={16} />
            {downloadState === 'DOWNLOADING' ? 'Preparing resume…' : 'Download clean resume'}
          </button>
        ) : (
          <p className="permission-note">Download unlocks after the file is verified clean.</p>
        )}
        {downloadState === 'COMPLETE' ? <p role="status">Resume download started.</p> : null}
        {downloadState === 'ERROR' ? (
          <p role="alert">The resume could not be downloaded. Try again.</p>
        ) : null}
      </section>
    </aside>
  );
}
