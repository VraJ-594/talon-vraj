import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AppErrorBoundary } from './AppErrorBoundary';

function BrokenRoute(): never {
  throw new Error('Candidate payload contained unsafe detail');
}

describe('application error boundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('replaces a failed route with a safe recovery surface', () => {
    render(
      <AppErrorBoundary>
        <BrokenRoute />
      </AppErrorBoundary>,
    );

    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Return to Candidates' })).toHaveAttribute(
      'href',
      '/candidates',
    );
    expect(screen.getByRole('alert')).not.toHaveTextContent(
      'Candidate payload contained unsafe detail',
    );
  });
});
