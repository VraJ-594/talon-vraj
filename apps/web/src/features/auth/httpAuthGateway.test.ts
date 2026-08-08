import { beforeEach, describe, expect, it, vi } from 'vitest';

import { HttpAuthGateway } from './httpAuthGateway';
import { ApiClient } from '../../lib/apiClient';

const LOGIN_RESPONSE = {
  userId: 'user-admin-001',
  workspaceId: 'workspace-talon-001',
  workspaceName: 'Talon Demo',
  role: 'WORKSPACE_ADMIN',
  displayName: 'Maya Reyes',
  accessToken: 'header.payload.signature',
  accessTokenExpiresAt: '2026-08-09T01:15:00Z',
} as const;

const SESSION_RESPONSE = {
  userId: LOGIN_RESPONSE.userId,
  workspaceId: LOGIN_RESPONSE.workspaceId,
  workspaceName: LOGIN_RESPONSE.workspaceName,
  role: LOGIN_RESPONSE.role,
  displayName: LOGIN_RESPONSE.displayName,
} as const;

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('HttpAuthGateway', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('maps the flat login response and accepts the refresh cookie without exposing tokens', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(LOGIN_RESPONSE));
    const gateway = new HttpAuthGateway(new ApiClient('http://localhost:8080', fetcher));

    const session = await gateway.login({
      email: 'admin@example.test',
      password: 'caller-supplied-password',
    });

    expect(session).toEqual(SESSION_RESPONSE);
    expect(fetcher).toHaveBeenCalledOnce();
    const [url, request] = fetcher.mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/v1/auth/login');
    expect(request).toMatchObject({ method: 'POST', credentials: 'include' });
    expect(new Headers(request?.headers).get('Content-Type')).toBe('application/json');
    expect(new Headers(request?.headers).has('Authorization')).toBe(false);
    expect(request?.body).toBe(
      JSON.stringify({
        email: 'admin@example.test',
        password: 'caller-supplied-password',
      }),
    );
    expect(window.localStorage).toHaveLength(0);
    expect(window.sessionStorage).toHaveLength(1);
  });

  it('hydrates the active tab synchronously after a page refresh', async () => {
    const loginFetcher = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(LOGIN_RESPONSE));
    await new HttpAuthGateway(new ApiClient('', loginFetcher)).login({
      email: 'admin@example.test',
      password: 'temporary-input',
    });
    const refreshedApiClient = new ApiClient('', vi.fn<typeof fetch>());
    const refreshedGateway = new HttpAuthGateway(refreshedApiClient);

    expect(refreshedGateway.cachedSession()).toEqual(SESSION_RESPONSE);
    expect(refreshedApiClient.hasAccessToken()).toBe(true);
    expect(window.localStorage).toHaveLength(0);
  });

  it('adds the in-memory bearer token centrally for session restoration and clears it on logout', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(LOGIN_RESPONSE))
      .mockResolvedValueOnce(jsonResponse(SESSION_RESPONSE))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ code: 'SESSION_EXPIRED' }, 401));
    const gateway = new HttpAuthGateway(new ApiClient('', fetcher));

    await gateway.login({ email: 'admin@example.test', password: 'temporary-input' });
    await expect(gateway.restoreSession()).resolves.toEqual(SESSION_RESPONSE);

    const [url, request] = fetcher.mock.calls[1];
    expect(url).toBe('/api/v1/session');
    expect(request).toMatchObject({ method: 'GET', credentials: 'include' });
    expect(new Headers(request?.headers).get('Authorization')).toBe(
      `Bearer ${LOGIN_RESPONSE.accessToken}`,
    );

    await gateway.logout();

    await expect(gateway.restoreSession()).resolves.toBeNull();
    expect(fetcher).toHaveBeenCalledTimes(4);
    expect(fetcher.mock.calls[2][0]).toBe('/api/v1/auth/logout');
    expect(fetcher.mock.calls[2][1]).toMatchObject({ method: 'POST', credentials: 'include' });
    expect(fetcher.mock.calls[3][0]).toBe('/api/v1/auth/refresh');
    expect(window.localStorage).toHaveLength(0);
    expect(window.sessionStorage).toHaveLength(0);
  });

  it('restores a refreshed browser session without using browser storage', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(LOGIN_RESPONSE));
    const apiClient = new ApiClient('', fetcher);
    const gateway = new HttpAuthGateway(apiClient);

    await expect(gateway.restoreSession()).resolves.toEqual(SESSION_RESPONSE);

    expect(apiClient.hasAccessToken()).toBe(true);
    expect(fetcher).toHaveBeenCalledOnce();
    const [url, request] = fetcher.mock.calls[0];
    expect(url).toBe('/api/v1/auth/refresh');
    expect(request).toMatchObject({ method: 'POST', credentials: 'include' });
    expect(new Headers(request?.headers).has('Authorization')).toBe(false);
    expect(window.localStorage).toHaveLength(0);
    expect(window.sessionStorage).toHaveLength(1);
  });

  it('treats a rejected refresh cookie as signed out', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'SESSION_EXPIRED' }, 401));
    const apiClient = new ApiClient('', fetcher);

    await expect(new HttpAuthGateway(apiClient).restoreSession()).resolves.toBeNull();

    expect(apiClient.hasAccessToken()).toBe(false);
  });

  it.each([
    ['INVALID_CREDENTIALS', 401],
    ['ACCOUNT_LOCKED', 423],
    ['RATE_LIMITED', 429],
    ['API_UNAVAILABLE', 503],
    ['SESSION_EXPIRED', 401],
  ] as const)('maps the backend %s problem without leaking its detail', async (code, status) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          type: 'https://talon.example/problems/authentication',
          title: 'Authentication failed',
          status,
          code,
          detail: 'Unsafe backend detail',
          correlationId: 'correlation-001',
        },
        status,
      ),
    );
    const gateway = new HttpAuthGateway(new ApiClient('', fetcher));

    const error = await gateway
      .login({ email: 'admin@example.test', password: 'incorrect-input' })
      .catch((problem: unknown) => problem);

    expect(error).toMatchObject({ code });
    expect(error).toBeInstanceOf(Error);
    expect((error as Error).message).not.toContain('Unsafe backend detail');
  });

  it.each([
    { ...LOGIN_RESPONSE, workspaceId: '' },
    { ...LOGIN_RESPONSE, role: 'HIRING_MANAGER' },
    { ...LOGIN_RESPONSE, accessTokenExpiresAt: 'not-a-timestamp' },
    { ...LOGIN_RESPONSE, accessTokenExpiresAt: 'August 8, 2026' },
    { ...LOGIN_RESPONSE, accessTokenExpiresAt: '2026-08-08T10:00:00' },
    { ...LOGIN_RESPONSE, accessTokenExpiresAt: '2026-02-31T10:00:00Z' },
  ])('treats a malformed successful login response as unavailable', async (body) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(body));
    const apiClient = new ApiClient('', fetcher);
    const gateway = new HttpAuthGateway(apiClient);

    await expect(
      gateway.login({ email: 'admin@example.test', password: 'temporary-input' }),
    ).rejects.toMatchObject({ code: 'API_UNAVAILABLE' });
    expect(apiClient.hasAccessToken()).toBe(false);
    expect(fetcher).toHaveBeenCalledOnce();
  });

  it('maps a network failure to API_UNAVAILABLE', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));
    const gateway = new HttpAuthGateway(new ApiClient('', fetcher));

    await expect(
      gateway.login({ email: 'admin@example.test', password: 'temporary-input' }),
    ).rejects.toMatchObject({ code: 'API_UNAVAILABLE' });
  });
});
