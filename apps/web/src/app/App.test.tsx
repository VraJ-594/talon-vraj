import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import App from './App';

describe('Talon application shell', () => {
  it('gives recruiters the primary Jobs workspace from the supplied design', () => {
    render(<App />);

    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Jobs', level: 1 })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'New job' })).toHaveLength(2);
    expect(screen.getByRole('searchbox', { name: 'Search candidates and jobs' })).toBeVisible();
  });

  it('moves focus to global search when the command shortcut is pressed', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.keyboard('{Meta>}k{/Meta}');

    expect(screen.getByRole('searchbox', { name: 'Search candidates and jobs' })).toHaveFocus();
  });
});
