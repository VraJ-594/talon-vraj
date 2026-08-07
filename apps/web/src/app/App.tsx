import {
  BarChart3,
  Bell,
  BriefcaseBusiness,
  CalendarDays,
  ChevronDown,
  FileText,
  Inbox,
  KanbanSquare,
  LogOut,
  Plus,
  Search,
  UserRound,
  UsersRound,
} from 'lucide-react';
import { useEffect, useRef } from 'react';

type NavigationItem = {
  readonly label: string;
  readonly href: string;
  readonly count?: number;
  readonly icon: typeof BriefcaseBusiness;
  readonly active?: boolean;
};

type Job = {
  readonly id: string;
  readonly title: string;
  readonly location: string;
  readonly owner: string;
  readonly ownerInitials: string;
  readonly ownerTone: 'amber' | 'blue' | 'violet';
  readonly inProcess: number;
  readonly activeCandidates: number;
  readonly status: 'Active' | 'On hold' | 'Closing';
};

type JobGroup = {
  readonly department: string;
  readonly jobs: readonly Job[];
};

const navigationGroups: ReadonlyArray<{
  readonly label: string;
  readonly items: readonly NavigationItem[];
}> = [
  {
    label: 'Recruit',
    items: [
      { label: 'Jobs', href: '#jobs', count: 6, icon: BriefcaseBusiness, active: true },
      { label: 'Pipeline', href: '#pipeline', count: 9, icon: KanbanSquare },
      { label: 'Review inbox', href: '#review', count: 4, icon: Inbox },
      { label: 'Candidates', href: '#candidates', icon: UsersRound },
    ],
  },
  {
    label: 'Coordinate',
    items: [
      { label: 'Scheduling', href: '#scheduling', count: 4, icon: CalendarDays },
      { label: 'Offers', href: '#offers', count: 1, icon: FileText },
    ],
  },
  {
    label: 'Insights',
    items: [{ label: 'Reports', href: '#reports', icon: BarChart3 }],
  },
];

const jobGroups: readonly JobGroup[] = [
  {
    department: 'Engineering',
    jobs: [
      {
        id: 'ENG-204',
        title: 'Senior Product Engineer',
        location: 'Remote (US)',
        owner: 'Maya Reyes',
        ownerInitials: 'MR',
        ownerTone: 'amber',
        inProcess: 18,
        activeCandidates: 38,
        status: 'Active',
      },
      {
        id: 'ENG-209',
        title: 'Staff Design Engineer',
        location: 'SF / Hybrid',
        owner: 'Tom Iwu',
        ownerInitials: 'TI',
        ownerTone: 'blue',
        inProcess: 8,
        activeCandidates: 21,
        status: 'Active',
      },
      {
        id: 'ENG-198',
        title: 'Engineering Manager, Infra',
        location: 'New York',
        owner: 'Maya Reyes',
        ownerInitials: 'MR',
        ownerTone: 'amber',
        inProcess: 3,
        activeCandidates: 12,
        status: 'On hold',
      },
    ],
  },
  {
    department: 'Design',
    jobs: [
      {
        id: 'DES-114',
        title: 'Product Designer, Growth',
        location: 'Remote (EU)',
        owner: 'Tom Iwu',
        ownerInitials: 'TI',
        ownerTone: 'blue',
        inProcess: 20,
        activeCandidates: 54,
        status: 'Active',
      },
    ],
  },
  {
    department: 'People',
    jobs: [
      {
        id: 'PPL-031',
        title: 'Recruiting Coordinator',
        location: 'Remote (US)',
        owner: 'Maya Reyes',
        ownerInitials: 'MR',
        ownerTone: 'amber',
        inProcess: 19,
        activeCandidates: 67,
        status: 'Active',
      },
    ],
  },
  {
    department: 'Sales',
    jobs: [
      {
        id: 'SAL-076',
        title: 'Head of Sales, EMEA',
        location: 'London',
        owner: 'Sam Altmann',
        ownerInitials: 'SA',
        ownerTone: 'violet',
        inProcess: 6,
        activeCandidates: 9,
        status: 'Closing',
      },
    ],
  },
];

function Brand() {
  return (
    <a className="brand" href="#jobs" aria-label="Talon home">
      <span className="brand-mark" aria-hidden="true">
        <span />
      </span>
      <span>Talon</span>
    </a>
  );
}

