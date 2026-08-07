# Priority Frontend Workstream Handoff

- Status: Checkpoint 3 complete and user-approved; Checkpoint 4 candidate export is next
- Branch/worktree: `codex/frontend-web` / `.worktrees/frontend-web`
- Scope: Priority frontend workflows defined by `docs/prompts/frontend-parallel-session.md`
- Updated: 2026-08-08

## Current checkpoint

Checkpoints 1 and 2 received user visual sign-off in the local Vite session. Checkpoint 3 now supplies the fixture-backed post-import candidate review route: a dense candidate/application projection, selected application profile, normalized and additional form answers, role-sensitive compensation, resume processing status, and clean-file-only download. The completed import view links directly to candidate review. Export and search route content remains for later checkpoints.

Checkpoint 3 code, test, formatting, lint, build, production dependency-audit, and whitespace gates are green. The user's first `/candidates` and mapping screenshots identified a repeated route heading and the browser-native mapping menu as visual problems. The route hierarchy is now singular and the strict canonical CSV contract replaces manual mapping controls with a compact recognized-column review. The user approved the refreshed frontend through Checkpoint 3 on 2026-08-08. Browser discovery returned no available browser instances, so automated desktop/narrow screenshots could not be captured.

## What changed

- Replaced the broad Jobs fixture screen with a protected, PDF-directed ATS shell whose primary destinations are Candidates, Import applications, and Search.
- Added a typed application-owned `AuthGateway` for session restoration, login, and logout.
- Added an in-memory fixture gateway for the pre-provisioned `admin@talon.demo` Workspace Admin. It accepts any caller-supplied non-empty temporary password and commits no real/reusable password or token.
- Added accessible email/password sign-in, password visibility, pending submission, generic invalid credentials, locked/rate-limited, unavailable, and expired-session states.
- Added protected-route redirects, safe return to an allowlisted requested priority route after login, restored-session behavior, and awaited logout.
- Added an application error boundary with a recovery route and no raw error disclosure.
- Added focused auth, fixture gateway, shell navigation, and error-boundary tests.
- Added exact `wouter@3.10.0` for small client-side routing. React Router 7.18.2 and 7.11.0 were evaluated first but rejected because the production audit reported high-severity advisories for each tested range; the final production dependency graph reports zero vulnerabilities.
- Added typed `JobGateway` and `ImportGateway` boundaries with synthetic fixture adapters outside presentation components.
- Added the seven-step import UI: job selection, canonical template/upload, recognized-column review, validation preview, one-time confirmation, durable progress, and results.
- Enforced the 10 MB/2,000-row fixture limits, UTF-8 BOM handling, quoted CSV headers, required canonical columns, and single-use canonical mappings.
- Added plain-language normalization guidance for LPA/ANNUAL compensation, currencies, experience, notice periods, and dates.
- Added safe valid/invalid/duplicate preview counts and row messages without logging or persisting uploaded candidate values.
- Added URL-based import restoration using only the opaque `importId`; raw CSV values and candidate data do not enter browser storage or the URL.
- Added fetching, quarantined, scan-pending, extracting, clean, and failed resume states, permitted row retry, and error CSV download.
- Replaced physical-line counting with RFC 4180-compatible record parsing, including quoted multiline values, escaped quotes, CRLF/LF, UTF-8 BOM, malformed-quote rejection, and data-row counting after parsing.
- Restricted uploaded CSV headers to the canonical Talon template with case-insensitive matching; unknown, missing required, and case-insensitive duplicate headers are rejected before an import draft is created.
- Replaced editable per-column selects and the retain-unmapped toggle with an automatic, read-only source-to-Talon recognition ledger. The gateway still rejects duplicate canonical mappings defensively.
- Kept every recognition row aligned by placing the required qualifier inside the Talon-field utility label instead of reserving a separate badge column.
- Validated the canonical schema before allocating an import ID or retaining fixture metadata, so rejected uploads have no draft side effects.
- Scoped fixture metadata by opaque import ID and bounded generated result rows to the uploaded row count; only row counts, confirmation keys, and opaque IDs are retained by the fixture.
- Kept rows rejected as invalid or duplicate in their terminal result states and reserved the six resume-processing showcase states for valid rows.
- Expanded the typed import and row status unions to the approved state machines and made result summaries, error CSV, and row retry actions status/capability-aware.
- Added safe operation-specific recovery for restore, upload, validation, confirmation, progress refresh, row retry, and error download failures without displaying adapter/provider error details.
- Preserved a validated opaque `importId` through sign-in redirects, guarded zero-row progress, prevented racing requests with pending states, rendered `ON_HOLD` jobs accurately, and added visible focus rings to custom radio/file controls.
- Added a typed application-owned `CandidateGateway` with list, selected-application, and resume-download operations plus stable forbidden/not-found/unavailable error vocabulary.
- Added synthetic candidate/application fixtures with job, stage, location, experience, company/title, skills, compensation, notice, application date, source, availability, form answers, and resume metadata. No real candidate data is committed.
- Added the dense `/candidates` roster and selected application dossier with responsive horizontal roster overflow and stacked narrow profile layout.
- Restricted compensation values to Admin and Recruiter roles in both list and profile; Hiring Manager receives a permission explanation without sensitive values.
- Exposed resume download only when both backend capability and the `CLEAN` status allow it; pending files show processing guidance instead of an inert download action.
- Added safe list and profile loading, empty, unavailable, forbidden, retry, download-success, and download-failure states without exposing adapter/provider messages.
- Added a completed-import action that moves directly from import results to candidate application review.
- Removed the repeated route heading from page content and promoted the compact top-bar route label to the single semantic page heading; candidate and import content now begin directly with their operational title.
- Changed empty-candidate and completed-import actions to client-side route links so the memory-only fixture session survives navigation.
- Remediated independent review findings by redacting compensation and resume capabilities in restricted gateway projections, role-gating resume download, mapping stale file authorization to a terminal safe state, ignoring out-of-order profile/download responses, moving focus into the opened dossier, exposing expansion relationships only while that dossier exists, restoring focus to the originating application control on close, and making the horizontally scrollable roster keyboard-focusable.

