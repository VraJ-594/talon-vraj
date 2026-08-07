# Priority Import, Export, and Search Implementation Handoff

## Scope and current status

Status: in progress.

The approved active slice is minimal application-owned authentication followed by candidate CSV
import/private export and dual-mode candidate search. The application-owned login/session HTTP
contract, PostgreSQL schema, Supabase-hosted Flyway deployment, tenant-safe JDBC persistence,
production JWT bean wiring, and idempotent environment-only demo Admin provisioner are implemented
and verified. Independent local JWT/HMAC keys, a random demo password, and its BCrypt hash were
generated without printing values; the live Supabase-backed login and bearer-session flow passed.
Refresh rotation/logout remain deferred with advanced auth; the active basic-auth demo path is
login plus bearer-authenticated session. Login and session now expose the workspace name needed by
the frontend gateway. Tenant-scoped job APIs and the candidate/application create-or-match facade
are implemented and locally verified. Flyway V3 and real Supabase persistence tests for those new
paths are present but are not marked verified because the session pooler became unreachable during
the latest run.

## What changed

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
from becoming database instructions.

## Important paths

- Authentication: existing account → BCrypt verification → access JWT + rotating hashed refresh
  session → workspace/role principal.
- Import: select job → upload/map/preview CSV → confirm durable job → rate-limited Drive fetch →
  quarantine/scan/PDF extraction → private store → candidate/application result.
- Export: validated candidate criteria → durable export job → private CSV → authorized five-minute
  download URL; artifact lifecycle is seven days.
- Search: Cmd+K/explicit filters → typed criteria → PostgreSQL. Natural language → Grok restricted
  DSL → backend validation → the same typed criteria and repository.
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

## Blockers, prerequisites, and exact next step

- The last verified Supabase state is Flyway version 2. The latest pooler connection is currently
  unreachable from this shell, so V3 and the new job/candidate persistence integration tests remain
  pending. Local database, JWT, refresh-HMAC, and demo-login values remain in ignored files and were
  never committed.
- AWS account, private S3/SQS resources, malware scanner choice, a funded xAI key, and one synthetic
  anonymously downloadable Drive PDF for a live provider smoke remain future external prerequisites.
- Exact next step: implement contract-tested ClamAV-compatible scanning and bounded PDFBox text
  extraction (50 pages, 500,000 characters, ten seconds, two concurrent), then add five-minute
  download grants. After that, implement the S3 adapter and Terraform public-access assertions
  behind the completed `ObjectStorage` port. When the pooler is reachable, run the saved
  database-only `supabase-smoke` command to apply V3 and close the persistence gate.
