# Priority Import, Export, and Search Implementation Handoff

## Scope and current status

Status: in progress.

Local CSV and Drive-fallback checkpoint (2026-08-08): the user-supplied test CSV at an external
local path was inspected without printing candidate values. It contains three rows, all 18
canonical headers, all required columns, and three Google Drive URLs. The authenticated API accepted
the CSV once the diagnostic client supplied a validated job UUID, and validation returned three
valid, zero invalid, and zero duplicate rows. Processing ended `COMPLETED_WITH_ERRORS` only because
all three Drive downloads returned `RESUME_FETCH_FAILED`; tenant-scoped command search independently
verified all three rows as candidates with application IDs. The worker now retains the original
`resume_drive_url` in application form answers before attempting private transfer, so a failed
Drive/S3 path does not discard the recruiter-visible source reference. The focused fallback test and
the full network-free backend suite pass (123/123). AWS bootstrap is paused until authenticated AWS
CLI access, account ID, selected region, and the Terraform executable path are available.

Search checkpoint (2026-08-08): the dual-mode candidate search implementation is present in the
working tree. Deterministic Cmd/Ctrl+K candidate/job lookup and explicit candidate filtering use a
tenant-scoped PostgreSQL repository. Natural-language search uses an application-owned interpreter
port with a Groq strict-JSON-schema adapter; model output is treated as untrusted and must pass the
same allowlisted DSL validator before execution. The dedicated search UI shows editable/removable
filters and never silently executes an interpretation. Flyway V7 is applied to the configured
Supabase PostgreSQL 17.6 project and its focused schema smoke passes. Local backend and frontend
search gates pass. The latest local restart replaced the stale JAR and Vite listeners: API health is
`UP` on port 8080 and the web root returns HTTP 200 on port 5173. The final authenticated
login/Cmd+K/Groq/query browser smoke remains a user manual gate.

Candidate roster checkpoint (2026-08-08): the live Candidates route now uses authenticated Talon
HTTP APIs rather than a runtime fixture selector. It renders one row per application, pages newest
first with an opaque seek cursor, opens a tenant-scoped application profile, and permits resume
delivery only when the stored candidate file is `CLEAN`. Test fixtures remain only as injected test
helpers. The candidate service/controller and Supabase query/seed integration gates pass, as do the
full backend and frontend gates. The idempotent SQL Editor script creates 36 synthetic `.test`
candidates/applications and four active jobs in an operator-reviewed workspace; it deliberately
creates no fake `candidate_file` rows. Live execution of that seed and a browser check of the roster,
detail drawer, pagination, Cmd/Ctrl+K, deterministic search, and Groq filter flow have not been
observed in this checkpoint and remain the manual gate. Per user instruction, these implementation
changes are left uncommitted while CI/CD and AWS work proceeds in parallel.

Candidate profile follow-up (2026-08-08): application rows now navigate to the protected,
deep-linkable `/candidates/applications/{applicationId}` route instead of expanding a detail panel
below the entire roster. Imported applications with nullable availability or compensation and a
blank source render safe “not provided” states; they no longer fail during date formatting. The
page owns loading, retry, not-found, forbidden, back-navigation, and clean-only resume-download
states, while direct profile URLs survive session restoration. Focused candidate/App tests pass
29/29; full frontend lint, all 88 tests, and the production build pass. API health is `UP` on port
8080 and the profile SPA route returns HTTP 200 on port 5173. Authenticated browser clicking on a
real imported row remains the user manual gate. No commit or push was made.

Current checkpoint (2026-08-08): the authenticated workflow is implemented from strict CSV upload
through durable confirmation and same-deployment asynchronous processing. Valid rows create or
match a workspace candidate and one application for the selected job; replay returns the existing
records. The required public Drive PDF then flows through the five-starts-per-second source adapter
into a private quarantine object. Flyway V5/V6 add durable processing state, row results, and
tenant-isolated candidate-file metadata. Both migrations are applied to the configured Supabase
PostgreSQL 17.6 project. The network-free backend gate passes 113 tests; frontend lint, all 78 tests,
and its production build pass. A live authenticated smoke using an anonymously downloadable Drive
PDF validated the row, created the candidate/application, and copied the signature-verified PDF into
private quarantine storage. The provider-selectable private S3 adapter, five-minute exact
clean-resume presigning, and a minimal Terraform storage/IAM foundation are implemented and locally
verified. Malware
scanning/clean promotion, real AWS apply/smoke, candidate HTTP review, export, and search remain
incomplete.

The approved active slice is minimal application-owned authentication followed by candidate CSV
import/private export and dual-mode candidate search. The application-owned login/session HTTP
contract, PostgreSQL schema, Supabase-hosted Flyway deployment, tenant-safe JDBC persistence,
production JWT bean wiring, and idempotent environment-only demo Admin provisioner are implemented
and verified. Independent local JWT/HMAC keys, a random demo password, and its BCrypt hash were
generated without printing values; the live Supabase-backed login and bearer-session flow passed.
Browser-session refresh rotation and server logout are implemented. For the approved demo behavior,
the validated access JWT and session projection are also stored in tab-scoped `sessionStorage`, so a
page refresh renders the protected route immediately while closing the browser session removes that
client state. The opaque HttpOnly cookie remains an atomically rotated PostgreSQL fallback. Login,
refresh, and session expose the workspace name needed by the frontend gateway. Tenant-scoped job
APIs and the candidate/application create-or-match facade
are implemented and locally verified. Flyway V3 and real Supabase persistence tests for those new
paths are present but are not marked verified because the session pooler became unreachable during
the latest run.

## What changed

- Added application-owned candidate roster/detail/resume query contracts with Admin/Recruiter
  authorization, bounded `1..100` pages, opaque cursors, and safe not-found/not-clean errors.
- Added a parameterized JDBC query adapter over application, candidate, job, and candidate-file
  tables. Every read transaction derives the workspace from the verified JWT, sets the PostgreSQL
  tenant context, and executes under forced RLS as `talon_app`.
- Added authenticated `GET /api/v1/applications`, `GET /api/v1/applications/{id}`, and
  `GET /api/v1/applications/{id}/resume-download`. Clean S3 objects use a five-minute redirect;
  application-owned local storage streams the clean object without exposing a private key.
- Added `HttpCandidateGateway`, cumulative Load more paging, real application profile loading, and
  explicit no-resume/error states. Production/development runtime wiring now uses HTTP gateways only;
  fixture gateways remain available to tests through dependency injection.
