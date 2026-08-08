import { ArrowLeft } from 'lucide-react';
import { useEffect, useState } from 'react';

import type { WorkspaceRole } from '../auth/authGateway';
import type { CandidateApplicationDetail, CandidateGateway } from './candidateGateway';
import { CandidateGatewayError } from './candidateGateway';
import { CandidateProfilePanel } from './CandidateProfilePanel';

type LoadState = 'LOADING' | 'READY' | 'NOT_FOUND' | 'FORBIDDEN' | 'ERROR';

export function CandidateApplicationProfilePage({
  applicationId,
  candidateGateway,
  role,
  onBack,
}: {
  readonly applicationId: string;
  readonly candidateGateway: CandidateGateway;
  readonly role: WorkspaceRole;
  readonly onBack: () => void;
}) {
  const [application, setApplication] = useState<CandidateApplicationDetail | null>(null);
  const [loadState, setLoadState] = useState<LoadState>('LOADING');
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [downloadState, setDownloadState] = useState<
    'IDLE' | 'DOWNLOADING' | 'COMPLETE' | 'ERROR' | 'BLOCKED'
  >('IDLE');

  useEffect(() => {
    let active = true;
    setApplication(null);
    setLoadState('LOADING');
    setDownloadState('IDLE');
    void candidateGateway
      .getApplication(applicationId)
      .then((loaded) => {
        if (!active) return;
        setApplication(loaded);
        setLoadState('READY');
      })
      .catch((error: unknown) => {
        if (!active) return;
        setLoadState(
          error instanceof CandidateGatewayError && error.code === 'NOT_FOUND'
            ? 'NOT_FOUND'
            : error instanceof CandidateGatewayError && error.code === 'FORBIDDEN'
              ? 'FORBIDDEN'
              : 'ERROR',
        );
      });
    return () => {
      active = false;
    };
  }, [applicationId, candidateGateway, loadAttempt]);

  async function downloadResume() {
    if (!application || downloadState === 'DOWNLOADING') return;
    setDownloadState('DOWNLOADING');
    try {
      const file = await candidateGateway.downloadResume(application.applicationId);
      if (typeof URL.createObjectURL === 'function') {
        const objectUrl = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = application.resumeFileName;
        link.click();
        URL.revokeObjectURL(objectUrl);
      }
      setDownloadState('COMPLETE');
    } catch (error: unknown) {
      setDownloadState(
        error instanceof CandidateGatewayError &&
          (error.code === 'RESUME_DOWNLOAD_FORBIDDEN' || error.code === 'RESUME_NOT_CLEAN')
          ? 'BLOCKED'
          : 'ERROR',
      );
    }
  }

  return (
    <section className="candidate-profile-page" aria-label="Candidate application profile">
      <button className="candidate-profile-back" type="button" onClick={onBack}>
        <ArrowLeft aria-hidden="true" size={17} />
        Back to candidates
      </button>

      {loadState === 'LOADING' ? (
        <div className="candidate-state-card" role="status">
          Loading candidate profile…
        </div>
      ) : loadState === 'NOT_FOUND' ? (
        <div className="candidate-state-card error-state" role="alert">
          <h2>Candidate application was not found.</h2>
          <p>Return to Candidates and select another application.</p>
        </div>
      ) : loadState === 'FORBIDDEN' ? (
        <div className="candidate-state-card forbidden-state" role="alert">
          <h2>You don&apos;t have access to this candidate profile.</h2>
          <p>Ask a workspace administrator to review your candidate access.</p>
        </div>
      ) : loadState === 'ERROR' ? (
        <div className="candidate-state-card error-state" role="alert">
          <h2>Candidate profile could not be loaded.</h2>
          <p>Check the connection and try this application again.</p>
          <button
            className="secondary-button"
            type="button"
            onClick={() => setLoadAttempt((attempt) => attempt + 1)}
          >
            Retry candidate profile
          </button>
        </div>
      ) : application ? (
        <CandidateProfilePanel
          application={application}
          role={role}
          onClose={onBack}
          onDownloadResume={() => void downloadResume()}
          downloadState={downloadState}
        />
      ) : null}
    </section>
  );
}
