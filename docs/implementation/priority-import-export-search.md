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
login plus bearer-authenticated session.

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

## Blockers, prerequisites, and exact next step

- Supabase is connected through its TLS session pooler and is at Flyway version 2. Local database,
  JWT, refresh-HMAC, and demo-login values remain in ignored files and were never committed.
- AWS account, private S3/SQS resources, malware scanner choice, and a funded xAI key remain future
  external prerequisites for the two priority features.
- Exact next step: add the minimal authenticated job and candidate/application query APIs required
  by CSV import and deterministic/natural-language search, then begin the durable CSV import model.