- Replaced the roster-bottom detail panel with a dedicated protected application profile route.
  Candidate names are now client-side links, direct URLs are preserved through session restoration,
  and invalid opaque identifiers are rejected before a detail request is made.
- Aligned the browser detail contract with nullable PostgreSQL application fields. Missing
  availability, compensation, or source values render safe fallback text rather than crashing an
  imported candidate profile.
- Clarified Search as a visible Describe → Review filters → Search flow, with separate Build AI
  filters and Search candidates actions, examples, reset behavior, and responsive/focus styling.
- Added `scripts/supabase/seed-candidate-search-demo.sql`, a guarded and idempotent operator script
  producing 36 synthetic applications across four jobs without credentials, Drive URLs, real PII,
  or fabricated clean-resume metadata.
- Added a versioned candidate-search DSL covering allowlisted profile, application, date,
  experience, notice-period, and currency-bound compensation predicates. Validation enforces
  field/operator compatibility, typed values, role checks, limits, opaque cursors, and stable
  tie-breaker sorting.
- Added forward Flyway V7 with a generated candidate `tsvector` plus GIN and tenant-first B-tree
  indexes for deterministic PostgreSQL search.
- Added a tenant-scoped, parameterized PostgreSQL search store. Dynamic SQL is limited to
  enum-controlled column/operator/sort fragments; user/model values remain parameters.
- Added authenticated `/api/v1/search/command`, `/api/v1/candidate-search/interpret`, and
  `/api/v1/candidate-search/query` APIs. Cmd/Ctrl+K never calls an AI provider.
- Added the Groq interpreter behind `NaturalLanguageSearchInterpreter`, using a three-second Java
  HTTP timeout and strict JSON Schema with `openai/gpt-oss-20b` by default. The request contains only
  the recruiter sentence, locale/timezone/date context, fixed instructions, and DSL schema—never
  candidate rows, resumes, tenant IDs, or SQL.
- Added per-user natural-language interpretation limiting at 10 requests per minute and stable
  disabled/unavailable/quota/invalid errors; explicit search remains available when Groq fails.
- Replaced the frontend search placeholder with deterministic keyword search, a review-before-run
  AI filter builder, editable/removable filter chips, safe failure states, and candidate result
  projections. Added a global Cmd/Ctrl+K candidate/job palette.
- Added an environment-gated, idempotent synthetic search-data provisioner for five `.test`
  candidates and two jobs in the demo workspace. It is enabled only in ignored `.env.supabase` for
  manual testing and contains no credentials or real PII.
- Added ignored runtime configuration for Groq and search demo seeding. A live-looking key was found
  in tracked `.env.example`, immediately replaced with a placeholder, and not used; that exposed key
  must be revoked/rotated. The replacement key belongs only in ignored `.env.supabase`.

- Added a strict, case-insensitive Talon template policy for the 18 canonical CSV columns. Unknown,
  missing-required, and case-insensitive duplicate source columns fail with stable codes before a
  draft can be persisted; the generated template contains one synthetic row.
- Preserved source-column order in the read-only recognition ledger and `ColumnMapping` rather than
  relying on `Map.copyOf` iteration order.
- Added idempotent `ObjectStorage.delete(PrivateObjectKey)` and local root-confined deletion so an
  upload workflow can compensate if database creation fails after a private object write.
- Added forward Flyway V4 with tenant-owned `candidate_import` and `candidate_import_row` tables,
  creator membership/job foreign keys, bounded counts/JSON shapes, forced RLS, indexes, and
  `talon_app` grants.
- Added `ImportDraftRepository` and `JdbcImportDraftRepository`. Every operation uses a Spring
  transaction, transaction-local workspace context, and `SET LOCAL ROLE talon_app`; preview
  replacement locks the draft and replaces normalized valid rows plus safe issue rows atomically.
- Exposed the files module's application-owned provider contracts as the named Modulith interface
  `files::contracts`; imports do not depend on the local adapter, S3 SDK, or Supabase-specific API.
- Added `ImportDraftService` orchestration for ADMIN/RECRUITER authorization, active-job checks,
  bounded uploads, private-object/database compensation, exact mapping validation, and repeatable
  preview reads.
- Added authenticated `/api/v1/imports` template, multipart upload, validate, and preview endpoints
  with stable problem responses and no private object keys in browser-visible payloads.
- Added fail-closed runtime provider selection. The files module owns local-adapter construction
  through `ObjectStorageFactory`, keeping imports dependent only on its exposed contract.
- Integrated the `codex/frontend-web` worktree into `codex/backend-api`. Normal runtime uses real
  job/import HTTP adapters, downloads the server template, uploads multipart CSV, renders the
  read-only detected mapping and safe validation preview, and restores previews by opaque ID.
- Kept fixture-only confirmation/progress screens unavailable in HTTP mode until their backend
  worker contract exists, preventing a recruiter from entering a known dead end.
- Added idempotent import confirmation and durable PostgreSQL progress/row state. A bounded local
  dispatcher invokes the same application worker that a later SQS adapter can invoke without
  changing import behavior.
- Added an exposed candidate import contract and JDBC adapter. Normalized rows create or match a
  candidate by workspace/email and create at most one application per candidate/job.
- Added rate-limited resume transfer orchestration. Successful anonymous Drive PDFs are stored under
  opaque private quarantine keys and linked to tenant-isolated `candidate_file` metadata; source
  URLs, object keys, and provider details never enter browser responses.
- Added the observed Google Drive download behavior to the adapter: allowlisted Drive hosts may use
  `application/octet-stream` or `application/binary`, but those bodies are accepted only when the
  first bytes are the PDF signature. HTML/auth pages, unrelated media types, invalid signatures,
  private-network targets, and files larger than 10 MB remain fail-closed.
- Enabled real frontend confirm/progress polling in normal HTTP mode now that the corresponding
  backend endpoints exist.
- Added the AWS SDK v2 S3 adapter behind `ObjectStorageFactory`. Uploads are staged through a bounded
  temporary file, hashed, encrypted at rest, and written under opaque application-owned keys;
  promotion copies only the exact quarantine key to its matching clean key before deleting source.
- Added on-demand clean-resume presigning capped at five minutes with inline PDF disposition.
  Quarantine/import/export keys cannot use this method, and generated URLs are never persisted.
- Added provider selection through `TALON_FILES_PROVIDER`, `TALON_FILES_S3_BUCKET`, and
  `TALON_FILES_S3_REGION`. AWS clients use the default credential chain so ECS task-role credentials
  replace local operator credentials without application changes.
