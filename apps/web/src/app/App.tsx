import { LogOut, Search, Upload, UsersRound, type LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'wouter';

import {
  isAuthProblem,
  type AuthenticatedSession,
  type AuthGateway,
} from '../features/auth/authGateway';
import { SignInPage } from '../features/auth/SignInPage';

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

type AppProps = {
  readonly authGateway?: AuthGateway;
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
  loggingOut,
  onLogout,
  session,
}: {
  readonly loggingOut: boolean;
  readonly onLogout: () => Promise<void>;
  readonly session: AuthenticatedSession;
}) {
  const [currentPath] = useLocation();
  const route = priorityRoutes.find((candidate) => candidate.path === currentPath);

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
        currentPath={currentPath}
        loggingOut={loggingOut}
        onLogout={onLogout}
        session={session}
      />
      <div className="app-content">
        <header className="topbar">
          <strong>{route.pageTitle}</strong>
          <Link className="topbar-search-link" href="/search">
            <Search aria-hidden="true" size={17} />
            Search candidates
          </Link>
        </header>
        <main className="workspace priority-workspace">
          <div className="workspace-heading">
            <div>
              <h1>{route.pageTitle}</h1>
            </div>
          </div>
          <section className="priority-placeholder" aria-label={`${route.pageTitle} foundation`}>
            <p>{route.description}</p>
          </section>
        </main>
      </div>
    </div>
  );
}

export default function App({ authGateway = unconfiguredAuthGateway }: AppProps) {
  const [currentPath, navigate] = useLocation();
  const requestedRoute = useRef<PriorityRoute['path']>(
    isPriorityRoutePath(currentPath) ? currentPath : '/candidates',
  );
  const [loggingOut, setLoggingOut] = useState(false);
  const [session, setSession] = useState<AuthenticatedSession | null | undefined>(undefined);
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
    return <main aria-label="Restoring session">Loading your workspace…</main>;
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
      session={session}
    />
  );
}
