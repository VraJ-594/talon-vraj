import { describe, expect, it, vi } from 'vitest';

import { createFixtureAuthGateway } from './fixtureAuthGateway';
import { HttpAuthGateway } from './httpAuthGateway';
import { createRuntimeAuthGateway } from './runtimeAuthGateway';

describe('runtime authentication selection', () => {
  it('uses HTTP authentication by default', () => {
    const gateway = createRuntimeAuthGateway({ DEV: true }, vi.fn<typeof fetch>());

    expect(gateway).toBeInstanceOf(HttpAuthGateway);
  });

  it('allows fixture authentication only through an explicit development opt-in', async () => {
    const gateway = createRuntimeAuthGateway(
      { DEV: true, VITE_AUTH_MODE: 'fixture' },
      vi.fn<typeof fetch>(),
      createFixtureAuthGateway,
    );

    await expect(
      gateway.login({ email: 'admin@talon.demo', password: 'caller-supplied-test-input' }),
    ).resolves.toMatchObject({ workspaceId: 'fixture-talon-workspace' });
  });

  it('ignores fixture mode in production', () => {
    const gateway = createRuntimeAuthGateway(
      { DEV: false, VITE_AUTH_MODE: 'fixture' },
      vi.fn<typeof fetch>(),
    );

    expect(gateway).toBeInstanceOf(HttpAuthGateway);
  });
});
