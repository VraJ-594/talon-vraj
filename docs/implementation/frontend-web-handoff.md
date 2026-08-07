# Priority Frontend Workstream Handoff

- Status: Checkpoint 1 implementation green; interactive visual comparison pending
- Branch/worktree: `codex/frontend-web` / `.worktrees/frontend-web`
- Scope: Priority frontend workflows defined by `docs/prompts/frontend-parallel-session.md`
- Updated: 2026-08-07

## Current checkpoint

Checkpoint 1 now supplies the sign-in/session boundary and protected application shell needed by the priority import, candidate, export, and search workflows. The implementation is deliberately limited to one fixture Workspace Admin and the Candidates, Import, and Search destinations. Import, candidate, export, and search route content remains placeholder copy for later checkpoints.

The code, test, formatting, lint, build, and production dependency-audit gates are green. The local route is available for manual review, but the in-app browser connection failed before it could capture comparison screenshots, so this checkpoint is not recorded as fully complete.

## What changed

- Replaced the broad Jobs fixture screen with a protected, PDF-directed ATS shell whose primary destinations are Candidates, Import applications, and Search.
- Added a typed application-owned `AuthGateway` for session restoration, login, and logout.
- Added an in-memory fixture gateway for the pre-provisioned `admin@talon.demo` Workspace Admin. It accepts any caller-supplied non-empty temporary password and commits no real/reusable password or token.
- Added accessible email/password sign-in, password visibility, pending submission, generic invalid credentials, locked/rate-limited, unavailable, and expired-session states.
- Added protected-route redirects, safe return to an allowlisted requested priority route after login, restored-session behavior, and awaited logout.
- Added an application error boundary with a recovery route and no raw error disclosure.
- Added focused auth, fixture gateway, shell navigation, and error-boundary tests.
- Added exact `wouter@3.10.0` for small client-side routing. React Router 7.18.2 and 7.11.0 were evaluated first but rejected because the production audit reported high-severity advisories for each tested range; the final production dependency graph reports zero vulnerabilities.

## Why this approach

Authentication is kept behind a frontend-owned port so the UI and behavior can be completed against a non-sensitive fixture today and wired to the application-owned backend identity module later. Session state exists only in component/module memory; neither candidate PII nor credentials/tokens enter local storage.

The route registry is an allowlist rather than an arbitrary post-login redirect. This preserves the requested priority destination while preventing open redirect behavior. Navigation excludes every deferred module so the shell describes the product currently being built rather than presenting dead links.

The sign-in composition follows the supplied Talon direction: off-white form surface, indigo action and story panel, restrained borders, dense recruiting context, and responsive stacking. Existing shell styling was retained and refocused rather than replaced with a generic dashboard template.

## Important paths

- `apps/web/src/app/App.tsx`: session bootstrap, safe protected routing, logout, route registry, sidebar, header, and priority placeholders.
- `apps/web/src/app/AppErrorBoundary.tsx`: route-level safe recovery surface.
- `apps/web/src/features/auth/authGateway.ts`: frontend-owned auth types and error vocabulary.
- `apps/web/src/features/auth/fixtureAuthGateway.ts`: memory-only demo identity adapter.
- `apps/web/src/features/auth/SignInPage.tsx`: accessible sign-in form and mapped recovery messages.
- `apps/web/src/main.tsx`: production composition root that injects the fixture adapter pending backend auth.
- `apps/web/src/styles.css`: responsive sign-in and protected-shell presentation.

## Files and modules affected

- `apps/web/package.json`
- `package-lock.json`
- `apps/web/src/app/App.tsx`
- `apps/web/src/app/App.test.tsx`
- `apps/web/src/app/AppErrorBoundary.tsx`
- `apps/web/src/app/AppErrorBoundary.test.tsx`
- `apps/web/src/features/auth/authGateway.ts`
- `apps/web/src/features/auth/fixtureAuthGateway.ts`
- `apps/web/src/features/auth/fixtureAuthGateway.test.ts`
- `apps/web/src/features/auth/SignInPage.tsx`
- `apps/web/src/features/auth/AuthFlow.test.tsx`
- `apps/web/src/main.tsx`
- `apps/web/src/styles.css`
- `docs/implementation/frontend-web-handoff.md`

