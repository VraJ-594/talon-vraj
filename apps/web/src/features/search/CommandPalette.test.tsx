import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { CommandPalette } from './CommandPalette';
import { createFixtureSearchGateway } from './fixtureSearchGateway';

describe('CommandPalette', () => {
  it('opens with Ctrl+K and searches candidates through the deterministic gateway', async () => {
    const user = userEvent.setup();
    render(<CommandPalette searchGateway={createFixtureSearchGateway()} />);

    await user.keyboard('{Control>}k{/Control}');
    const input = await screen.findByLabelText('Command search');
    await user.type(input, 'Nila');

    expect(await screen.findByText('Nila Raman')).toBeInTheDocument();
  });
});
