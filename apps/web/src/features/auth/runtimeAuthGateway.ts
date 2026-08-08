import type { AuthGateway } from './authGateway';
import { HttpAuthGateway } from './httpAuthGateway';
import type { ApiClient } from '../../lib/apiClient';

type RuntimeAuthEnvironment = {
  readonly DEV: boolean;
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_AUTH_MODE?: 'http' | 'fixture';
};

export function createRuntimeAuthGateway(
  environment: RuntimeAuthEnvironment,
  apiClient: ApiClient,
  fixtureFactory?: () => AuthGateway,
): AuthGateway {
  if (environment.DEV && environment.VITE_AUTH_MODE === 'fixture') {
    if (!fixtureFactory) throw new Error('Fixture authentication is unavailable');
    return fixtureFactory();
  }
  return new HttpAuthGateway(apiClient);
}