function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-topline">
        <Brand />
        <kbd>⌘K</kbd>
      </div>

      <nav aria-label="Primary" className="primary-nav">
        {navigationGroups.map((group) => (
          <section className="nav-group" key={group.label}>
            <h2>{group.label}</h2>
            <ul>
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <li key={item.label}>
                    <a
                      className={item.active ? 'nav-link active' : 'nav-link'}
                      href={item.href}
                      aria-current={item.active ? 'page' : undefined}
                    >
                      <Icon aria-hidden="true" size={18} strokeWidth={1.8} />
                      <span>{item.label}</span>
                      {item.count ? <span className="nav-count">{item.count}</span> : null}
                    </a>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </nav>

      <button className="sidebar-new-job" type="button">
        <Plus aria-hidden="true" size={16} />
        New job
      </button>

      <div className="profile-card">
        <span className="avatar avatar-green" aria-hidden="true">
          MR
        </span>
        <span className="profile-copy">
          <strong>Maya Reyes</strong>
          <small>Recruiting lead</small>
        </span>
        <button type="button" aria-label="Sign out">
          <LogOut aria-hidden="true" size={17} />
        </button>
      </div>
    </aside>
  );
}

function JobRow({ job }: { readonly job: Job }) {
  return (
    <article className="job-row">
      <div className="job-identity">
        <h3>{job.title}</h3>
        <p>
          <span>{job.id}</span>
          <i aria-hidden="true">·</i>
          {job.location}
        </p>
      </div>
      <div className="job-owner">
        <span className={`avatar avatar-${job.ownerTone}`} aria-hidden="true">
          {job.ownerInitials}
        </span>
        <span>{job.owner}</span>
      </div>
      <div className="pipeline-summary">
        <span className="pipeline-bar" aria-hidden="true">
          <i />
          <i />
          <i />
          <i />
        </span>
        <small>{job.inProcess} in process</small>
      </div>
      <div className="active-count">
        <strong>{job.activeCandidates}</strong> active
      </div>
      <span className={`status status-${job.status.toLowerCase().replace(' ', '-')}`}>
        {job.status}
      </span>
    </article>
  );
}

function JobsWorkspace() {
  return (
    <main className="workspace" id="jobs">
      <div className="workspace-heading">
        <div>
          <h1>Jobs</h1>
          <span>6 open</span>
        </div>
        <div className="workspace-actions">
          <button className="filter-button" type="button">
            Status: All
            <ChevronDown aria-hidden="true" size={16} />
          </button>
          <button className="primary-button" type="button">
            <Plus aria-hidden="true" size={17} />
            New job
          </button>
        </div>
      </div>

      <div className="job-groups">
        {jobGroups.map((group) => (
          <section
            className="job-group"
            key={group.department}
            aria-labelledby={`group-${group.department}`}
          >
            <h2 id={`group-${group.department}`}>
              {group.department} <span>· {group.jobs.length} open</span>
            </h2>
            <div className="job-list">
              {group.jobs.map((job) => (
                <JobRow job={job} key={job.id} />
              ))}
            </div>
          </section>
        ))}
      </div>
    </main>
  );
}

export default function App() {
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const focusSearch = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        searchRef.current?.focus();
      }
    };

    window.addEventListener('keydown', focusSearch);
    return () => window.removeEventListener('keydown', focusSearch);
  }, []);

  return (
    <div className="app-shell">
      <Sidebar />
      <div className="app-content">
        <header className="topbar">
          <strong>Jobs</strong>
          <div className="topbar-actions">
            <label className="global-search">
              <Search aria-hidden="true" size={17} />
              <span className="sr-only">Search candidates and jobs</span>
              <input
                ref={searchRef}
                type="search"
                aria-label="Search candidates and jobs"
                placeholder="Search candidates, jobs"
              />
            </label>
            <button
              className="notification-button"
              type="button"
              aria-label="Notifications, one unread"
            >
              <Bell aria-hidden="true" size={18} />
              <span aria-hidden="true" />
            </button>
            <button className="account-button" type="button" aria-label="Open account menu">
              <UserRound aria-hidden="true" size={18} />
            </button>
          </div>
        </header>
        <JobsWorkspace />
      </div>
    </div>
  );
}