- Added the initial Terraform development storage foundation: globally unique `-vraj` bucket name,
  all S3 public-access blocks, bucket-owner-enforced ownership, encryption, versioning, TLS-only
  policy, quarantine/export lifecycles, 30-day noncurrent clean-resume retention, optional signed-GET
  CORS, and one least-scope policy matching the current combined API/in-process-worker runtime.
  Terraform state, credentials, saved plans, and apply are not committed.
- Recorded the owner naming convention in `docs/architecture/aws-terraform-design.md`: explicitly
  nameable AWS resources end in `-vraj`, global uniqueness precedes that suffix, and supported
  resources receive `Owner=Vraj` plus `Project=TalonATS` tags.

- Replaced the broad active roadmap with gated foundation, import/export, search, AWS, and E2E
  phases.
- Rewrote the HLD, LLD, API, database, security, deployment/testing, and Terraform documents around
  the approved priority behavior and updated the browser index/editable diagrams.
- Added ADRs for priority scope/authentication, private file transfer, and validated dual-mode
  search.
- Updated the provider-port ADR for Drive, object storage, scanner, queue, and Grok boundaries.
- Prepared a reduced parallel-frontend prompt covering only the approved workflow.
- Removed the Cognito subject from `AppUser`, bootstrap command, service, and persistence port.
- Added canonical display email, normalized lookup email, BCrypt hash storage, and rejection of
  plaintext/non-BCrypt provisioning values.
- Added authentication application ports/service, generic authentication failure, access-token
  claims, and refresh-session domain data.
- Login always performs password verification (using a dummy BCrypt hash for unknown emails),
  accepts only active users/memberships, and atomically hands the hashed refresh session plus login
  timestamp to persistence.
- Access and refresh defaults are supplied explicitly to the service; the verified contract uses
  15 minutes and seven days. A token provider that returns the raw refresh token as its stored hash
  is rejected.
- Added Spring Security/resource-server dependencies, a BCrypt verifier, HS256 JWT issuer, opaque
  256-bit refresh-token generation, keyed HMAC refresh hashing, login controller, secure refresh
  cookie, protected session endpoint, and generic RFC 9457 invalid-credential response.
- JWTs contain only user/workspace/role/display claims with issued/expiry times and require an
  absolute URI issuer. Unknown/invalid bearer access is rejected with 401.
- Security activation is explicit through `talon.security.enabled=true`. When disabled, the default
  filter chain exposes health only and denies all application routes; this avoids a fake in-memory
  production auth path.
- Added Flyway V2 workspace/account/membership/refresh-session/job/candidate/application tables,
  constraints, indexes, `talon_app`, forced RLS policies, and transaction-local workspace context.
- Kept normal Maven verification network-, Docker-, and Supabase-free. Explicit
  `postgres-integration` and `supabase-smoke` profiles own real PostgreSQL checks.
- Applied and validated Flyway V1/V2 against the configured Supabase PostgreSQL 17.6 session
  pooler. The application remains JDBC/Flyway portable and uses no Supabase Auth, Storage, Data
  API, or Edge Function.
- Added `JdbcIdentityAccountStore` for default-workspace account lookup under RLS and atomic hashed
  refresh-session/last-login persistence. The same adapter atomically provisions workspace, user,
  and Admin membership through the existing bootstrap port.
- Added runtime properties and beans for BCrypt, HS256 JWT encoding/decoding, issuer/audience
  validation, HMAC refresh hashing, authentication service, and JDBC persistence. Authentication
  stays disabled by default; invalid or sub-256-bit keys fail startup.
- Added an explicitly enabled, idempotent demo Admin provisioner. Email and BCrypt hash come only
  from runtime configuration; neither is committed or logged.
- Generated local demo-only secrets into ignored `.env.supabase` and `.env.demo-credentials` files.
  Two independent random 256-bit keys protect access-token signing and refresh-token hashing; the
  random plaintext demo password exists only in the ignored credentials file and only its BCrypt
  cost-12 hash enters application configuration/database storage.
- AWS deployment will inject the same environment variable contract from Secrets Manager through
  the Terraform-managed ECS task definition. Secrets are not baked into the image, committed in
  `.tfvars`, or written into application Terraform resources.
- Added `workspaceName` to the authenticated account/result, signed JWT claim, login response, and
  session response so the frontend can map the real API without a hard-coded workspace label.
- Moved `WorkspaceRole` into the explicitly exposed `identity::workspace-access` contract. Jobs and
  candidates can depend on the authorization vocabulary without reaching into identity internals.
- Added the jobs domain/application/controller/JDBC layers. Authenticated Admins and Recruiters can
  create active jobs and list tenant-scoped import targets; the API maps internal `ACTIVE` to the
  frontend `OPEN` status without changing the database vocabulary.
- Added forward Flyway V3 for required job location. Existing rows receive `Unspecified` during
  migration; new writes must provide a non-blank location.
- Added typed candidate/application input models, ISO currency plus annual minor units, application
  service, and JDBC port adapter. Candidate email matching and one-application-per-job insertion use
  PostgreSQL uniqueness with `ON CONFLICT`, making replay return the original IDs instead of
  duplicating records.
- Added real-database integration tests for job isolation and candidate/application replay. These
  compile and are selected by `supabase-smoke`; their latest execution is blocked by remote pooler
  reachability and therefore is not counted as passing evidence.
- Added the provider-free candidate import domain: canonical Google Form fields, required/unique
  column mapping, bounded import aggregate, row state vocabulary, row validation results, and typed
  normalization for email, dates, experience, notice period, and annual compensation.
- Compensation normalization uses ISO currency minor units. `LPA` is accepted only with INR, and
  `40 LPA` becomes `400,000,000` paise without floating-point arithmetic.
- Added an application-owned streaming CSV parser port and an Apache Commons CSV adapter for
  RFC 4180 input. Parsing is bounded to 10 MB and 2,000 data rows, recognizes an optional UTF-8
  BOM, rejects malformed or duplicate headers with stable error codes, and never fetches resumes
  during preview.
- Added header inspection with conservative canonical-field suggestions, explicit column mapping,
  optional retention of unmapped Google Form answers, and row-numbered partial preview results.
  Valid, invalid, and within-file duplicate rows are separated without aborting an otherwise
  usable file.
- Kept the parser behind `CsvApplicationParser`, so provider/library details do not leak into the
  import domain. The durable upload/preview HTTP endpoints remain in Task 5 because their job ID,
  mapping, preview, and retry behavior must be backed by PostgreSQL rather than an in-memory store.
