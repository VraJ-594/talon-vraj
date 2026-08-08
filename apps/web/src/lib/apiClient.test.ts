import { describe, expect, it } from 'vitest';

import { ApiClient, type ApiFetcher } from './apiClient';

describe('ApiClient', () => {
  it('does not rebind the injected fetcher to the ApiClient instance', async () => {
    const browserLikeFetcher: ApiFetcher = function (this: unknown) {
      if (this !== undefined) {
        throw new TypeError("Failed to execute 'fetch' on 'Window': Illegal invocation");
      }
      return Promise.resolve(new Response(null, { status: 204 }));
    };

    const response = await new ApiClient('', browserLikeFetcher).request('/api/v1/session');

    expect(response.status).toBe(204);
  });
});
