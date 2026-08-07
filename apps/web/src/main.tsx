import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './app/App';
import { AppErrorBoundary } from './app/AppErrorBoundary';
import { createRuntimeAuthGateway } from './features/auth/runtimeAuthGateway';
import { createFixtureCandidateGateway } from './features/candidates/fixtureCandidateGateway';
import { createFixtureJobGateway } from './features/jobs/fixtureJobGateway';
import { createFixtureImportGateway } from './features/imports/fixtureImportGateway';
import './styles.css';

const root = document.getElementById('root');

if (!root) {
  throw new Error('Talon root element was not found');
}

const candidateGateway = createFixtureCandidateGateway();
const jobGateway = createFixtureJobGateway();
const importGateway = createFixtureImportGateway();

async function bootstrap() {
  const fixtureFactory =
    import.meta.env.DEV && import.meta.env.VITE_AUTH_MODE === 'fixture'
      ? (await import('./features/auth/fixtureAuthGateway')).createFixtureAuthGateway
      : undefined;
  const authGateway = createRuntimeAuthGateway(import.meta.env, fetch, fixtureFactory);

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