- Added the `ExternalFileSource`/`BoundedObjectSink` application boundary and an anonymous
  `PublicGoogleDriveSource` adapter. It recognizes only the approved `drive.google.com/file/d`,
  `open?id`, and `uc?id` share forms and converts them to the narrow anonymous download request;
  it sends no OAuth credentials, cookies, confirmation token, or abuse-warning bypass flag.
- Every initial request and redirect requires HTTPS, an allowlisted Google host, the default TLS
  port, and public DNS results. Loopback, link-local, site-local, multicast, carrier-grade NAT,
  documentation, benchmark, and other reserved destinations are rejected before HTTP access.
- Drive responses are streamed through the caller-owned sink with a hard byte-counting limit.
  Both `application/pdf` and `%PDF-` are required; HTML permission/confirmation pages, oversized
  files, spoofed media, and unsupported statuses become stable failure codes. Retryable failures
  carry explicit classification, and HTTP 429 preserves a valid `Retry-After` delay.
- Added a generic, named `platform::rate-limiting` interface and configurable in-process
  `LeakyBucket`. The approved defaults are represented by construction inputs: five starts per
  second, burst capacity five, and five in flight. Fetch execution also has response and total
  operation deadlines; interrupted/timed-out downloads release their concurrency permit.
- Added the application-owned `ObjectStorage` contract, opaque `PrivateObjectKey` factories, and a
  local filesystem adapter. Resume keys contain workspace/file/version UUIDs only; import/export
  keys contain workspace/import-or-export UUIDs only. Arbitrary paths, traversal, names, emails,
  and source URLs
  cannot enter the key type.
- Local writes stream through a hard caller-supplied limit into a same-directory temporary object,
  calculate SHA-256, and atomically publish the completed object. Limit/I/O failures remove the
  temporary file. Promotion only accepts a quarantine resume and its exact derived clean key.
- Added application-owned scanner and resume-text-extractor ports plus fail-closed processing
  orchestration. Only a `CLEAN` verdict triggers promotion and extraction; infected files and
  scanner failures remain in quarantine. Extraction output is structurally capped at 50 processed
  pages and 500,000 characters, with an explicit truncation marker for the concrete adapter.
- Added the backend clean-download policy boundary. Cross-workspace access resolves as not found,
  while quarantine or non-clean objects are not downloadable. Actual role/resource lookup and
  five-minute local/S3 delivery grants remain controller/persistence adapter work.

## Why this approach

Finishing a secure vertical workflow provides stronger evidence than partially implementing every
ATS area. Ports preserve provider independence; durable PostgreSQL jobs preserve restart safety;
private object storage protects candidate data; and a validated DSL prevents LLM-produced queries
from becoming database instructions. The roster stays application-shaped because stage, job,
compensation, notice, and resume state belong to an application; the same person may therefore
appear once for each job. A real HTTP-only runtime makes deployment behavior visible early, while
fixtures remain useful at test boundaries without masking unavailable APIs in production.

## Important paths

- Authentication: existing account → BCrypt verification → access JWT + rotating hashed refresh
  session → workspace/role principal.
- Import: select job → upload/map/preview CSV → confirm durable job → rate-limited Drive fetch →
  quarantine/scan/PDF extraction → private store → candidate/application result.
- Export: validated candidate criteria → durable export job → private CSV → authorized five-minute
  download URL; artifact lifecycle is seven days.
- Search: Cmd+K/explicit filters → typed criteria → PostgreSQL. Natural language → Groq restricted
  DSL → backend validation → the same typed criteria and repository.
- Candidate roster: verified JWT → workspace/role query service → transaction-local RLS JDBC query
  → newest-first application page → `HttpCandidateGateway` → application pipeline. Selecting a row
  navigates to `/candidates/applications/{applicationId}` → authenticated detail request → dedicated
  profile page. A resume request succeeds only for an exact `CLEAN` object.
- Cmd/Ctrl+K: global keyboard shortcut → debounced text → authenticated
  `GET /api/v1/search/command` → bounded tenant-scoped candidate/job lookup → selecting a result
  carries the query into the Search route. No interpreter or Groq request exists on this path.
- AI search: recruiter sentence plus locale/timezone → authenticated
  `POST /api/v1/candidate-search/interpret` → per-user limiter → application-owned Groq adapter with
  fixed instructions and strict JSON schema → backend DSL validation → editable filter chips. The
  browser does not execute those filters until Search candidates sends the validated criteria to
  `POST /api/v1/candidate-search/query`, which revalidates and runs parameterized RLS SQL. Provider
  errors leave deterministic keyword/filter search available.
- Jobs: verified JWT workspace/role → application service → transaction-local tenant context →
  import-target query or active-job insert.
- Candidate/application foundation: validated typed CSV row → active job check → normalized email
  candidate insert/match → application insert/match → stable replay result.

## Files and modules affected

- `docs/plans/talon-ats-implementation-plan.md`
- `docs/architecture/adr/0005-external-provider-ports.md`
- `docs/architecture/{hld,lld,api-design,database-design,security-threat-model}.md`
- `docs/architecture/{aws-terraform-design,deployment-and-testing}.md`
- `docs/architecture/architecture.html` and priority Eraser sources
- `docs/architecture/adr/0007-priority-slice-and-application-auth.md`
- `docs/architecture/adr/0008-private-candidate-file-transfer.md`
- `docs/architecture/adr/0009-validated-dual-mode-search.md`
- `docs/prompts/frontend-parallel-session.md`
- Planned backend modules: `identity`, `jobs`, `candidates`, `imports`, `files`, `search`, and shared
  authorization/error infrastructure.
- Identity bootstrap domain/application files and `WorkspaceBootstrapServiceTests`.
- Identity authentication application/domain files and `AuthenticationServiceTests`.
- `apps/api/pom.xml`, `.env.example`, and Flyway
  `V2__priority_identity_candidate_schema.sql`.
- Identity `api`, `infrastructure/security`, and `infrastructure/persistence` adapters.
- Candidate roster application records/service, candidate API/problem handler, and
  `JdbcCandidateApplicationQueryStore` under `apps/api/src/main/java/com/talon/ats/candidates`.
- Candidate service/controller/Supabase persistence and seed integration tests under
  `apps/api/src/test/java/com/talon/ats/candidates`.
- `apps/web/src/features/candidates/{candidateGateway,httpCandidateGateway,CandidateWorkspace,CandidateProfilePanel,CandidateApplicationProfilePage}`
  plus focused tests and HTTP-only runtime construction in `apps/web/src/main.tsx`.