## Why this approach

Authentication is kept behind a frontend-owned port so the UI and behavior can be completed against a non-sensitive fixture today and wired to the application-owned backend identity module later. Session state exists only in component/module memory; neither candidate PII nor credentials/tokens enter local storage.

The route registry is an allowlist rather than an arbitrary post-login redirect. This preserves the requested priority destination while preventing open redirect behavior. Navigation excludes every deferred module so the shell describes the product currently being built rather than presenting dead links.

The sign-in composition follows the supplied Talon direction: off-white form surface, indigo action and story panel, restrained borders, dense recruiting context, and responsive stacking. Existing shell styling was retained and refocused rather than replaced with a generic dashboard template.

The import flow keeps backend persistence and authorization authoritative through gateways. The fixture parses only enough input to demonstrate contract behavior and returns synthetic safe outcomes; the UI never treats fixture validation as a production security boundary. The route uses a dense operational-card composition with an indigo step rail so it remains consistent with the supplied ATS rather than becoming a generic marketing wizard.

Candidate data stays behind a separate application-owned gateway so the approved UI can proceed while backend candidate contracts are still being implemented. Compensation and file actions are capability- and role-aware in the presentation, while the future backend remains authoritative. The fixture uses `.test` contact data and a generated PDF blob only; it does not simulate persistence or weaken the required server authorization.

The candidate composition extends the existing Talon visual language: an operational roster for scanning applications and an indigo-spined dossier for focused review. On smaller viewports the roster remains intentionally dense with horizontal overflow, while profile sections stack to preserve readable detail and touch targets.

## Important paths

