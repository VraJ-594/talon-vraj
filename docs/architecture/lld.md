# Talon ATS Low-Level Design

## 1. Repository layout

```text
apps/web/                  React/Vite TypeScript SPA
apps/api/                  Java 21 Spring Boot modular monolith
  src/main/java/com/talon/ats/
    identity/ jobs/ candidates/ imports/ files/ search/ shared/
  src/main/resources/db/migration/
infra/terraform/           account/region-parameterized AWS modules
tests/e2e/                 Playwright priority journey
docs/architecture/         decisions and contracts
docs/implementation/       observed implementation handoffs
```

Within a backend module use `domain`, `application`, `adapter/in/web`, and `adapter/out/*`.
Application services depend on ports; provider and persistence adapters depend inward.

## 2. Runtime profiles

### API

- versioned REST under `/api/v1`;
- JWT authentication and workspace/role principal;
- commands, queries, previews, status polling, and signed-download authorization;
- never performs Drive downloads, scans, PDF parsing, or full export generation inline.

### Worker

- same artifact and application handlers;
- local profile claims PostgreSQL jobs through a bounded dispatcher;
- AWS profile consumes SQS notifications and reclaims durable PostgreSQL state;
- retries are idempotent and poison work reaches a DLQ/failed row with a safe error code.

## 3. Core domain model

- `AppUser`: normalized email, BCrypt password hash, status.
- `WorkspaceMembership`: user, workspace, Admin/Recruiter role, status.
- `RefreshSession`: user/workspace, hashed token, expiry/revocation/rotation metadata.
- `Job`: workspace-owned import target.
- `Candidate`: one person per normalized workspace email.
- `Application`: one candidate application per selected job; typed form and compensation fields.
- `FileObject`: private object metadata and quarantine/scan/promotion state.
- `ImportJob` / `ImportRow`: durable aggregate and per-row outcome.
- `ExportJob`: durable criteria snapshot and private artifact state.
- `SearchCriteria`: text, allowlisted filters, allowlisted sort, cursor, limit.

Money uses ISO currency plus signed 64-bit integer minor units. INR LPA parsing is explicit and
overflow checked. Timestamps are UTC; display zones are IANA names.

## 4. Authentication request path

1. `POST /auth/login` normalizes email and applies per-IP/per-account rate limits.
2. Account repository loads only by normalized email; BCrypt verifies password.
3. Membership policy selects the authorized workspace and role.
4. Token service issues a short access JWT and opaque random refresh token.
5. Only a keyed hash of the refresh token is persisted.
6. Refresh rotates atomically; reuse revokes the token family.
7. A request filter verifies signature/issuer/audience/expiry and constructs `RequestPrincipal`.

Public sign-up, OAuth, MFA, password reset, and invitations are not active routes.

## 5. Import state and transactions

```text
UPLOADED -> MAPPED -> PREVIEWED -> QUEUED -> RUNNING -> COMPLETED
                                            \-> COMPLETED_WITH_ERRORS
                                            \-> FAILED

row: PENDING -> DOWNLOADING -> QUARANTINED -> SCANNING -> IMPORTED
                                             \-> REJECTED
          any retryable worker state -> RETRY_PENDING -> prior operation
```

- Upload/mapping/preview is read-only with respect to candidates.
- Confirm snapshots job, mapping, canonical values, and row validation using an idempotency key.
- A worker row is claimed using locking/lease metadata.
- Resume content reaches quarantine before the candidate/application transaction.
- Only a clean promoted resume permits the candidate/application transaction.
- Candidate match and application creation use database unique constraints for replay safety.
- Safe row error codes/details are stored; raw resume content and secrets are not.

Application-owned ports:

```java
interface ResumeSource { FetchedResume fetch(ValidatedResumeReference reference); }
interface ObjectStore { ObjectRef putQuarantine(...); ObjectRef promote(...); SignedGet signGet(...); }
interface MalwareScanner { ScanResult scan(ObjectRef quarantineObject); }
interface WorkDispatcher { void dispatch(WorkReference work); }
interface NaturalLanguageQueryInterpreter { InterpretedDsl interpret(QueryText query, DslSchema schema); }
```

The exact Java signatures evolve test first; provider DTOs never cross these boundaries.

## 6. Drive and file controls

- Accept only `https` and allowlisted Google Drive hosts/link shapes.
- Resolve and validate every redirect; reject loopback, link-local, private, reserved, and metadata
  addresses, including DNS rebinding results.
- Stream with timeouts and a hard 10 MB limit; require PDF media/signature and reject HTML.
- Default limiter: five starts/second, burst five, and five in flight per worker.
- Quarantine is not downloadable. Promotion requires `CLEAN` scan status.
- Private keys use opaque IDs, for example `workspaces/{workspaceId}/candidates/{candidateId}/...`;
  filenames/emails never appear in keys.
- Presigned GET URLs expire in five minutes and are created only after authorization.

## 7. Search contract

The canonical criteria supports:

- text over name, email, skills/profile fields, application answers, and extracted resume text;
- job, stage, source, application date, notice period, availability;
- current/expected compensation with explicit currency and integer minor units;
- allowlisted sorts such as relevance, name, application date, and expected compensation;
- bounded limit and opaque cursor.

The versioned DSL is JSON, not SQL. Each predicate is `{field, operator, value}`; fields define
allowed operators/types. Groq receives only query text, locale/time-zone context needed to parse it,
and the schema. Validation returns normalized criteria plus display chips. Repository adapters use
Criteria/JDBC parameters and workspace predicates; no model string becomes a SQL fragment.

## 8. Frontend structure and behavior

Feature folders: `auth`, `shell`, `jobs`, `imports`, `candidates`, `search`, plus shared API,
components, schemas, and test fixtures. TanStack Query owns server state; route/search params own
reproducible filters; form state stays local.

Active UI: sign-in, sidebar/header, job selector, CSV template/upload, column mapping, validation
preview, confirmation/progress/results, candidate list/profile, export, Cmd+K, and natural-language
search with interpreted editable chips. All support loading, empty, permission, validation, and
recoverable provider/error states and follow the supplied PDF visual system.

## 9. HTTP/error rules

- UUID identifiers, JSON, UTC ISO-8601, cursor pagination.
- `Idempotency-Key` on confirm/export and retryable commands.
- RFC 9457 `application/problem+json` with stable machine code, correlation ID, safe field/row
  errors, and no stack/provider leakage.
- Optimistic version fields on mutable resources.
- Bean Validation at the adapter edge and domain invariant checks inside application/domain code.

## 10. Configuration and coding rules

Environment config includes database URL, token issuer/audience/key reference, token lifetimes,
private bucket names, SQS URLs/local dispatcher mode, Drive allowlist/timeouts/limits, scanner,
and `SEARCH_AI_PROVIDER=XAI_GROK|DISABLED` plus model/key secret. No secrets or account-specific
ARNs are committed.

Use constructor injection, immutable commands/results, small interfaces, forward-only Flyway,
workspace-required repositories, domain tests before behavior, adapter contract tests, and
Spring Modulith boundary verification. Avoid generic repositories and speculative abstractions.