- `apps/web/src/app/{App,App.test}.tsx` and candidate-profile layout rules in
  `apps/web/src/styles.css`.
- `docs/superpowers/specs/2026-08-08-candidate-application-profile-page-design.md` and
  `docs/superpowers/plans/2026-08-08-candidate-application-profile-page.md`.
- Search workspace/action hierarchy under `apps/web/src/features/search` and shared visual states in
  `apps/web/src/styles.css`.
- `scripts/supabase/seed-candidate-search-demo.sql` and
  `docs/superpowers/plans/2026-08-08-db-backed-candidate-roster.md`.
- Identity `contract` named interface plus the `jobs` and `candidates` domain/application/API/JDBC
  modules.
- Flyway `V3__job_import_target_location.sql`.
- Import `domain` and `application` types plus `ImportMappingTests`.
- Import CSV application contracts, `infrastructure/csv/CommonsCsvApplicationParser`, parser tests,
  and the Apache Commons CSV dependency in `apps/api/pom.xml`.
- File-source application contracts and `files/infrastructure/drive` HTTP adapter.
- Generic `platform/ratelimit` contract, `LeakyBucket`, named Modulith interface, and focused tests.
- `files/application` private-object contracts and `files/infrastructure/storage/LocalObjectStorage`.
- File scan/extraction ports, `CandidateFileProcessingService`, and processing/download-policy tests.
- `AuthControllerTests`, `SecurityAdaptersTests`, `AuthenticationRuntimeConfigurationTests`,
  `DemoAdminProvisionerTests`, `PrioritySchemaMigrationIT`, `SupabaseSchemaSmokeIT`, and
  `SupabaseIdentityPersistenceIT`.

## Verification commands and observed results

- Candidate profile red/green evidence: an imported detail fixture with `availableFrom: null`
  initially failed with `RangeError: Invalid time value`; after making nullable fields explicit and
  guarding formatters, its focused component test passed. The dedicated-route tests initially
  failed because rows were buttons and the profile page module did not exist.
- Candidate/App focused gate:
  `npm --workspace @talon/web run test -- --run src/features/candidates/CandidateApplicationProfilePage.test.tsx src/features/candidates/CandidateProfilePanel.test.tsx src/features/candidates/CandidateWorkspace.test.tsx src/app/App.test.tsx`
  — 4 files, 28 tests, 0 failures before the final invalid-ID assertion was added.
- Final profile frontend gates: `npm run lint:web` passed with zero warnings/errors;
  `npm run test:web` passed 15 files and 88 tests including the final invalid-ID assertion;
  `npm run build:web` passed TypeScript and Vite production compilation with 1,694 modules
  transformed, and `npm run format:check:web` passed all matched files.
- Live availability check: listeners were present on 8080 (PID 42576) and 5173 (PID 38436),
  `GET /actuator/health` returned HTTP 200 with `UP`, and the Vite profile SPA URL returned HTTP 200.

- Final backend gate: Maven with Java 21 and the E-drive repository,
  `mvn -f apps/api/pom.xml spotless:apply verify` — build succeeded, 123/123 tests passed, all 209
  Java files were Spotless-clean, and the executable JAR declared Spring Boot `JarLauncher` with
  `com.talon.ats.TalonAtsApplication` as its start class.
- Candidate backend focused gate: `CandidateApplicationQueryServiceTests` plus
  `CandidateApplicationControllerTests` — 9/9 passed after witnessed missing-contract/endpoint red
  runs.
- Supabase rollback-only gate: expanded `SupabaseCandidateApplicationPersistenceIT` plus
  `SupabaseCandidateSearchSeedIT` — 2/2 passed against PostgreSQL 17.6 at Flyway V7. Running the seed
  twice in one rolled-back transaction produced 36 candidates, 36 applications, and zero
  candidate-file rows, so no demo rows persisted from the test.
- Candidate/search frontend focused gate — 22/22 passed, including real HTTP mapping, cumulative
  pagination, no-resume behavior, deterministic Cmd/Ctrl+K, editable AI filters, and explicit
  execution.
- Final frontend gates: `npm run lint:web`, `npm run test:web -- --run`, and
  `npm run build:web` — lint passed, 13 test files/85 tests passed, and Vite transformed 1,693 modules
  into the production bundle.
- `git diff --check` passed. Tracked and relevant untracked source scans found no Groq key, AWS access
  key ID, or RSA/OpenSSH/EC private-key marker. The demo SQL uses synthetic `.test` identities and no
  candidate files.
- Local runtime recovery: Maven initially could not clean the packaged JAR because the existing
  Talon Java process on 8080 held it open. After resolving and stopping only the Talon Java/Vite
  listeners, `mvn ... clean package -DskipTests` rebuilt the executable JAR successfully. Fresh
  listeners are active on 8080/5173; actuator health and the Vite root return HTTP 200. Direct and
  proxied application endpoints plus command search return the expected HTTP 401 without a bearer
  JWT. Two Vite proxy refusals occurred only during the API startup interval; the current health and
  proxy checks pass and the latest API error log is empty.
- Focused search backend gate: `mvn ...
  -Dtest=SearchDslValidatorTests,SearchServiceTests,GroqNaturalLanguageSearchInterpreterTests,ModuleArchitectureTests
  test` — 9/9 passed. This covers typed/forbidden predicates, compensation authorization, opaque
  cursors, no-silent-execution service flow, provider request minimization, strict schema, and module
  boundaries.
- Full network-free backend gate: `mvn ... spotless:apply verify` — build succeeded, 113/113 tests
  passed, the executable JAR was produced, and all 192 Java files were clean.
- Focused frontend search gate: `npm --workspace @talon/web run test -- --run
  src/features/search/SearchWorkspace.test.tsx src/features/search/CommandPalette.test.tsx` — 3/3
  passed, covering deterministic keyword search, Cmd/Ctrl+K, editable AI criteria, and explicit
  execution.
- Frontend lint and production build both passed after the search integration; Vite transformed
  1,699 modules and produced the production bundle.
- Initial Supabase profile attempts provided two diagnostics: loading all runtime variables polluted
  disabled-by-default unit tests, and the sandboxed database run was denied at the socket boundary.
  Neither result was counted as a search failure.
- Focused approved Supabase schema smoke: direct Failsafe run of `SupabaseSchemaSmokeIT` — Flyway
  validated seven migrations against PostgreSQL 17.6, reported schema version 7 current, and the
  search document/index assertions passed 1/1.

