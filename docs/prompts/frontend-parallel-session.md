# Prompt: Parallel Talon ATS Frontend Implementation Session

Copy the prompt below into the separate frontend session. Start that session only in its own Git worktree/branch so it cannot overwrite the backend session.

---

You are implementing the complete production-quality frontend for the Talon ATS repository.

## Repository and isolation

- Main repository: `E:\Project\LiveBuildTask`
- Create or use a separate worktree such as `E:\Project\LiveBuildTask-web`.
- Create branch `codex/frontend-web` from the latest `codex/phase-1-baseline` commit.
- Never work on the backend session's branch and never edit the same physical worktree concurrently.
- Before changing code, read the root `AGENTS.md` completely and obey its implementation-record requirement.
- Maintain `docs/implementation/frontend-web-handoff.md` throughout the session. At every checkpoint record what was done, why, how, verification evidence, unresolved API needs, blockers, and next steps.

If the worktree has not been created yet, first inspect repository status and use the repository's worktree workflow. Do not discard or overwrite existing changes.

## Required context review

Before implementation, read all of the following:

- `Talon ATS.pdf` — inspect and render every relevant screen; do not rely only on extracted text.
- `initial_requirements.txt`
- `docs/plans/talon-ats-implementation-plan.md`
- `docs/architecture/hld.md`
- `docs/architecture/lld.md`
- `docs/architecture/api-design.md`
- `docs/architecture/database-design.md`
- `docs/architecture/security-threat-model.md`
- `docs/architecture/deployment-and-testing.md`
- `docs/implementation/phase-01-executable-baseline.md`
- Existing frontend code and tests under `apps/web`.

Use the PDF and frontend-design skills for visual decisions, the TDD skill for feature work, systematic debugging for failures, and verification-before-completion before any success claim. Use the approved browser skill for rendered UI inspection and local interaction testing when available.

## Ownership boundaries

This session owns:

- `apps/web/**`
- Frontend-only tests and frontend test fixtures
- `docs/implementation/frontend-web-handoff.md`
- Frontend-specific API-needs notes inside that handoff

This session does not own and must not modify without explicit coordination:

- `apps/api/**`
- `compose.yaml`
- Backend Flyway migrations or Java domain code
- Terraform/AWS resources
- Approved architecture decisions
- Backend-generated OpenAPI artifacts

The backend session owns the real API and database. Do not invent a second backend, Supabase-direct browser data access, production credentials, or frontend-only authorization rules.

## Current baseline

- React 19, strict TypeScript, Vite, Vitest, Testing Library, ESLint, Prettier, and Lucide are configured.
- A PDF-directed Jobs/dashboard shell exists in `apps/web/src/app/App.tsx` with smoke tests and responsive CSS.
- Current job rows are isolated design fixtures, not production persistence.
- Docker/local PostgreSQL verification may still be pending. You may build against typed fixture adapters while it is pending, but you must not claim real end-to-end integration until the backend contract and services are available.
- Authentication, routing, shared components, feature modules, generated API client, Playwright, and the remaining product screens still need implementation.

## Goal

Build a cohesive, accessible ATS web application covering:

1. Sign-in/sign-up, OAuth entry, 2FA enrollment/challenge, invitation, and workspace onboarding screens.
2. Sectioned recruiting sidebar, responsive application shell, notifications, account menu, and Cmd/Ctrl+K command search.
3. Department-grouped Jobs list and multi-step New Job wizard.
4. Candidate pipeline Kanban with stage movement, conversion/time metrics, filtering, and responsive overflow.
5. Candidate profile with activity, email, interview, scorecard, and file areas.
6. Review inbox with resume triage, signal presentation, bulk selection, advance, and reject actions.
7. Offer builder, ordered approval-chain editor, approval status, and letter preview.
8. Scheduling grid and calendar-connection states.
9. CSV import workflow, validation/error summary, progress, and bulk actions.
10. Reports for hiring KPIs, funnel conversion, sources, and interview-volume trends.
11. Loading, empty, error, forbidden, offline/retry, and destructive-confirmation states appropriate to each workflow.

Follow the supplied design faithfully while creating reusable primitives and feature boundaries. Preserve a clear path from fixtures to the generated API client; do not scatter mock data through presentation components.

## Architecture to implement

Organize by product feature, with a small shared layer. A practical target is:

```text
apps/web/src/
  app/                 # providers, router, layouts, route guards
  components/          # genuinely shared UI primitives
  features/
    auth/
    jobs/
    candidates/
    pipeline/
    review/
    offers/
    scheduling/
    imports/
    reports/
    search/
    notifications/
  lib/                 # HTTP client, errors, dates, validation helpers
  test/                # shared test setup/builders
```