- `apps/web/src/app/App.tsx`: session bootstrap, safe protected routing, logout, route registry, sidebar, header, and priority placeholders.
- `apps/web/src/app/AppErrorBoundary.tsx`: route-level safe recovery surface.
- `apps/web/src/features/auth/authGateway.ts`: frontend-owned auth types and error vocabulary.
- `apps/web/src/features/auth/fixtureAuthGateway.ts`: memory-only demo identity adapter.
- `apps/web/src/features/auth/SignInPage.tsx`: accessible sign-in form and mapped recovery messages.
- `apps/web/src/main.tsx`: production composition root that injects the fixture adapter pending backend auth.
- `apps/web/src/styles.css`: responsive sign-in and protected-shell presentation.
- `apps/web/src/features/jobs/jobGateway.ts`: import-target job contract.
- `apps/web/src/features/jobs/fixtureJobGateway.ts`: synthetic open-job adapter.
- `apps/web/src/features/imports/importGateway.ts`: upload, strict header recognition, preview, confirmation, progress, retry, and download contract.
- `apps/web/src/features/imports/fixtureImportGateway.ts`: bounded fixture parser and deterministic lifecycle outcomes.
- `apps/web/src/features/imports/ImportWizard.tsx`: accessible seven-step route UI and opaque-ID restoration.
- `apps/web/src/features/imports/fixtureImportGateway.test.ts`: RFC 4180, canonical case-insensitive recognition, unsupported/missing/duplicate-header, mapping-collision, and per-import isolation regressions.
- `apps/web/src/features/imports/ImportWizard.test.tsx`: safe async recovery, lifecycle-sensitive actions, zero-row progress, pending controls, and job-status regressions.
- `apps/web/src/features/candidates/candidateGateway.ts`: frontend-owned candidate/application, compensation, resume, and error contracts.
- `apps/web/src/features/candidates/fixtureCandidateGateway.ts`: synthetic post-import candidate/application and clean-resume fixture adapter.
- `apps/web/src/features/candidates/CandidateWorkspace.tsx`: list orchestration, permissions, async recovery, selection, and authorized browser download.
- `apps/web/src/features/candidates/CandidateProfilePanel.tsx`: selected profile, application, form-answer, compensation, and resume presentation.
- `apps/web/src/features/candidates/CandidateWorkspace.test.tsx`: candidate projection, permissions, file gating, and loading/error/forbidden/retry regressions.
- `apps/web/src/features/candidates/fixtureCandidateGateway.test.ts`: restricted-projection redaction and resume authorization regression.

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
- `apps/web/src/features/jobs/jobGateway.ts`
- `apps/web/src/features/jobs/fixtureJobGateway.ts`
- `apps/web/src/features/imports/importGateway.ts`
- `apps/web/src/features/imports/fixtureImportGateway.ts`
- `apps/web/src/features/imports/fixtureImportGateway.test.ts`
- `apps/web/src/features/imports/ImportWizard.tsx`
- `apps/web/src/features/imports/ImportWizard.test.tsx`
- `apps/web/src/features/candidates/candidateGateway.ts`
- `apps/web/src/features/candidates/fixtureCandidateGateway.ts`
- `apps/web/src/features/candidates/CandidateWorkspace.tsx`
- `apps/web/src/features/candidates/CandidateProfilePanel.tsx`
- `apps/web/src/features/candidates/CandidateWorkspace.test.tsx`
- `apps/web/src/features/candidates/fixtureCandidateGateway.test.ts`

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
| Focused Checkpoint 2 red/green cycles | Observed intended failures for missing job selection, upload, mapping, lifecycle, and refresh restoration; final `App.test.tsx` passed 7/7. |
| Final `npm run format:check:web` after Checkpoint 2 sign-off | Passed: all matched files use Prettier style. |
| Final `npm run lint:web` after Checkpoint 2 sign-off | Passed with zero warnings/errors. |
| Final `npm run test:web` after Checkpoint 2 sign-off | Passed: 4 files, 21 tests, 0 failures. |
| Final `npm run build:web` after Checkpoint 2 sign-off | Passed: TypeScript and Vite production build; 1,683 modules transformed. The restricted command sandbox first returned `EPERM` on `tsconfig.app.tsbuildinfo`; the approved unrestricted rerun passed without a code change, confirming an execution-sandbox write restriction. |
| Final `npm audit --omit=dev` after Checkpoint 2 sign-off | Passed: found 0 vulnerabilities. |
| Checkpoint 2 interactive visual review | User approved the locally served `/imports` workflow on 2026-08-07. Automated screenshots remain unavailable because the in-app browser runtime cannot initialize. |
| Review-remediation gateway red cycle | Five focused tests failed for the intended missing behaviors: quoted multiline row count, alias collision, duplicate source header, duplicate canonical mapping, and per-import isolation. |
| Review-remediation gateway green cycle | Passed: `fixtureImportGateway.test.ts`, 5/5. |
| Review-remediation UI/auth red cycle | Six wizard recovery/lifecycle tests and the import-ID re-authentication test failed for the intended missing behavior; existing import journey also exposed its three-row/six-result mismatch before the fixture was corrected to six rows. |
| Review-remediation focused green cycle | Passed: 4 files, 30 tests, 0 failures. |
| Follow-up consistency red/green cycle | The focused test first failed because invalid/duplicate preview rows became clean results; after the fix, the gateway and complete import-journey suites passed 13/13. |
| Final `npm run format:check:web` after review remediation | Passed: all matched files use Prettier style. |
| Final `npm run lint:web` after review remediation | Passed with zero warnings/errors. |
| Final `npm run test:web` after review remediation | Passed: 6 files, 34 tests, 0 failures. |
| Final `npm run build:web` after review remediation | Passed: TypeScript and Vite production build; 1,684 modules transformed. |
| Final `npm audit --omit=dev` after review remediation | Passed: found 0 vulnerabilities. |
| Final `git diff --check` after review remediation | Passed with no whitespace errors. |
| Checkpoint 3 candidate-list red/green cycle | The focused test first failed because no candidate workspace module existed; the typed fixture projection then passed with all required list fields. |
| Checkpoint 3 profile red/green cycle | Three tests first failed because candidate buttons had no detail behavior; selected profile, additional answers, clean-only resume action, and compensation restriction then passed. |
| Checkpoint 3 state and download red/green cycles | Empty, unavailable, forbidden, list retry, profile loading/error/retry, and active resume-download tests each failed for the intended missing behavior before implementation; final candidate suite passed 10/10. |
| Completed-import handoff red/green cycle | The clean result test first failed because no candidate-review link existed; adding the `/candidates` action made the import suite green. |
| Final `npm run format:check:web` for Checkpoint 3 | Passed: all matched files use Prettier style. |
| Final `npm run lint:web` for Checkpoint 3 | Passed with zero warnings/errors. |
| Independent Checkpoint 3 code review | No Critical findings. Five Important findings covered frontend data redaction, resume role/capability enforcement, async selection races, authorization-specific download recovery, and keyboard/focus behavior; all were remediated before handoff. |
| Review-remediation red cycle | Six candidate tests failed for the intended missing behaviors: focusable roster, dossier focus/expanded state, terminal resume authorization handling, latest-selection wins, role-gated download, and non-zero loading label. |
| Review-remediation focused green cycle | Passed: candidate gateway and workspace suites, 13/13 tests. |
| Final accessibility red/green cycle | The focused assertions first failed because loading applications reported an expanded dossier and closing discarded keyboard focus; after lifecycle/focus restoration changes, `CandidateWorkspace.test.tsx` passed 12/12. |
| Independent Checkpoint 3 follow-up review | Passed: no remaining Critical or Important findings. The reviewer verified collapsed loading/error controls, valid expansion relationships only while the panel exists, and focus restoration to the exact originating candidate control. Verdict: ready to merge. |
| Strict-header gateway red/green cycle | Two focused tests first failed because unknown columns and missing required columns still produced import drafts; after strict case-insensitive canonical recognition, the gateway suite passed 8/8. |
| Import UI refinement red/green cycle | Four focused shell/import checks first failed for the duplicate content heading, editable mapping UI, generic unsupported-header recovery, and the prior journey fixture; the recognized-column ledger and safe strict-schema messages then passed. A separate required-header UI assertion failed with the generic upload message before its safe specific mapping was added. |
| Required-label alignment red/green cycle | The focused UI assertion first failed because `Required` sat outside the Talon-field card; moving it into the card's utility label made the assertion pass and removed the shifting badge column. |
| Rejected-schema state regression | The focused assertion failed because an unsupported upload consumed `fixture-import-001`; moving canonical validation before ID allocation/storage made it pass. |
| Import/candidate SPA navigation red/green cycle | Two integration tests first failed because native anchors left the active route unchanged in the SPA and would reload the memory-only session in a browser; client-side links made both pass. |
| Independent UI-refinement follow-up review | Passed: no Critical or Important findings. The reviewer verified validation-before-allocation, rejected-schema ID preservation, both client-side navigation paths, and their integration coverage. Verdict: approved. |
| Final `npm run test:web` for Checkpoint 3 | Passed: 8 files, 54 tests, 0 failures. |
| Final `npm run build:web` for Checkpoint 3 | Passed: TypeScript and Vite production build; 1,688 modules transformed. |
| Final `npm audit --omit=dev` for Checkpoint 3 | Passed: found 0 vulnerabilities. |
| Final `git diff --check` for Checkpoint 3 | Passed with no whitespace errors. |
| Checkpoint 3 interactive visual review | User approved the refreshed frontend through `/candidates` and the refined canonical mapping view on 2026-08-08. Automated screenshots remain unavailable because browser discovery returned no available browser instances. |
| Refreshed local route smoke | `http://localhost:5173/candidates` and `/imports` both returned HTTP 200 from the user's running isolated-worktree Vite server. |

