import { Component, type ReactNode } from 'react';

type AppErrorBoundaryProps = {
  readonly children: ReactNode;
};

type AppErrorBoundaryState = {
  readonly failed: boolean;
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return (
        <main className="workspace app-error" role="alert">
          <p className="eyebrow">Route unavailable</p>
          <h1>Something went wrong</h1>
          <p>This workspace view couldn’t be opened. Return to Candidates and try again.</p>
          <a className="primary-button" href="/candidates">
            Return to Candidates
          </a>
        </main>
      );
    }

    return this.props.children;
  }
}