For every feature:

- Keep route/page composition separate from reusable view components.
- Put remote calls behind a typed feature gateway/repository interface.
- Keep fixtures in explicit test/demo adapters that implement the same interface.
- Centralize query keys, error translation, and request cancellation when server-state tooling is introduced.
- Treat backend authorization failures as authoritative; route guards improve UX but are not security controls.
- Use composition over large conditional components.

Do not add a library until the current feature needs it. If adding routing, server-state, forms/schema validation, accessible primitives, charts, drag-and-drop, dates, or Playwright, select one well-maintained library per concern, justify it in the handoff, pin through the lockfile, and run an audit.

## API coordination contract

- Consume `/api/v1/**` only through a centralized HTTP layer.
- Prefer a backend-published OpenAPI document and generated TypeScript client once available.
- Until it is available, define narrow frontend ports and fixture adapters; do not guess response fields across many components.
- Record every needed endpoint, request field, response field, enum, pagination/filter rule, idempotency need, and error state under `## Backend contract requests` in `docs/implementation/frontend-web-handoff.md`.
- Do not silently change an agreed enum or workflow. Surface contract conflicts to the user/backend session.
- Keep tenant/workspace identity derived from the authenticated session, never from an unrestricted arbitrary browser parameter.

## Implementation order

Work in small test-driven checkpoints:

1. Refactor the existing shell into stable layout/navigation primitives without changing its verified appearance.
2. Add router, route registry, error boundary, authenticated/unauthenticated layouts, and fixture feature gateways.
3. Implement design tokens and shared accessible primitives used by the PDF screens.
4. Implement auth/onboarding screens and route states against the fixture auth gateway.
5. Complete Jobs and New Job wizard.
6. Implement candidate pipeline and candidate profile.
7. Implement Review inbox and bulk/import flows.
8. Implement scheduling grid and calendar connection states.
9. Implement offers and approval/preview states.
10. Implement search, notifications, and reports.
11. Replace fixture gateways with the generated API client feature by feature as backend contracts land.
12. Add Playwright journeys only after stable routes exist; prioritize sign-in/onboarding/job/candidate/pipeline as the first vertical flow.

Commit each independently green checkpoint with a narrow message. Avoid one giant frontend commit.

## Test-first and quality gates

Before each behavior, write a focused failing test and confirm it fails for the expected reason. Then implement the minimum cohesive behavior and refactor while green.

Required coverage includes:

- Route and permission-state behavior
- Keyboard navigation, focus management, dialogs, menus, and Cmd/Ctrl+K
- Form validation and multi-step preservation
- Loading/empty/error/retry states
- Kanban keyboard-accessible movement plus pointer interaction
- Bulk-selection safety and destructive confirmations
- Offer approval/preview states
- Calendar time-zone presentation states
- Responsive shell and overflow behavior
- Critical accessibility checks
- Playwright happy path and one important denial/error path per stable vertical flow

At every checkpoint run from the repository root:

```powershell
npm run format:check:web
npm run lint:web
npm run test:web
npm run build:web
npm audit --omit=dev
```

Also run the relevant Playwright tests once configured. Do not claim visual fidelity without inspecting rendered pages against the corresponding PDF screens.

## Security and production constraints

- Never commit secrets, tokens, real candidate data, or OAuth credentials.
- Do not store access tokens in `localStorage` when an HttpOnly-cookie/session approach is available from the backend architecture.
- Do not render untrusted resume/email/offer HTML without an explicit sanitization boundary.
- Enforce file type/size feedback in the UI while treating backend validation as authoritative.
- Avoid logging PII or API bodies containing candidate data.
- Provide clear session-expired, forbidden, rate-limited, and upload-failure behavior.
- Maintain WCAG-oriented semantics, labels, contrast, focus visibility, reduced motion, and keyboard paths.

## Coordination and completion

- The backend session is progressing in parallel. Keep frontend commits isolated and report commit hashes plus backend contract requests to the user.
- Rebase/merge only at an agreed checkpoint; do not modify the backend worktree to resolve conflicts.
- A screen is not complete merely because it renders: its tests, responsive behavior, accessibility behavior, loading/error states, and implementation handoff must be current.
- If an essential workflow is ambiguous after reviewing the PDF and architecture docs, ask one concise, implementation-impacting question; otherwise make a reversible UI assumption and record it.
- End each checkpoint with: changed files, passing commands, screenshots/visual review status, mock-versus-real integration status, API requests, risks, and next recommended checkpoint.

Begin by inspecting the baseline, creating `docs/implementation/frontend-web-handoff.md`, and proposing the first small shell/router checkpoint. Do not edit backend or infrastructure files.

---