- `git -c safe.directory=E:/Project/LiveBuildTask diff --check` — passed with no whitespace errors
  in the architecture changes (Git emitted only the existing `initial_requirements.txt` line-ending
  warning).
- `rg` scans for active Cognito, CSV/ZIP, Gemini fallback, and Google Calendar decisions — no active
  references remain; Cognito appears only in ADR 0007 context explaining the superseded choice.
- Focused red test: `mvn ... -Dtest=WorkspaceBootstrapServiceTests test` — failed at test compile
  because the old `AppUser` lacked `normalizedEmail/passwordHash` and the store still exposed
  `hasMembership(cognitoSubject)`, confirming the intended contract gap.
- Focused green test: the same command — 3 tests passed.
- Full verification: `mvn ... spotless:apply verify` — build succeeded; 13 tests passed and Spotless
  reported all 22 Java files clean.
- Authentication focused red test — failed at test compilation because all new login/session types
  were intentionally absent.
- Authentication focused green test — 4 tests passed.
- Authentication full verification: `mvn ... spotless:apply verify` — build succeeded; 17 tests
  passed and Spotless reported all 32 Java files clean.
- Initial auth HTTP red test — dependencies downloaded to `E:\maven-repo`, then compilation failed
  because controller/security classes were intentionally absent.
- Security condition red/diagnostic run — all auth routes returned 403 with no handler because
  bean-order conditions selected the safe fallback; replaced them with explicit activation.
- Adapter red runs identified the installed Nimbus encoder constructor, fixed-clock JWT expiry in
  the test, and the need for an absolute URI issuer. The final focused suite passed 10/10.
- Current full verification: `mvn ... spotless:apply verify` — build succeeded; 23 tests passed and
  Spotless reported all 39 Java files clean.
- Environment verification: Maven 3.9.11 uses Oracle Java 21.0.11 from
  `C:\Program Files\Java\jdk-21.0.11`; direct Maven commands now work with the E-drive cache.
- Initial Testcontainers run with the Docker Desktop Linux engine and Testcontainers 1.21.4 —
  Flyway V1/V2 applied to PostgreSQL 17.10 and five schema/RLS/constraint tests passed.
- Supabase configuration shape validation checks JDBC prefix, session-pooler port 5432, separate
  username/password, and ignored-file status without printing values.
- Supabase smoke: `mvn ... -Psupabase-smoke spotless:apply verify` — Flyway applied two migrations
  to PostgreSQL 17.6; schema, `talon_app`, and RLS policy assertions passed. The expanded rerun
  completed in 1:23 with 26 local tests and three Supabase integration tests passing.
- Persistence red test failed at compilation because `JdbcIdentityAccountStore` was absent; green
  Supabase tests verified account mapping, workspace-role mapping, RLS transaction context, atomic
  refresh/login persistence, atomic workspace bootstrap, duplicate protection, and cleanup.
- Runtime wiring red test failed because `AuthenticationRuntimeConfiguration` was absent; the
  focused green run passed three configuration tests. Provisioner red failed because its runner was
  absent; the focused green run passed two idempotency/configuration tests.
- Current network-free full verification: `mvn ... spotless:apply verify` — build succeeded; 28
  tests passed and Spotless reported all 49 Java files clean. Docker and Supabase were not started
  or contacted by this default command.
- Local secret shape/ignore verification — both keys decoded to at least 32 bytes, were distinct,
  the demo hash matched the BCrypt format, both enable flags were true, both ignored files were
  excluded by Git, and no values were printed.
- Live API smoke against Supabase — Flyway reported schema version 2, Tomcat started on port 8080,
  the idempotent provisioner created the demo workspace/Admin, `/actuator/health` returned `UP`,
  `/api/v1/auth/login` succeeded, and the resulting bearer token resolved `/api/v1/session` with
  `WORKSPACE_ADMIN` plus non-empty user/workspace IDs. Passwords and tokens were not printed.
- Auth/frontend-contract red run — focused tests failed at compilation because authenticated account,
  token claims, and response types did not yet contain `workspaceName`. Green run passed 10/10 auth
  controller/service/adapter tests after the additive contract implementation.
- Jobs service red run — test compilation failed because the jobs module did not exist. Green
  service run passed 3/3; controller red failed because `JobController` was absent and its green run
  passed 2/2.
- The first expanded verification correctly failed the Modulith boundary test because jobs imported
  an unexposed identity-domain enum. Moving that enum into the named workspace-access contract made
  the focused auth/jobs/architecture suite pass 16/16.
- Candidate/application service red run — test compilation failed because the application port and
  typed models were absent. Green service plus module-boundary run passed 4/4.
- Priority persistence foundation checkpoint committed as `c7037e6`.
- Current network-free full verification: `mvn ... spotless:apply verify` — build succeeded; 36
  tests passed and Spotless reported all 72 Java files clean.
- Latest `supabase-smoke` attempt: all 33 then-current local tests passed, but four integration tests
  failed to obtain a socket to the configured session pooler (`Permission denied: getsockopt`). An
  escalated retry remained blocked on the remote connection and was terminated; Flyway V3 was not
  claimed as applied.
- Import-domain red run failed at test compilation because canonical mapping/state/validation types
  were absent. The focused green run passed 7/7 import and module-boundary tests.
- Current network-free full verification after import Task 1: `mvn ... spotless:apply verify` —
  build succeeded; 42 tests passed and Spotless reported all 84 Java files clean.
- CSV-parser red run failed at test compilation because the parser port, inspection/result models,
  and Commons CSV adapter were absent. The focused green run passed 14/14 CSV, mapping, and module
  boundary tests.
- Current network-free full verification after import Task 2: `mvn ... spotless:apply verify` —
  build succeeded; 49 tests passed, the executable JAR was produced, and Spotless reported all 92
  Java files clean.
- Drive-source red run failed at test compilation because the file-source, HTTP adapter, and limiter
  contracts were absent. The initial focused green run passed 10/10 adapter/limiter tests.
- A review regression test reproduced an HTTP-body read-after-close error; removing the redundant
  post-copy read made that test pass while the counting stream continues to enforce the hard limit.
- The first module-boundary run rejected the files module's dependency on internal platform types.
  Exposing only `platform::rate-limiting` as a named interface made the focused source, limiter, and
  architecture suite pass 13/13.
- Current network-free full verification after import Task 3: `mvn ... spotless:apply verify` —
  build succeeded; 61 tests passed, the executable JAR was produced, and Spotless reported all 109
  Java files clean.
