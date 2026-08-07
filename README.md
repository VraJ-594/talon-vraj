# Talon ATS

Production-oriented Applicant Tracking System built as a React/Vite SPA and a Java 21 Spring Boot modular monolith. Architecture and delivery decisions are documented before implementation.

## Repository map

```text
apps/web/                 React/Vite frontend
apps/api/                 Spring Boot API and worker artifact
docs/architecture/        approved architecture and ADRs
docs/plans/               implementation plan
docs/implementation/      resumable implementation handoffs
infra/                    Terraform in a later phase
```

## Prerequisites

- Node.js 22 or newer and npm 10 or newer.
- JDK 21 and Maven 3.9. The current machine has JDK 21 at `C:\Program Files\Java\jdk-21.0.11`.
- Docker Desktop with Compose. Docker data may remain on E:; named volumes use Docker's configured data location.

If a new PowerShell session does not see Java, set it for that session:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Local services

Copy `.env.example` to `.env` if local values need changing, then start PostgreSQL and Mailpit:

```powershell
docker compose up -d postgres mail
docker compose ps
```

Mailpit is available at `http://localhost:8025`. PostgreSQL listens on `localhost:5432`.

## Frontend

```powershell
npm install
npm run dev:web
npm run test:web
npm run lint:web
npm run build:web
```

The Vite application runs at `http://localhost:5173`.

## Backend

The existing Maven cache on this machine is on E: to avoid consuming C: space:

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml test
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml spring-boot:run
```

The API runs at `http://localhost:8080`; its stable smoke endpoint is `GET /api/v1/health` and Actuator health is at `GET /actuator/health`.

## Container builds

Run from the repository root so both Dockerfiles receive the monorepo build context:

```powershell
docker build -f apps/web/Dockerfile -t talon-web:local .
docker build -f apps/api/Dockerfile -t talon-api:local .
```

## Implementation status

Read [the active Phase 1 handoff](./docs/implementation/phase-01-executable-baseline.md) before continuing implementation.
