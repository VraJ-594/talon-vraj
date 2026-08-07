# Phase 1: Executable Baseline

- Status: Awaiting Docker verification
- Branch: `codex/phase-1-baseline`
- Started: 2026-08-07
- Plan task: Task 1 in `docs/plans/talon-ats-implementation-plan.md`

## Scope

Establish a repeatable monorepo with an executable React/Vite frontend, Java 21 Spring Boot API, PostgreSQL/Flyway configuration, local PostgreSQL and mail services, baseline automated tests, and developer commands. This phase creates the technical runway; it does not implement authentication or real job persistence.

## Implemented so far

### Repository foundation

- Initialized Git on the dedicated `codex/phase-1-baseline` branch because the supplied workspace was not previously a repository.
- Added root ignore, editor, line-ending, npm workspace, and tool-version conventions.
- Kept temporary PDF renders, dependency folders, builds, secrets, Terraform state, and Playwright output outside version control.

### Frontend shell

- Added a strict TypeScript React 19/Vite 7 workspace under `apps/web`.
- Implemented the Jobs shell from page 2 of `Talon ATS.pdf`: fixed recruiting navigation, global search, notification/account controls, department-grouped jobs, owners, pipeline bars, statuses, and New job actions.
- Used semantic navigation/headings and real buttons. Cmd/Ctrl+K focuses the global search field.
- Kept the current job rows as isolated design fixtures; Task 3 will replace them with generated API client data without changing the shell boundary.
- Added responsive overflow behavior, visible focus, and reduced-motion handling.

### Backend executable baseline

- Added the Maven project metadata for Java 21, Spring Boot, Spring Modulith, Actuator, Web, Validation, JPA, Flyway, and PostgreSQL.
- Added tests for Spring context startup, `GET /api/v1/health`, and Modulith boundary verification before backend application code.
- Implemented the minimal Spring Boot entrypoint and health contract after confirming the tests failed for the missing production types.
- Added the Maven Wrapper pinned to Maven 3.9.11, Java/Maven runtime enforcement, deterministic test-agent configuration, and Java formatting checks.
- Configured environment-driven PostgreSQL, Flyway, SMTP, CORS, Actuator, and structured health settings in `application.yml`.
- Added the first Flyway migration for the PostgreSQL extensions required by later search and identifier work.
- Maven dependency cache is directed to the existing `E:\maven-repo` to avoid filling C:.

### Local services and container packaging

- Added Docker Compose services for PostgreSQL 17 and Mailpit 1.30.0 with explicit health checks and a named database volume.
- Pinned Mailpit to a current patched release instead of `latest`; its bundled `/mailpit readyz` probe calls the documented readiness endpoint.
- Added multi-stage, non-root production Dockerfiles for the React/nginx frontend and Spring Boot API.
- Added a checked-in environment template and local/container commands without committing credentials.

### Dependency and formatting hygiene

- Removed currently unused React Router and TanStack Query packages. They can be introduced when routing and server-state boundaries are implemented instead of carrying avoidable dependencies now.
- Added Prettier and Spotless checks so AI-assisted changes share deterministic formatting conventions.
- Remediated the npm advisory report by removing the unused vulnerable dependency path; the subsequent install reported zero vulnerabilities.

### Parallel implementation boundary

- Added `docs/prompts/frontend-parallel-session.md` as the copy-ready brief for a separate frontend session.
- Assigned that session only `apps/web/**` and its frontend handoff, while this session retains API, migrations, Compose, and backend contract ownership.
- Required an isolated `codex/frontend-web` worktree/branch from the verified baseline so parallel work cannot overwrite the backend worktree.
- Defined fixture gateways as temporary typed adapters and reserved the real `/api/v1` and OpenAPI contracts for backend ownership, reducing integration churn.

## Why these choices

- A single npm workspace and one Spring Boot artifact match the approved modular-monolith plan and minimize cross-project tooling.
- The frontend follows the supplied product design rather than a generic dashboard template.
- Standard JDBC/JPA/Flyway dependencies preserve portability between local PostgreSQL and Supabase-hosted PostgreSQL.
- Tests establish consumer-visible contracts before implementation and protect later refactors.

## Verification evidence

| Check | Result |
|---|---|
| Frontend test red phase | Failed because `App.tsx` did not exist, as intended |
| Frontend tests after implementation | 2 tests passed |
| Frontend lint | Passed with zero warnings |
| Frontend Prettier check | Passed; all matched source files use the configured style |
| Frontend production build | Passed; Vite emitted the production bundle |
| Backend test red phase | Failed compilation because `TalonAtsApplication` and `HealthController` did not exist, as intended |
| Backend tests after implementation | 3 tests passed: context, health contract, and Modulith verification |
| Backend Maven `verify` | Passed; Java/Maven enforcement, tests, executable JAR packaging, and Spotless all succeeded |
| Maven Wrapper generation | Passed; wrapper is pinned to Maven 3.9.11 |
| npm dependency install after pruning | Passed; npm reported zero vulnerabilities |
| Production npm audit | Passed; `npm audit --omit=dev` reported zero vulnerabilities |

The first backend packaging attempt passed all tests but could not write Maven tracking metadata to the shared `E:\maven-repo` under the managed filesystem sandbox. Re-running the same command with the approved E-drive cache permission completed successfully; no source change was required.

## Environment observations

- Node `24.13.1` and npm `11.8.0` are available.
- Oracle JDK `21.0.11` is installed at `C:\Program Files\Java\jdk-21.0.11`.
- Maven `3.9.11` is available; commands currently set `JAVA_HOME` explicitly.
- Docker data is intentionally on E:. The Docker CLI/Desktop executable is not yet discoverable from the current shell, so Compose validation, image builds, and service health checks are pending.
- The approved in-app browser surface has no installed browser in this environment. Automated DOM/accessibility-facing tests are available, but the final rendered PDF comparison remains pending until that browser surface or another approved visual-review route is available.

## Next steps

1. Run fresh frontend formatting, lint, tests, build, and dependency-audit checks plus backend `verify`.
2. Locate/start Docker Desktop without moving its E: data directory and validate Compose, image builds, PostgreSQL/Flyway, API health, and Mailpit readiness.
3. Complete a rendered comparison with the supplied PDF when an approved browser is available.
4. Commit the phase only when required executable checks pass, retaining any environment-only limitation as an explicit handoff item.