## Integration status

- Authentication integration: typed fixture only. The gateway boundary is ready; no HTTP adapter exists yet.
- Session storage: module/component memory only. No `localStorage` token or identity persistence.
- Backend API integration: none in this checkpoint.
- Demo identity: synthetic Workspace Admin `admin@talon.demo`; no candidate PII and no embedded password.
- Visual review: all nine supplied PDF pages were inspected as design input. The user approved the completed sign-in, protected shell, `/imports`, and candidate-review workflow in the local Vite session; browser automation remains unavailable for screenshot evidence.
- Job/import integration: typed fixtures only. No real CSV, Drive, queue, storage, or candidate API is called.
- Fixture persistence: safe runtime metadata is keyed by opaque import ID, and the documented fixture route restores a deterministic synthetic status. Real durability must come from backend import records; candidate values are not stored in browser storage.
- Checkpoint 1 visual review: user-approved locally on 2026-08-07.
- Checkpoint 2 visual review: user-approved locally on 2026-08-07.
- Candidate integration: typed fixture only. The application-owned gateway is ready for a future authenticated HTTP adapter; no candidate API is called yet.
- Candidate file handling: the fixture returns an in-memory synthetic PDF blob. The production adapter must use the authorized five-minute resume-download response and must not expose storage keys or Drive URLs.
- Checkpoint 3 visual review: user-approved locally on 2026-08-08.

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

