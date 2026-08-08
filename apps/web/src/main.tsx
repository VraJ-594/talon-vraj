import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './app/App';
import { AppErrorBoundary } from './app/AppErrorBoundary';
import { createRuntimeAuthGateway } from './features/auth/runtimeAuthGateway';
import { createFixtureCandidateGateway } from './features/candidates/fixtureCandidateGateway';
import { createFixtureJobGateway } from './features/jobs/fixtureJobGateway';
import { HttpJobGateway } from './features/jobs/httpJobGateway';
import { createFixtureImportGateway } from './features/imports/fixtureImportGateway';
import { HttpImportGateway } from './features/imports/httpImportGateway';
import { ApiClient } from './lib/apiClient';
import './styles.css';

const root = document.getElementById('root');

if (!root) {
  throw new Error('Talon root element was not found');
}

async function bootstrap() {
  const fixtureMode = import.meta.env.DEV && import.meta.env.VITE_AUTH_MODE === 'fixture';
  const apiClient = new ApiClient(import.meta.env.VITE_API_BASE_URL, fetch);
  const fixtureFactory = fixtureMode
    ? (await import('./features/auth/fixtureAuthGateway')).createFixtureAuthGateway
    : undefined;
  const authGateway = createRuntimeAuthGateway(import.meta.env, apiClient, fixtureFactory);
  const candidateGateway = createFixtureCandidateGateway();
  const jobGateway = fixtureMode ? createFixtureJobGateway() : new HttpJobGateway(apiClient);
  const importGateway = fixtureMode
    ? createFixtureImportGateway()
    : new HttpImportGateway(apiClient);

  createRoot(root!).render(
    <StrictMode>
      <AppErrorBoundary>
        <App
          authGateway={authGateway}
          candidateGateway={candidateGateway}
          importGateway={importGateway}
          jobGateway={jobGateway}
        />
      </AppErrorBoundary>
    </StrictMode>,
  );
}

void bootstrap();