- Private-storage red run failed at test compilation because the key/storage/metadata types and
  local adapter were absent. The first green behavior run exposed only sandbox cleanup failures from
  JUnit allocating `@TempDir` under C:. Moving synthetic test roots beneath the E:-drive Maven
  `target` directory made the storage, Drive, and architecture suite pass 15/15.
- Current network-free full verification after the Task 4 local-storage checkpoint:
  `mvn ... spotless:apply verify` — build succeeded; 65 tests passed, the executable JAR was
  produced, and Spotless reported all 115 Java files clean.
- Scan-processing red run failed at test compilation because the processing service, verdict,
  scanner/extractor ports, bounded result, and stable processing exception were absent. The focused
  service/storage/architecture run passed 8/8 after implementing the fail-closed boundary.
- Current network-free full verification after the Task 4 processing-policy checkpoint:
  `mvn ... spotless:apply verify` — build succeeded; 68 tests passed, the executable JAR was
  produced, and Spotless reported all 122 Java files clean.
- Strict-template RED failed at test compilation because `StrictTalonImportTemplate` was absent;
  the final focused template/parser/mapping gate passed 19/19 and committed as `f031a54`.
- Object-deletion RED failed on the missing port method; the final storage/Drive/file-processing
  gate passed 18/18 and committed as `2422e2f`.
- Supabase schema RED observed Flyway version 3 and missing `candidate_import`; forward V4 then
  applied successfully and `SupabaseSchemaSmokeIT` passed against PostgreSQL 17.6.
- JDBC persistence RED failed at compilation because the draft/preview port and adapter were
  absent. `SupabaseImportDraftPersistenceIT` then passed 2/2 with random synthetic tenant data,
  proving draft readback, RLS scoping, JSONB mapping, atomic preview replacement, replay, and
  cleanup.
- The first full gate correctly failed because imports referenced the files module's unexposed
  application contract. Adding `files::contracts` made `ModuleArchitectureTests` pass.
- Current network-free full verification: `mvn ... spotless:apply verify` — build succeeded; 75
  tests passed, the executable JAR was produced, and Spotless reported all 130 Java files clean.
- Durable import application workflow committed as `f27a935`: authorized ADMIN/RECRUITER uploads
  now require an importable job, store the original CSV behind the private `ObjectStorage` port,
  compensate storage when draft persistence fails, and restore repeatable validation previews.
- The authenticated HTTP/runtime checkpoint adds template download, multipart draft creation,
  strict validation, preview restoration, stable RFC 9457 problems, and fail-closed storage-provider
  selection. A files-module factory contract creates local private storage without exposing its
  infrastructure adapter to the imports module.
- Focused import/auth/jobs/runtime/architecture verification passed 17/17.
- Current network-free full verification after the import HTTP checkpoint:
  `mvn ... spotless:apply verify` — build succeeded; 93 tests passed, the executable JAR was
  produced, and Spotless reported all 142 Java files clean.
- Current processing/resume-transfer full verification: `mvn ... verify` — build succeeded; all 93
  tests passed, the executable JAR was produced, and Spotless reported all 152 Java files clean.
- Frontend integration gate: `npm run lint:web`, `npm run test:web -- --run`, and
  `npm run build:web` — current lint passed, 78/78 tests passed, and Vite produced the production
  bundle.
- Supabase V6 smoke: Flyway validated six migrations, advanced schema version 5 to 6, and
  `SupabaseSchemaSmokeIT` passed against PostgreSQL 17.6.
- Live local-HTTP/Supabase smoke: health and login succeeded; a one-row upload validated 1/0/0,
  confirmation returned `CONFIRMED`, candidate/application processing reached 1/1, same-key replay
  returned the same import, and the intentionally fake Drive ID produced safe retryable
  `RESUME_FETCH_FAILED` before the parent reached `COMPLETED_WITH_ERRORS`.
- S3 adapter RED failed because `S3ObjectStorage` was absent. The final focused adapter/provider gate
  passed 9/9, covering bounded upload, oversize rejection, exact promotion, clean-only presigning,
  local selection, S3 selection, and unknown-provider fail-closed behavior.
- IaC static checks found no public-read ACL, website configuration, forced bucket deletion, or AWS
  access-key variables. Required controls were present for all four public-access blocks,
  `BucketOwnerEnforced`, AES256 encryption, TLS-only policy, and final `-vraj` naming.
- Current network-free full backend verification: `mvn ... spotless:apply verify` first passed all
  99 tests but packaging was blocked because the manual-test API held the executable JAR open.
  After stopping only that process, `mvn ... verify` passed 99/99, produced the executable JAR, and
  Spotless reported all 154 Java files clean. The API was restarted and health returned `UP`.
- Real Drive MIME regression RED: the new binary-content test failed with `Resume must be a PDF`
  against the prior strict header check. After requiring the PDF signature for Drive binary media,
  the focused source-adapter gate passed 11/11.
- Current full backend gate after the Drive correction: `mvn ... spotless:apply verify` passed
  100/100, produced the executable JAR, and kept all 154 Java files clean.
- Real public-Drive/Supabase smoke: authenticated upload validated 1 valid and 0 invalid rows;
  durable processing reached 1/1 with `APPLICATION_CREATED`. Private local quarantine contained the
  newly copied 111,093-byte file and its first five bytes were `%PDF-`. The ignored source URL,
  generated synthetic candidate address, access token, credentials, and object key were not logged.
- Focused pre-commit review found no critical issue. Review corrections removed the invalid
  SSE-S3/bucket-key combination, aligned IAM with the current combined runtime, bounded noncurrent
  clean-resume retention to 30 days, replaced the AWS-provider-v6 deprecated region attribute, and
  strengthened exact S3 request/order and both Drive binary-media tests. The reported Terraform
  plan-ignore gap was checked against `HEAD` and rejected: `*.tfplan` was already ignored.
- Post-review focused verification: S3 and Drive adapter suites passed 16/16. Upload assertions now
  cover bucket, opaque key, AES256, and content type; promotion assertions cover exact copy/delete
  keys and ordering.
- Implemented `POST /api/v1/auth/refresh` and `/logout` with a Secure, HttpOnly, SameSite=Strict,
  `/api/v1/auth` browser-session cookie. Refresh hashes the presented token, consumes it once under
  a PostgreSQL row lock, inserts a child token in the same family, rechecks active account/membership,
  and rejects/revokes replay. Logout revokes the family and always clears the browser cookie.
