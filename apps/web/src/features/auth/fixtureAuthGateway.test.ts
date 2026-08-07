import { describe, expect, it } from 'vitest';

import { createFixtureAuthGateway, FIXTURE_ADMIN_EMAIL } from './fixtureAuthGateway';

describe('fixture authentication gateway', () => {
  it('keeps the synthetic Admin session in memory until logout', async () => {
    const gateway = createFixtureAuthGateway();

    expect(await gateway.restoreSession()).toBeNull();

    const session = await gateway.login({
      email: `  ${FIXTURE_ADMIN_EMAIL.toUpperCase()}  `,
      password: 'caller-supplied-test-input',
    });

    expect(session).toEqual({
      userId: 'fixture-workspace-admin',
      displayName: 'Maya Reyes',
      workspaceName: 'Talon Demo',
      role: 'WORKSPACE_ADMIN',
    });
    expect(await gateway.restoreSession()).toEqual(session);
    expect(window.localStorage).toHaveLength(0);

    await gateway.logout();

    expect(await gateway.restoreSession()).toBeNull();
  });

  it('rejects credentials for any identity outside the synthetic Admin account', async () => {
    const gateway = createFixtureAuthGateway();

    await expect(
      gateway.login({ email: 'someone-else@example.test', password: 'caller-supplied-test-input' }),
    ).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
  });
});