## Verification evidence

| Command or check | Observed result |
|---|---|
| Baseline `npm run test:web` | Passed: 1 file, 2 tests. |
| Focused red/green auth cycles | Each required behavior first failed for the intended missing behavior; final `AuthFlow.test.tsx` passed 11/11. The last red case proved login incorrectly returned `/imports` visits to `/candidates`; the allowlisted return-route fix made it green. |
| `npm run format:check:web` | Passed: all matched files use Prettier style. |
| `npm run lint:web` | Passed with zero warnings/errors after removing an unused error-boundary callback. |
| `npm run test:web` | Passed: 4 files, 16 tests, 0 failures. |
| `npm run build:web` | Passed: TypeScript project build and Vite production build; 1,680 modules transformed. |
| `npm audit --omit=dev` | Passed: found 0 vulnerabilities. |
| Local smoke availability | Vite served the isolated worktree successfully at `http://localhost:5174/` (5173 was already occupied). |
| Automated visual comparison | Blocked before inspection: the in-app browser runtime reported a missing kernel-assets path. No screenshots or visual-pass claim were recorded. |

## Integration status

- Authentication integration: typed fixture only. The gateway boundary is ready; no HTTP adapter exists yet.
- Session storage: module/component memory only. No `localStorage` token or identity persistence.
- Backend API integration: none in this checkpoint.
- Demo identity: synthetic Workspace Admin `admin@talon.demo`; no candidate PII and no embedded password.
- Visual review: all nine supplied PDF pages were inspected as design input. The completed sign-in and protected routes still need interactive desktop/narrow-width comparison because browser automation was unavailable.

## Backend contract requests

The approved API surface names these endpoints; request/response fields and status mappings below need backend confirmation before the HTTP adapter is implemented.

### `POST /api/v1/auth/login`

- Request fields: `email: string`, `password: string`.
- Successful response needed by the gateway: `accessToken: string`, `expiresAt: string`, and `session` with `userId: string`, `displayName: string`, `workspaceName: string`, `role: WorkspaceRole`.
- `WorkspaceRole` enum requested: `WORKSPACE_ADMIN | RECRUITER | HIRING_MANAGER | INTERVIEWER`.
- The access token will remain in frontend memory. The refresh token must be set only by the backend as the approved HttpOnly, SameSite=Strict cookie.

### `POST /api/v1/auth/refresh`

- No refresh token in the JSON request or frontend storage; the backend reads the refresh cookie.
- Successful response needed: a replacement `accessToken`, `expiresAt`, and current `session` using the same fields/enums as login.
- Terminal expired/revoked refresh behavior requested as `401` with stable code `SESSION_EXPIRED`.

### `GET /api/v1/session`

- Bearer access token supplied by the centralized future HTTP layer.
- Successful response needed: `userId`, `displayName`, `workspaceName`, and `role`.
- Unauthenticated/expired behavior requested as `401` with stable code `SESSION_EXPIRED`.

### `POST /api/v1/auth/logout`

- No JSON body required.
- Successful response requested as `204`; backend revokes the refresh session and clears its cookie. The frontend awaits completion before discarding its in-memory session.

### Problem response vocabulary

- Stable frontend codes required for this checkpoint: `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `RATE_LIMITED`, `API_UNAVAILABLE`, `SESSION_EXPIRED`.
- `INVALID_CREDENTIALS` must not distinguish unknown email from bad password.
- Problem body requested in the architecture RFC-style shape: `type`, `title`, `status`, `code`, `detail`, `correlationId`, and `fieldErrors`.
- Retryable rate-limit responses should include standards-compatible `Retry-After` information.

## Blockers and exact next step

- Required interactive visual comparison remains blocked by the in-app browser runtime failure. A user can review the running local route now; automated screenshots should be retried when that runtime is repaired.
- Real auth remains externally blocked on confirmed backend request/response fields and endpoints above. The fixture gateway is intentional until then.
- Exact implementation next step after visual sign-off: begin Checkpoint 2 test-first with the import-target job selector, then the upload/template route of the CSV application import wizard.