- Updated `HttpAuthGateway` to hydrate a validated, unexpired current-tab session synchronously from
  `sessionStorage`, preserve the bearer `/session` validation path, fall back to cookie refresh when
  tab state is absent, and call server logout before clearing memory and tab state. `localStorage`
  remains unused.
- Refresh focused verification: controller/service tests passed 13/13; frontend auth suites passed
  29/29; the live Supabase persistence suite passed 3/3 including one-use rotation and replay-family
  revocation. Final gates passed: Maven 113/113 with a packaged JAR, frontend lint, 78/78 tests, and
  production build. The refreshed JAR is running against Supabase and health is `UP`; user manual
  refresh/logout confirmation is pending.
- Immediate-refresh follow-up: the fake candidate skeleton was removed. Focused frontend auth suites
  passed 31/31. The executable API JAR was rebuilt after stopping the stale lock holder; frontend
  port 5173 returned HTTP 200 and Supabase-backed API port 8080 reported `UP`. Manual sign-in,
  refresh, logout, and tab-close checks are the exact next gate.

## Blockers, prerequisites, and exact next step

- Exact profile manual gate: sign in at `http://127.0.0.1:5173`, open Candidates, click a synthetic
  candidate and an imported candidate such as Drive Smoke, confirm the URL changes to
  `/candidates/applications/{applicationId}`, verify the profile renders near the top with Back to
  candidates, and confirm unavailable/nullable values show safe fallback text. Resume download must
  remain unavailable unless the backend reports an exact `CLEAN` candidate file.
- Import history/recovery is deliberately not part of this follow-up. Implement it only after the
  public Drive PDF → private AWS S3 quarantine → malware scan → clean promotion path and its
  Terraform/IAM resources are applied and verified; otherwise history would imply recoverability
  that the deployment cannot yet guarantee.

- Candidate roster/manual prerequisite: in
  `scripts/supabase/seed-candidate-search-demo.sql`, set `v_workspace_slug` to the intended existing
  workspace. A non-demo/test workspace additionally requires the operator to deliberately set
  `v_allow_non_demo_workspace := true`; then run the whole `DO` block in Supabase SQL Editor. The
  expected notice is 36 candidates and 36 applications. The script is idempotent and creates no
  resumes, so these rows correctly display `No resume uploaded`.
- Exact candidate/search manual gate: the current uncommitted API/web code is running locally. Sign
  in at `http://127.0.0.1:5173` as an Admin/Recruiter in the seeded workspace, verify 36 application
  rows across Load more pages and a detail panel, then test Cmd/Ctrl+K, deterministic `Java`, Build
  AI filters for `candidates with expected CTC below 40 LPA`, edit/remove a chip, and explicitly click
  Search candidates. Finally, verify a missing/failed Groq provider leaves deterministic search
  usable. No controllable browser was attached to this session, so the authenticated UI flow remains
  for the user to observe.
- Resume delivery cannot be proven with the synthetic seed because it intentionally creates no
  `candidate_file`. Use a successfully imported and scan-promoted `CLEAN` row for the later download
  smoke; quarantine/pending/failed/no-resume rows must remain unavailable.
- No implementation commit or push is the current instruction. The working tree also contains
  parallel CI/CD, Terraform, authentication, import, and storage changes; they were preserved rather
  than separated or overwritten.
- AWS bootstrap prerequisite: locally authenticated AWS CLI access must make
  `aws sts get-caller-identity` succeed in the intended account. Confirm the non-secret account ID,
  deployment region, and `(Get-Command terraform).Source`; never paste access keys. GitHub Actions
  will use OIDC and will not store permanent AWS credentials.
- Terraform remote-state/OIDC execution is specified in
  `docs/superpowers/plans/2026-08-08-terraform-state-github-oidc.md`. The bootstrap state bucket and
  branch-restricted GitHub role are the next implementation step after AWS authentication. ECS is
  explicitly gated on a later real private-S3 upload reliability smoke.
- Search code and Supabase V7 are ready for the remaining live smoke. The freshly rebuilt API is
  healthy on 8080 and Vite is serving on 5173; authenticated candidate/search browser verification
  remains the immediate gate.
- Exact search next step: confirm the synthetic seed in the intended workspace, then perform
  authenticated Cmd/Ctrl+K, keyword `Java`, Groq interpretation of `candidates with
  expected CTC below 40 LPA`, editable-chip execution, and provider-failure fallback checks. Do not
  claim manual readiness until all are observed.

- Deployed replay-import diagnosis: the latest three rows created three candidate/application file
  records and wrote three private S3 quarantine objects (333,279 aggregate bytes), but import-row
  file links remained empty and each row was incorrectly marked `RESUME_TRANSFER_FAILED`. The
  candidate-file upsert preserved the existing application file primary key while the worker tried
  to link its newly generated ID. `CandidateImportAccess.attachResume` now returns the actual
  `RETURNING id`, and the worker records that persisted identity. The regression failed against the
  old void contract, then passed after the correction; the full backend gate passed 124/124.
- Resume URL handling remains private by design: PostgreSQL stores only the private object key and
  file status. Authorized clean-file preview generates a short-lived presigned URL on demand; an
  expiring presigned URL is never persisted in the database.

- Docker/Testcontainers is not required for the current manual path because the API uses the
  configured Supabase JDBC connection. The retained PostgreSQL Testcontainers gate remains useful
  when Docker Desktop is running from its E:-drive installation. Supabase is green at Flyway V6.
- The temporary branch split was corrected with a safe fast-forward: `codex/backend-api` now
  contains persistence commit `7498989` and is the active checkout. The appmod branch was preserved
  at the same commit; no branch or work was reset/deleted.
- AWS account, private S3/SQS resources, malware scanner runtime, and a funded xAI key remain
  external prerequisites for their later checkpoints. The public Drive provider smoke is green.
- The user reports Terraform 1.15.8 is installed, but the current Codex process does not inherit its
  executable path (`terraform` is not recognized). The new HCL therefore has static review evidence
  only in this session; `terraform fmt -check -recursive`, `validate`, reviewed `plan`, and apply
  remain explicit gates before any AWS resource is claimed. The exact executable path or a reopened
  VS Code session is required to run that gate here.
- The GitHub remote is `git@github.com:VraJ-594/talon-vraj.git`; verified import checkpoint
  `1d00767` is pushed on `origin/codex/backend-api`.
- Exact remaining backend feature step: implement fail-closed scan/clean promotion. Candidate
  application projections and clean-only resume delivery are now exposed; AWS delivery additionally
  requires the Terraform-created private bucket and task-role access.
