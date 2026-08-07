import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './app/App';
import { AppErrorBoundary } from './app/AppErrorBoundary';
import { createFixtureAuthGateway } from './features/auth/fixtureAuthGateway';
import { createFixtureCandidateGateway } from './features/candidates/fixtureCandidateGateway';
import { createFixtureJobGateway } from './features/jobs/fixtureJobGateway';
import { createFixtureImportGateway } from './features/imports/fixtureImportGateway';
import './styles.css';

const root = document.getElementById('root');

if (!root) {
  throw new Error('Talon root element was not found');
}

const authGateway = createFixtureAuthGateway();
const candidateGateway = createFixtureCandidateGateway();
const jobGateway = createFixtureJobGateway();
const importGateway = createFixtureImportGateway();

createRoot(root).render(
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