### `GET /api/v1/jobs`

- Response fields needed per item: `id`, `title`, `department`, `location`, `status`.
- Status enum requested for the selector: `OPEN | ON_HOLD`; only backend-authorized import targets should be returned.

### `POST /api/v1/imports`

- Multipart request needed: `jobId` plus CSV file; maximum 10 MB and 2,000 data rows remain server-authoritative.
- Response fields needed: `id`, `jobId`, `fileName`, `rowCount`, `sourceColumns`, `status` (`UPLOADED`).
- Stable safe errors requested: `FILE_TOO_LARGE`, `TOO_MANY_ROWS`, `INVALID_CSV`, `JOB_NOT_IMPORTABLE`, `UNSUPPORTED_SOURCE_COLUMN`, and `MISSING_REQUIRED_COLUMN`.
- Mapping/operation codes additionally requested: `DUPLICATE_SOURCE_COLUMN`, `DUPLICATE_MAPPING`, `MISSING_REQUIRED_MAPPING`, `IMPORT_NOT_FOUND`, `IMPORT_ALREADY_CONFIRMED`, `ROW_NOT_RETRYABLE`, `ERROR_CSV_UNAVAILABLE`, and `API_UNAVAILABLE`.

### Mapping and validation

- The current frontend no longer presents user-authored mappings or `retainUnmappedAsAdditionalAnswers`. Upload must accept only the canonical headers below, compare names case-insensitively, and reject unknown or missing required columns before creating the draft.
- `PUT /api/v1/imports/{importId}/mapping`, if retained by the backend, receives only the server-recognized one-to-one mapping returned from upload; the frontend does not allow a recruiter to alter it.
- Canonical field enum requested: `first_name`, `last_name`, `email`, `resume_drive_url`, `phone`, `location`, `total_experience_years`, `current_company`, `current_title`, `skills`, `current_ctc`, `expected_ctc`, `ctc_unit`, `ctc_currency`, `notice_period_days`, `availability_date`, `source`, `application_date`.
- `POST /api/v1/imports/{importId}/validate`; `GET /api/v1/imports/{importId}/preview` response fields needed: `validCount`, `invalidCount`, `duplicateCount`, and safe row issues with `rowNumber`, `code`, `message`.

