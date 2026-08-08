export type ApiFetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export class ApiClient {
  private accessToken: string | null = null;
  private readonly baseUrl: string;

  constructor(
    baseUrl = '',
    private readonly fetcher: ApiFetcher = fetch,
  ) {
    this.baseUrl = baseUrl.trim().replace(/\/$/, '');
  }

  hasAccessToken() {
    return this.accessToken !== null;
  }

  setAccessToken(accessToken: string) {
    this.accessToken = accessToken;
  }

  clearAccessToken() {
    this.accessToken = null;
  }

  request(path: string, init: RequestInit = {}, authenticated = false) {
    const headers = new Headers(init.headers);
    if (authenticated && this.accessToken) {
      headers.set('Authorization', `Bearer ${this.accessToken}`);
    }

    const fetcher = this.fetcher;
    return fetcher(`${this.baseUrl}${path}`, {
      ...init,
      credentials: 'include',
      headers,
    });
  }
}
