import { LogOut, Search, Upload, UsersRound, type LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'wouter';

import {
  isAuthProblem,
  type AuthenticatedSession,
  type AuthGateway,
} from '../features/auth/authGateway';
import { SignInPage } from '../features/auth/SignInPage';
import { CandidateApplicationProfilePage } from '../features/candidates/CandidateApplicationProfilePage';
import { CandidateWorkspace } from '../features/candidates/CandidateWorkspace';
import type { CandidateGateway } from '../features/candidates/candidateGateway';
import { ImportWizard } from '../features/imports/ImportWizard';
import { isOpaqueImportId, type ImportGateway } from '../features/imports/importGateway';
import type { JobGateway } from '../features/jobs/jobGateway';
import { CommandPalette } from '../features/search/CommandPalette';
import type { SearchGateway } from '../features/search/searchGateway';
import { SearchWorkspace } from '../features/search/SearchWorkspace';

type PriorityRoute = {
  readonly path: '/candidates' | '/imports' | '/search';
  readonly label: string;
  readonly pageTitle: string;
  readonly description: string;
  readonly icon: LucideIcon;
};

const priorityRoutes: readonly PriorityRoute[] = [
  {
    path: '/candidates',
    label: 'Candidates',
    pageTitle: 'Candidates',
    description: 'Candidate and application records will appear here after import.',
    icon: UsersRound,
  },
  {
    path: '/imports',
    label: 'Import applications',
    pageTitle: 'Import applications',
    description: 'Select a job and bring in application responses from CSV.',
    icon: Upload,
  },
  {
    path: '/search',
    label: 'Search',
    pageTitle: 'Search',
    description: 'Find candidates with keywords or structured filters.',
    icon: Search,
  },
];

function isPriorityRoutePath(path: string): path is PriorityRoute['path'] {
  return priorityRoutes.some((route) => route.path === path);
}

function candidateApplicationId(path: string): string | null {
  const match = /^\/candidates\/applications\/([A-Za-z0-9][A-Za-z0-9_-]{0,127})$/.exec(path);
  return match?.[1] ?? null;
}

function requestedLocation(
  path: string,
): PriorityRoute['path'] | `/imports?importId=${string}` | `/candidates/applications/${string}` {
  const applicationId = candidateApplicationId(path);
  if (applicationId) {
    return `/candidates/applications/${applicationId}`;
  }
  if (!isPriorityRoutePath(path)) {
    return '/candidates';
  }
  if (path === '/imports') {
    const importId = new URLSearchParams(window.location.search).get('importId');
    if (isOpaqueImportId(importId)) {
      return `/imports?importId=${encodeURIComponent(importId)}`;
    }
  }
  return path;
}

type AppProps = {
  readonly authGateway?: AuthGateway;
  readonly candidateGateway?: CandidateGateway;
  readonly importGateway?: ImportGateway;
  readonly jobGateway?: JobGateway;
  readonly searchGateway?: SearchGateway;
};

const unconfiguredAuthGateway: AuthGateway = {
  async restoreSession() {
    return null;
  },
  async login() {
    throw new Error('Authentication gateway is not configured');
  },
  async logout() {
    return undefined;
  },
};

const unconfiguredJobGateway: JobGateway = {
  async listImportTargets() {
    throw new Error('Job gateway is not configured');
  },
};

const unconfiguredImportGateway: ImportGateway = {
  processingAvailable: false,
  async downloadTemplate() {
    throw new Error('Import gateway is not configured');
  },
  async uploadCsv() {
    throw new Error('Import gateway is not configured');
  },
  async validate() {
    throw new Error('Import gateway is not configured');
  },
  async getPreview() {
    throw new Error('Import gateway is not configured');
  },
  async confirm() {
    throw new Error('Import gateway is not configured');
  },
  async getImport() {
    throw new Error('Import gateway is not configured');
  },
  async retryRow() {
    throw new Error('Import gateway is not configured');
  },
  async downloadErrors() {
    throw new Error('Import gateway is not configured');
  },
};

function Brand() {
  return (
    <Link className="brand" href="/candidates" aria-label="Talon home">
      <span className="brand-mark" aria-hidden="true">
        <span />
      </span>
      <span>Talon</span>
    </Link>
  );
}

function Sidebar({
  currentPath,
  loggingOut,
  onLogout,
  session,
}: {
  currentPath: string;
  loggingOut: boolean;
  onLogout: () => Promise<void>;
  session: AuthenticatedSession;
}) {
  return (
    <aside className="sidebar">
      <div className="sidebar-topline">
        <Brand />
        <kbd>⌘K</kbd>
      </div>

      <nav aria-label="Primary" className="primary-nav">
        <section className="nav-group">
          <h2>Workspace</h2>
          <ul>
            {priorityRoutes.map((route) => {
              const Icon = route.icon;
              const active = route.path === currentPath;

              return (
                <li key={route.path}>
                  <Link
                    className={active ? 'nav-link active' : 'nav-link'}
                    href={route.path}
                    aria-current={active ? 'page' : undefined}
                  >
                    <Icon aria-hidden="true" size={18} strokeWidth={1.8} />
                    <span>{route.label}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      </nav>

      <div className="profile-card">
        <span className="avatar avatar-green" aria-hidden="true">
          MR
        </span>
        <span className="profile-copy">
          <strong>{session.displayName}</strong>
          <small>Workspace Admin</small>
        </span>
        <button
          type="button"
          aria-label={loggingOut ? 'Signing out…' : 'Sign out'}
          disabled={loggingOut}
          onClick={() => void onLogout()}
        >
          <LogOut aria-hidden="true" size={17} />
        </button>
      </div>
    </aside>
  );
}

function ProtectedWorkspace({
  candidateGateway,
  importGateway,
  jobGateway,
  loggingOut,
  onLogout,
  searchGateway,
  session,
}: {
  readonly candidateGateway?: CandidateGateway;
  readonly importGateway: ImportGateway;
  readonly jobGateway: JobGateway;
  readonly loggingOut: boolean;
  readonly onLogout: () => Promise<void>;
  readonly searchGateway?: SearchGateway;
  readonly session: AuthenticatedSession;
}) {
  const [currentPath, navigate] = useLocation();
  const applicationId = candidateApplicationId(currentPath);
  const route = applicationId
    ? priorityRoutes.find((candidate) => candidate.path === '/candidates')
    : priorityRoutes.find((candidate) => candidate.path === currentPath);

  if (!route) {
    return (
      <main className="workspace">
        <h1>Page not found</h1>
        <Link href="/candidates">Return to Candidates</Link>
      </main>
    );
  }

  return (
    <div className="app-shell">
      <Sidebar
        currentPath={applicationId ? '/candidates' : currentPath}
        loggingOut={loggingOut}
        onLogout={onLogout}
        session={session}
      />
      {searchGateway ? <CommandPalette searchGateway={searchGateway} /> : null}
      <div className="app-content">
        <header className="topbar">
          <h1>{applicationId ? 'Candidate profile' : route.pageTitle}</h1>
          <Link className="topbar-search-link" href="/search">
            <Search aria-hidden="true" size={17} />
            Search candidates
          </Link>
        </header>
        <main className="workspace priority-workspace">
          {applicationId && candidateGateway ? (
            <CandidateApplicationProfilePage
              applicationId={applicationId}
              candidateGateway={candidateGateway}
              role={session.role}
              onBack={() => navigate('/candidates')}
            />
          ) : route.path === '/candidates' && candidateGateway ? (
            <CandidateWorkspace candidateGateway={candidateGateway} role={session.role} />
          ) : route.path === '/imports' ? (
            <ImportWizard importGateway={importGateway} jobGateway={jobGateway} />
          ) : route.path === '/search' && searchGateway ? (
            <SearchWorkspace searchGateway={searchGateway} />
          ) : (
            <section className="priority-placeholder" aria-label={`${route.pageTitle} foundation`}>
              <p>{route.description}</p>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}

function SessionRestoringScreen() {
  return (
    <main className="session-restoring-page" aria-label="Restoring session" aria-busy="true">
      <div className="brand" aria-hidden="true">
        <span className="brand-mark">
          <span />
        </span>
        <span>Talon</span>
      </div>
      <span className="session-restoring-spinner" aria-hidden="true" />
      <span className="sr-only" role="status">
        Restoring your session
      </span>
    </main>
  );
}

export default function App({
  authGateway = unconfiguredAuthGateway,
  candidateGateway,
  importGateway = unconfiguredImportGateway,
  jobGateway = unconfiguredJobGateway,
  searchGateway,
}: AppProps) {
  const [currentPath, navigate] = useLocation();
  const requestedRoute = useRef(requestedLocation(currentPath));
  const [loggingOut, setLoggingOut] = useState(false);
  const [session, setSession] = useState<AuthenticatedSession | null | undefined>(() =>
    authGateway.cachedSession?.(),
  );
  const [sessionMessage, setSessionMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    void authGateway
      .restoreSession()
      .then((restoredSession) => {
        if (!active) {
          return;
        }

        if (!restoredSession && window.location.pathname !== '/sign-in') {
          navigate('/sign-in', { replace: true });
        }
        setSession(restoredSession);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }

        if (isAuthProblem(error) && error.code === 'SESSION_EXPIRED') {
          setSessionMessage('Your session expired. Sign in again to continue.');
        }
        navigate('/sign-in', { replace: true });
        setSession(null);
      });

    return () => {
      active = false;
    };
  }, [authGateway, navigate]);

  if (session === undefined) {
    return <SessionRestoringScreen />;
  }

  if (!session) {
    return (
      <SignInPage
        statusMessage={sessionMessage}
        onLogin={async (credentials) => {
          const authenticatedSession = await authGateway.login(credentials);
          setSession(authenticatedSession);
          navigate(requestedRoute.current, { replace: true });
        }}
      />
    );
  }

  return (
    <ProtectedWorkspace
      candidateGateway={candidateGateway}
      importGateway={importGateway}
      jobGateway={jobGateway}
      loggingOut={loggingOut}
      onLogout={async () => {
        setLoggingOut(true);
        try {
          await authGateway.logout();
          setSession(null);
          navigate('/sign-in', { replace: true });
        } finally {
          setLoggingOut(false);
        }
      }}
      searchGateway={searchGateway}
      session={session}
    />
  );
}
