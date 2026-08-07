# Priority Import, Export, and Search Implementation Handoff

## Scope and current status

Status: in progress.

The approved active slice is minimal application-owned authentication followed by candidate CSV
import/private export and dual-mode candidate search. The design and three executable plans are
approved. The application-owned identity model and login/refresh-session creation contracts are
implemented and verified. Spring Security/JWT HTTP adapters are next.

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
- Docker-backed checks are blocked because Docker is not currently discoverable from this shell.

## Blockers, prerequisites, and exact next step

- External prerequisites: runnable Docker engine/CLI, PostgreSQL/Supabase connection, AWS account,
  private S3/SQS resources, malware scanner choice, and an xAI key with usable billing.
- Exact next step: add Spring Security dependencies and failing MockMvc tests for public health/
  login, protected session, generic 401, secure refresh transport, logout, and role claims.
