# Talon ATS Low-Level Design

## 1. Repository layout

```text
apps/
  web/                         React/Vite TypeScript SPA
  api/                         Spring Boot API and worker profiles
infra/
  terraform/                   bootstrap, modules, environments
docs/
  plans/                       delivery plans
  architecture/                HLD, LLD, ADRs, diagrams, runbooks
```

The frontend and backend are separately deployable but versioned together. OpenAPI is generated from the backend and used to produce the frontend client during CI.

## 2. Backend module structure

Use the root package `com.talon.ats`. Each top-level domain module owns its persistence and public application facade.

```text
com.talon.ats.jobs
  api/              controllers and transport DTOs
  application/      commands, queries, authorization orchestration
  domain/           aggregates, value objects, policies, domain events
  infrastructure/   JPA repositories and adapter implementations
```

Allowed dependency direction is `api -> application -> domain`. Infrastructure implements ports declared by application/domain. A module may call another module only through that module's public application facade or consume a published event. JPA entities and repository interfaces are not shared between modules. Spring Modulith's application-module verifier is mandatory; custom ArchUnit rules are added only for constraints not covered by that verifier.

## 3. Runtime profiles

### API profile

- Embedded HTTP server and OpenAPI.
- Cognito resource-server validation.
- Request principal and transaction-scoped tenant context.
- Controllers, command/query handlers, outbox writes, and health endpoints.
- No long-running provider calls in request threads.

### Worker profile

- SQS listeners and scheduled reconciliation.
- Outbox dispatcher if not run as an API sidecar thread.
- File scan/parse, AI provider routing, SES, Google Calendar, report maintenance, and retention consumers.
- No public HTTP routes except health/metrics on an internal port.

The same artifact enables both profiles, guaranteeing identical domain rules and event definitions.

## 4. Request processing

1. Correlation filter accepts or creates `X-Correlation-Id`.
2. Spring Security validates issuer, signature, audience, expiry, and token use.
3. Membership resolver loads the user/workspace/role and creates `RequestPrincipal`.
4. Transaction interceptor executes `SET LOCAL app.workspace_id = :workspaceId` before tenant SQL.
5. Controller validates transport shape and delegates to one command/query handler.
6. Handler authorizes action and resource scope, invokes aggregate rules, persists state/history/outbox, and maps a response DTO.
7. Exception mapper returns `application/problem+json` with stable error code and correlation ID.

## 5. Domain state machines

### Job

```text
DRAFT -> ACTIVE -> ON_HOLD -> ACTIVE
ACTIVE|ON_HOLD -> CLOSED
```

Only drafts allow structural pipeline changes. Publishing requires role basics, at least two ordered stages, a recruiter/owner, and valid scorecard configuration. Closed jobs accept no new applications.

### Application

```text
ACTIVE(stageId) -> ACTIVE(next or authorized stage)
ACTIVE -> HIRED | REJECTED | WITHDRAWN
```

Every transition records from/to stage, actor, reason, source operation, and effective timestamp. Terminal outcomes cannot be moved through ordinary transition APIs.

### Interview and scorecard

```text
Interview: DRAFT -> SCHEDULED -> COMPLETED | CANCELLED
Scorecard: DRAFT -> SUBMITTED -> REOPENED -> SUBMITTED
```

Rubrics are snapshotted at interview creation. Peer scorecards are hidden from an interviewer until that interviewer has submitted.

### Offer

```text
DRAFT -> PENDING_APPROVAL -> APPROVED -> SENT -> ACCEPTED | DECLINED | EXPIRED
                    \-> REJECTED
```

Material edits while pending/approved create a new version and return to `DRAFT`. Only the active ordered approver may approve/reject. Delivery is permitted once for an approved version unless the previous attempt conclusively failed before provider acceptance.

## 6. Asynchronous events

Event envelopes contain `eventId`, `eventType`, `schemaVersion`, `workspaceId`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, and a minimal payload. Candidate PII and resume text are referenced by authorized identifiers rather than copied into general queues.

Initial event types:

