import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { createFixtureSearchGateway } from './fixtureSearchGateway';
import { SearchWorkspace } from './SearchWorkspace';

describe('SearchWorkspace', () => {
  it('runs deterministic keyword search without AI interpretation', async () => {
    const user = userEvent.setup();
    const gateway = createFixtureSearchGateway();
    render(<SearchWorkspace searchGateway={gateway} />);

    await user.type(screen.getByLabelText('Keyword candidate search'), 'Java');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Nila Raman')).toBeInTheDocument();
    expect(screen.queryByText('Kai Sen')).not.toBeInTheDocument();
  });

  it('shows editable AI filters and waits for explicit search execution', async () => {
    const user = userEvent.setup();
    render(<SearchWorkspace searchGateway={createFixtureSearchGateway()} />);

    await user.type(
      screen.getByLabelText('Describe candidates to find'),
      'Candidates with expected CTC below 40 LPA',
    );
    await user.click(screen.getByRole('button', { name: 'Build AI filters' }));

    expect(await screen.findByText('Review filters before searching')).toBeInTheDocument();
    expect(
      screen.queryByRole('region', { name: 'Candidate search results' }),
    ).not.toBeInTheDocument();
    const value = screen.getByLabelText('Expected compensation less than value');
    expect(value).toHaveValue('40');

    await user.clear(value);
    await user.type(value, '30');
    await user.click(screen.getByRole('button', { name: 'Search candidates' }));

    expect(await screen.findByText('Kai Sen')).toBeInTheDocument();
    expect(screen.queryByText('Nila Raman')).not.toBeInTheDocument();
    expect(screen.getByText(/annual INR/)).toBeInTheDocument();
  });

  it('makes the AI request flow visible and offers a practical example query', async () => {
    const user = userEvent.setup();
    render(<SearchWorkspace searchGateway={createFixtureSearchGateway()} />);

    expect(screen.getByRole('list', { name: 'AI search steps' })).toHaveTextContent(
      'DescribeReview filtersSearch',
    );
    expect(screen.queryByRole('button', { name: 'Search candidates' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Use senior Java example' }));
    expect(screen.getByLabelText('Describe candidates to find')).toHaveValue(
      'Senior Java candidates in Bengaluru with at least 5 years of experience',
    );
    expect(screen.getByRole('button', { name: 'Build AI filters' })).toBeEnabled();
  });
});