### Confirmation, progress, retry, and errors

- `POST /api/v1/imports/{importId}/confirm`: idempotency key must be accepted through the backend-standard header; response needs `importId`, `status`, `processedCount`, `totalCount`.
- `GET /api/v1/imports/{importId}` and `/rows`: import status enum from the approved state machine plus rows with `rowNumber`, `status`, `retryable`, and safe `message`.
- Resume/row statuses required by the UI include `FETCHING_RESUME`, `RESUME_QUARANTINED`, `SCAN_PENDING`, `EXTRACTING_TEXT`, `COMPLETED`, and terminal failure codes such as `RESUME_FETCH_FAILED`.
- `POST /api/v1/imports/{importId}/rows/{rowNumber}/retry` should reject non-retryable states with a stable problem code.
- `GET /api/v1/imports/{importId}/errors.csv` should return an authorized attachment without source Drive URLs or unsafe provider details.

### Candidate and selected application review

- `GET /api/v1/candidates` needs the selected application projection used by the roster: `applicationId`, `candidateId`, `candidateName`, `jobTitle`, `stage`, `location`, `totalExperienceMonths`, `currentCompany`, `currentTitle`, `skills`, current/expected compensation with currency and minor units, `noticePeriodDays`, `applicationDate`, and `resumeStatus`.
- `GET /api/v1/candidates/{candidateId}` may provide the basic profile, but `GET /api/v1/applications/{applicationId}` must provide the selected application detail: masked/authorized contact fields, source, availability date, normalized fields, allowlisted additional answers, resume filename/status, and a server-authoritative resume-download capability.
- `GET /api/v1/applications/{applicationId}/resume-download` is requested only for authorized Admin/Recruiter users and only after the file is `CLEAN`; the response should provide the approved five-minute exact-object private download without exposing bucket keys or the source Drive URL.
- Candidate/resume statuses required by this UI: `FETCHING_RESUME`, `RESUME_QUARANTINED`, `SCAN_PENDING`, `EXTRACTING_TEXT`, `CLEAN`, `FAILED`, and `UNSAFE_FILE`.
- Stable candidate problem codes requested: `FORBIDDEN`, `CANDIDATE_NOT_FOUND`, `APPLICATION_NOT_FOUND`, `RESUME_NOT_CLEAN`, `RESUME_DOWNLOAD_FORBIDDEN`, and `API_UNAVAILABLE`. Provider details must remain out of the response shown to users.
- Compensation fields must be omitted or rejected server-side for roles other than Workspace Admin and Recruiter; frontend masking is not the authorization boundary.

## Blockers and exact next step

- Automated screenshot comparison remains unavailable because browser discovery returned no available browser instances; user-driven visual review is the available evidence.
- Real auth remains externally blocked on confirmed backend request/response fields and endpoints above. The fixture gateway is intentional until then.
- Exact next step: commit the verified Checkpoint 3 state, then implement candidate CSV export states through the typed fixture boundary. Stop before adding any deterministic or natural-language search behavior.