- `CandidateImportConfirmed.v1`
- `ResumeStored.v1`
- `ResumeParsingRequested.v1`
- `ResumeAssessmentRequested.v1`
- `ResumeAssessmentCompleted.v1`
- `InterviewSchedulingRequested.v1`
- `CalendarReconciliationRequested.v1`
- `MessageDeliveryRequested.v1`
- `OfferApprovalAdvanced.v1`
- `CandidateRetentionDue.v1`

Consumers insert `(consumer_name, event_id)` into `processed_message` before completing side effects or use a provider-specific idempotency key. Retries classify failures as transient, rate-limited, invalid/permanent, or security/quarantine failures.

## 7. Frontend structure

```text
src/
  app/               router, providers, auth bootstrap, layout
  features/          jobs, pipeline, candidates, review, scheduling, offers, reports
  components/        reusable Talon UI primitives
  api/               generated client plus query adapters
  lib/               formatting, validation, permissions, telemetry
  styles/            design tokens and global rules
```

- TanStack Query owns server state and invalidation.
- React Hook Form and Zod own form state and client validation; the server remains authoritative.
- URL search parameters own filters/sorts where shareable.
- Local component state owns dialog, selection, and transient drag state.
- Avoid a global state library unless a concrete cross-route state requirement appears.

## 8. UI behavior

- Desktop minimum width is 1,280 px.
- Sidebar remains fixed; wide Kanban and scheduling surfaces scroll horizontally.
- Keyboard actions have visible labels and do not require drag-and-drop.
- Every query surface defines loading, empty, error, stale, and unauthorized states.
- Optimistic UI is limited to reversible commands such as notification read state. Pipeline moves wait for server confirmation or retain a rollback snapshot.
- Forms preserve draft input across validation errors and prevent duplicate submission.

## 9. Search and reporting

Search uses normalized columns, PostgreSQL `tsvector`, and `pg_trgm` indexes. Search results are permission-filtered before ranking and return only display-safe fields.

Reports query immutable history rather than mutable card counts. Expensive aggregates use bounded date windows and appropriate composite/partial indexes. Materialized views are introduced only if measured query plans exceed latency targets.

## 10. Error contracts

Problem responses contain:

```json
{
  "type": "https://docs.talon.example/problems/optimistic-conflict",
  "title": "The application changed",
  "status": 409,
  "code": "APPLICATION_VERSION_CONFLICT",
  "detail": "Refresh the pipeline before retrying this move.",
  "correlationId": "9e925cb7-7cbb-47fa-8fc6-6b519ff4a6a5",
  "fieldErrors": []
}
```

Stable error classes include validation, unauthenticated, forbidden, not found within tenant scope, optimistic conflict, idempotency conflict, provider unavailable, rate limited, unsafe file, and illegal state transition.

## 11. Configuration

Configuration enters through environment variables or Secrets Manager references. The application validates required variables at startup and fails before accepting traffic. Secrets never receive defaults. Non-secret defaults cover ports, page sizes, timeouts, retry counts, file limits, and feature flags.

Feature flags are limited to integration enablement and safe rollout; they do not create long-lived alternate domain behavior.

AI selection uses `AI_PROVIDER=XAI_GROK|GEMINI|DISABLED`. `XAI_API_KEY` and optional `GEMINI_API_KEY` are runtime secrets. The router prefers the explicitly configured provider, validates structured output into the same internal schema, records provider/model/prompt versions, and converts quota or availability failures into a retryable/manual-review state rather than a hiring decision.

The deployed database URL targets the TLS Supabase Supavisor session pooler on port 5432. HikariCP connection limits are calculated from the database plan's connection allowance and the maximum API/worker task count. Local and Testcontainers profiles use ordinary PostgreSQL without Supabase-only code paths.

## 12. Coding rules

- Prefer immutable records/value objects at boundaries.
- Keep controllers thin and transaction boundaries in application services.
- Do not return JPA entities or accept them as API input.
- Use database constraints for invariants that can be expressed relationally.
- Avoid generic repositories/services that obscure domain intent.
- Use explicit mapping code or small generated mappers; do not share transport DTOs with persistence.
- Record actor and reason for every hiring decision or security-sensitive change.
