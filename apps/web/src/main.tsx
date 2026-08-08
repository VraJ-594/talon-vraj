import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './app/App';
import { AppErrorBoundary } from './app/AppErrorBoundary';
import { HttpAuthGateway } from './features/auth/httpAuthGateway';
import { HttpCandidateGateway } from './features/candidates/httpCandidateGateway';
import { HttpJobGateway } from './features/jobs/httpJobGateway';
import { HttpImportGateway } from './features/imports/httpImportGateway';
import { HttpSearchGateway } from './features/search/httpSearchGateway';
import { ApiClient } from './lib/apiClient';
import './styles.css';

const root = document.getElementById('root');

if (!root) {
  throw new Error('Talon root element was not found');
}

async function bootstrap() {
  const apiClient = new ApiClient(import.meta.env.VITE_API_BASE_URL, fetch);
  const authGateway = new HttpAuthGateway(apiClient);
  const candidateGateway = new HttpCandidateGateway(apiClient);
  const jobGateway = new HttpJobGateway(apiClient);
  const importGateway = new HttpImportGateway(apiClient);
  const searchGateway = new HttpSearchGateway(apiClient);

  createRoot(root!).render(
    <StrictMode>
      <AppErrorBoundary>
        <App
          authGateway={authGateway}
          candidateGateway={candidateGateway}
          importGateway={importGateway}
          jobGateway={jobGateway}
          searchGateway={searchGateway}
        />
      </AppErrorBoundary>
    </StrictMode>,
  );
}

void bootstrap();
