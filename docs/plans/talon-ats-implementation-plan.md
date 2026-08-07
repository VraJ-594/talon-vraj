# Talon ATS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver a production-oriented, desktop ATS covering authentication, jobs, candidate pipelines, review scoring, scheduling, scorecards, offers, reports, bulk operations, AWS infrastructure, and Playwright automation.

**Architecture:** A React/Vite TypeScript SPA talks to a Spring Boot modular monolith through versioned REST APIs. Supabase-hosted PostgreSQL is the transactional source of truth; the same Spring Boot image runs API and SQS worker profiles. Terraform provisions the AWS runtime in `ap-south-1` using CloudFront/S3, Cognito, ALB/ECS Fargate, SQS, SES, Secrets Manager, and CloudWatch, and manages or imports the external Supabase project through the official provider.

**Tech Stack:** React, TypeScript, Vite, React Router, TanStack Query, Tailwind, Radix UI, Spring Boot, Java 21, Maven, Supabase PostgreSQL, Flyway, SQS, Cognito, Google Calendar, xAI Grok with an optional Gemini fallback, SES, Terraform, Docker, Testcontainers, Vitest, and Playwright.

## Global Constraints

- Implement the supplied Talon desktop designs; mobile redesign and public career pages are excluded.
- Keep one monorepo with separately deployable web, API/worker, and Terraform artifacts.
- Use a shared-schema multi-tenant model with `workspace_id`, backend authorization, PostgreSQL RLS, and tenant-prefixed object keys.
- Use four fixed roles: Workspace Admin, Recruiter, Hiring Manager, and Interviewer.
- Candidates are internal domain records and do not authenticate in v1.
- Use a person-plus-applications model: one candidate per workspace and one application per job.
- Keep the backend a modular monolith; do not add Kafka, Kubernetes, Redis, OpenSearch, or independent microservices.
- Slow or retryable work must run through an outbox and SQS, not in request threads.
- Grok is the preferred resume-analysis adapter when funded xAI API access is configured; Gemini is an optional fallback and `DISABLED` preserves manual review. Domain code depends only on `AiScoringProvider`, and no AI API is assumed to be permanently free.
- Configure the adapter explicitly with `AI_PROVIDER=XAI_GROK|GEMINI|DISABLED`; never infer or silently switch providers without recording the provider/model used.
- Treat an xAI API key with usable credits/billing as an external prerequisite. Current xAI documentation says API requests are metered and Indian payment cards may not be accepted, so verify organization billing before scheduling the AI demo.
- Supabase Free may be used for development and the first demo. Production launch requires the same database to be upgraded to a non-pausing tier with automated backups/PITR, or an approved equivalent managed PostgreSQL service; AWS RDS is excluded from this design.
- Use plain JDBC/JPA, Flyway, PostgreSQL SQL, and the Supavisor session pooler rather than Supabase Auth, Storage, or Data APIs so the database remains portable.
- Support Google Calendar two-way synchronization and ATS-sent SES email; exclude Microsoft Calendar and Gmail inbox sync.
- Support CSV plus ZIP candidate imports up to 2,000 rows.
- Offers use sequential approvals, version reset after material edits, PDF delivery through SES, and manual acceptance status.
- Store money as currency plus integer minor units, timestamps as UTC, and human time zones as IANA identifiers.
- Apply database changes only through forward Flyway migrations.
- Begin with local development and manual AWS deployment; add GitHub OIDC deployment automation after the first cloud slice is stable.
- Treat the product as SOC 2-aligned; do not claim certification.
- Use test-first implementation for domain rules and run verification before marking any task complete.

---

## Program Boundaries

This program is intentionally split into independently reviewable vertical phases. A phase may begin only when its consumed interfaces exist and its predecessor's acceptance suite passes. Frontend, backend, infrastructure, and test work within a phase may run in parallel after the OpenAPI and database contracts are agreed.

### Stable public interfaces

```java
public interface AiScoringProvider {
    ResumeAssessment assess(ResumeText resume, JobScoringRubric rubric);
}

public interface CalendarProvider {
    AvailabilityResult availability(AvailabilityQuery query);
    ExternalEvent createInterview(InterviewEvent event);
    ExternalEvent updateInterview(String externalId, InterviewEvent event);
    void cancelInterview(String externalId);
}

public interface MailProvider {
    DeliveryReceipt send(CandidateMessage message);
}

public interface ObjectStore {
    PresignedUpload createUpload(UploadRequest request);
    StoredObjectMetadata inspect(ObjectKey key);
    PresignedDownload createDownload(ObjectKey key);
}
```

All public HTTP operations use `/api/v1`, UUID identifiers, cursor pagination, RFC 9457 problem details, idempotency keys for retried commands, and entity versions for optimistic concurrency.

## Task 1: Repository and Executable Baseline

**Files:**

- Create `apps/web/` as the React/Vite TypeScript application.
- Create `apps/api/` as the Java 21 Spring Boot Maven application.
- Create root Docker Compose, editor, formatting, environment-example, and build documentation files.
- Test through frontend smoke tests, a Spring context test, Spring Modulith verification, and Docker Compose health checks.

**Interfaces:**

- Produces `GET /api/v1/health`, the base application layout, PostgreSQL connectivity, and repeatable local commands.
- Produces the package/module conventions described in `docs/architecture/lld.md`.

- [x] Create the frontend and backend smoke tests before application code.
- [x] Verify the tests fail because the entrypoints do not exist.
- [x] Add strict TypeScript, linting, formatting, Java 21, Maven Wrapper, Spring Actuator, and PostgreSQL/Flyway dependencies.
- [x] Add Docker Compose services for PostgreSQL and a local mail catcher with explicit health checks and named volumes.
- [x] Implement `/api/v1/health` and the frontend shell route.
- [ ] Run frontend tests, Maven tests, production builds, and Docker Compose health verification.
- [ ] Commit as `chore: establish ATS application baseline`.

## Task 2: Identity, Workspace, and Tenant Isolation

**Files:**

- Create backend identity/workspace modules and Flyway migrations for users, workspaces, memberships, invitations, and audit events.
- Create frontend sign-in callback, TOTP enrollment, workspace onboarding, invitation, and access-denied routes.
- Create Cognito adapter and local fake identity provider.

**Interfaces:**

- Consumes verified JWT `sub`, email, and identity-provider claims.
- Produces `RequestPrincipal(userId, workspaceId, role)` and workspace-scoped transaction context.
- Produces `/api/v1/session`, `/api/v1/workspaces`, `/api/v1/invitations`, and membership administration APIs.

- [ ] Write failing tests for first-user workspace creation, invitation expiry, role permissions, cross-workspace denial, and PostgreSQL RLS.
- [ ] Add the tenant tables, constraints, RLS policies, audit-event append API, and Spring Modulith boundary verification.
- [ ] Implement Cognito JWT verification with local fake tokens for deterministic tests.
- [ ] Implement first-user onboarding and single-use invitation acceptance.
- [ ] Implement fixed-role policies in backend services and corresponding frontend route guards.
- [ ] Verify local auth flows, RLS integration tests, and a real Cognito smoke test in the AWS environment.
- [ ] Commit as `feat: add workspace identity and tenant isolation`.

## Task 3: Shared Talon Shell and Job Management

**Files:**

- Create reusable frontend layout, sidebar, header, dialog, form, command, table, avatar, badge, and notification components.
- Create job, department, pipeline-template, hiring-team, and scorecard-template backend modules and migrations.
- Create the Jobs page and four-step New Job wizard.

**Interfaces:**

- Produces `/api/v1/jobs`, `/api/v1/departments`, `/api/v1/pipeline-templates`, and `/api/v1/jobs/{id}/publish`.
- Produces `JobStatus = DRAFT | ACTIVE | ON_HOLD | CLOSED` and versioned draft commands.

- [ ] Write failing tests for draft creation, wizard validation, pipeline copying, structural lock after publish, department grouping, and role restrictions.
- [ ] Add normalized job tables and a default workspace pipeline template.
- [ ] Implement job application services and versioned REST contracts.
- [ ] Implement PDF-faithful Jobs and New Job screens using Tailwind tokens and Radix primitives.
- [ ] Add keyboard navigation, empty/loading/error states, and desktop overflow behavior.
- [ ] Verify unit, integration, component, accessibility, and Playwright create-job tests.
- [ ] Commit as `feat: add job management workflow`.

## Task 4: Candidates, Applications, and Kanban Pipeline

**Files:**

- Create candidate, application, stage-transition, activity, and file metadata modules/migrations.
- Create candidate add flow, job pipeline board, application cards, filters, and candidate profile shell.

**Interfaces:**

- Produces `/api/v1/candidates`, `/api/v1/jobs/{jobId}/applications`, `/api/v1/applications/{id}/transitions`, and `/api/v1/applications/{id}/activity`.
- Produces immutable `StageTransition` events and `ApplicationOutcome = HIRED | REJECTED | WITHDRAWN`.

- [ ] Write failing tests for candidate deduplication, one application per candidate/job, legal transitions, terminal outcomes, optimistic conflicts, and time-in-stage metrics.
- [ ] Implement tables, indexes, constraints, services, and transition audit events.
- [ ] Implement the horizontally scrollable Kanban board with dnd-kit and accessible non-drag controls.
- [ ] Implement unified candidate profile tabs and stage/action header.
- [ ] Verify concurrent transition tests and Playwright add-candidate/move-stage flows.
- [ ] Commit as `feat: add candidate pipeline and profile foundation`.

## Task 5: Bulk Import, File Security, and Resume Processing

**Files:**

- Create import-job, import-row, file-object, outbox, and idempotency modules/migrations.
- Create CSV/ZIP upload, mapping, validation preview, progress, and error-report screens.
- Create S3 object-store and malware-scanner adapters.

**Interfaces:**

- Produces presigned upload APIs and `/api/v1/imports/{id}/preview|confirm|status`.
- Publishes `CandidateImportConfirmed`, `ResumeStored`, and `ResumeParsingRequested` events.

- [ ] Write failing tests for 2,000 rows, duplicate emails, unsafe ZIP paths, archive limits, MIME spoofing, missing filenames, idempotent confirmation, and partial row failures.
- [ ] Implement streaming CSV parsing and bounded ZIP extraction without loading the archive into memory.
- [ ] Implement dry-run preview and confirmation transaction boundaries.
- [ ] Implement S3-private object keys, short-lived URLs, scan state, and quarantine behavior.
- [ ] Implement SQS consumers with retry limits, DLQs, and replay-safe idempotency.
- [ ] Verify import integration tests, a 2,000-row performance fixture, and the Playwright import flow.
- [ ] Commit as `feat: add secure bulk candidate import`.

## Task 6: AI Assessment and Review Inbox

**Files:**

- Create AI assessment, rubric, parsing, xAI Grok adapter, optional Gemini fallback adapter, and review-decision modules.
- Create Review Inbox queue, signal panel, explanations, and keyboard shortcuts.

**Interfaces:**

- Consumes `ResumeParsingRequested` and publishes `ResumeAssessmentCompleted|Failed`.
- Produces `/api/v1/review-queue`, `/api/v1/applications/{id}/assessment`, and `/api/v1/review-decisions`.
- Persists provider, model, rubric version, prompt version, evidence, confidence, warnings, and validated schema.

- [ ] Write failing tests for output-schema rejection, prompt injection isolation, provider timeout, retry/DLQ, deterministic format signals, ranking, and no automatic rejection.
- [ ] Implement PDF/DOCX extraction and deterministic parsability/section metrics.
- [ ] Implement versioned structured job criteria, xAI OpenAI-compatible structured assessment, optional Gemini fallback, provider routing, and a disabled/manual-review mode.
- [ ] Implement the queue ordering, advance/reject commands, required rejection reason, and audit events.
- [ ] Implement accessible A/R/up/down keyboard behavior matching the design.
- [ ] Verify adapter contract tests, stored evidence, failure states, and Playwright review flows.
- [ ] Run a real Grok smoke test only after confirming funded credits/billing; otherwise demonstrate the deterministic fake plus `DISABLED` manual-review behavior and record the provider prerequisite.
- [ ] Commit as `feat: add explainable resume review scoring`.

## Task 7: Interviews and Scorecards

**Files:**

- Create interview, panelist, rubric snapshot, and scorecard modules/migrations.
- Create candidate Interviews and Scorecards tabs plus interviewer submission UI.

**Interfaces:**

- Produces `/api/v1/interviews`, `/api/v1/interviews/{id}/scorecards`, and completion summaries.
- Enforces peer-score visibility after the viewer submits their own scorecard.

- [ ] Write failing tests for rubric snapshots, one scorecard per panelist, submit locking, peer visibility, administrative reopen audit, and role restrictions.
- [ ] Implement interview and scorecard state machines.
- [ ] Implement structured competency/rating/recommendation forms.
- [ ] Add activity events for interview and scorecard changes.
- [ ] Verify backend authorization, frontend accessibility, and Playwright visibility tests.
- [ ] Commit as `feat: add interviews and structured scorecards`.

## Task 8: Google Calendar Scheduling

**Files:**

- Create calendar connection, availability, hold, external event, webhook, and reconciliation modules.
- Create scheduling day/week grid and interviewer selection UI.

**Interfaces:**

- Implements `CalendarProvider` for Google Calendar.
- Produces `/api/v1/calendar-connections`, `/api/v1/scheduling/availability`, `/api/v1/schedule-holds`, and interview event commands.

- [ ] Write failing tests for token encryption, time-zone conversion, free/busy intersection, expiring holds, conflict recheck, webhook deduplication, token refresh, and event cancellation.
- [ ] Implement least-privilege Google OAuth connection and encrypted token references.
- [ ] Implement asynchronous incremental sync and webhook renewal.
- [ ] Implement scheduling grid, conflict warnings, holds, and send-invite action.
- [ ] Verify WireMock contracts, DST/time-zone cases, Playwright sandbox flow, and one real Google smoke test.
- [ ] Commit as `feat: add Google interview scheduling`.

## Task 9: Offers, Approval Chain, and Email

**Files:**

- Create offer, offer-version, approval-step, document, message, and delivery-event modules/migrations.
- Create offer builder, letter preview/upload, approval-chain, and delivery screens.
- Create SES mail adapter and delivery-event receiver.

**Interfaces:**

- Produces `/api/v1/offers`, `/api/v1/offers/{id}/submit`, `/approvals`, `/documents`, and `/deliver`.
- Implements `MailProvider` and publishes offer/message activity events.

- [ ] Write failing tests for ordered approvals, unauthorized approval, rejection, version reset after material edits/document replacement, PDF authorization, duplicate send prevention, and delivery/bounce events.
- [ ] Implement the versioned offer state machine and approval notifications.
- [ ] Implement structured terms, PDF preview/upload, private storage, and approved-version delivery guard.
- [ ] Implement SES sending, configuration-set events, and candidate timeline messages.
- [ ] Verify unit/integration/adapter tests and a Playwright multi-role approval flow.
- [ ] Commit as `feat: add offer approvals and delivery`.

## Task 10: Search, Notifications, and Reports

**Files:**

- Create PostgreSQL search indexes/queries, notification module, report read models, and report endpoints.
- Create Cmd+K palette, notification center, and Reports screen.

**Interfaces:**

- Produces `/api/v1/search`, `/api/v1/notifications`, and `/api/v1/reports/*`.
- Consumes immutable stage, interview, offer, message, and source history.

- [ ] Write failing tests for tenant/role-filtered search, notification idempotency/read state, metric definitions, date boundaries, and department/job filters.
- [ ] Implement full-text/trigram search without OpenSearch.
- [ ] Implement notification producers for assignments, reviews, interviews, scorecards, approvals, and import failures.
- [ ] Implement time-to-hire, acceptance, active-candidate, funnel, source, and interview-volume queries.
- [ ] Implement PDF-faithful Cmd+K and Reports screens.
- [ ] Verify metric fixtures, query plans, component tests, and Playwright search/report flows.
- [ ] Commit as `feat: add ATS search notifications and reports`.

## Task 11: Retention, Audit, and Production Security

**Files:**

- Create retention-policy, purge-job, privacy export/delete, rate-limit, and security configuration.
- Create administrative retention and audit views.

**Interfaces:**

- Produces admin-only privacy export/delete commands and scheduled retention events.
- Default rejected/withdrawn retention is two years; purging preserves only non-identifying aggregates and required security evidence.

- [ ] Write failing tests for purge eligibility, S3 deletion, anonymized reports, protected audit records, rate limits, file authorization, CSP/CORS, and log redaction.
- [ ] Implement scheduled retention and legal-hold-safe deletion boundaries.
- [ ] Add structured audit events for security-sensitive commands.
- [ ] Add dependency, container, secret, and IaC scanning configurations.
- [ ] Verify tenant isolation, OWASP-oriented API tests, backup/restore runbook, and privacy workflows.
- [ ] Commit as `feat: harden ATS security and retention`.

## Task 12: Terraform and First AWS Environment

**Files:**

- Create Terraform bootstrap, network, data, identity, storage, messaging, compute, delivery, and observability modules.
- Create environment variables, outputs, plan workflow, and operator guide.

**Interfaces:**

- Produces CloudFront URL, Cognito identifiers, ALB/ECS services, Supabase project/connection references, buckets, queues, ECR repositories, SES identities, and monitoring outputs.
- Uses encrypted S3 remote state with locking; no state or credentials enter Git.

- [ ] Create static Terraform checks before resources: formatting, validation, lint, policy/security scan, and module tests.
- [ ] Implement the state bootstrap in `ap-south-1`.
- [ ] Implement two-AZ VPC, private ECS subnets, public ALB/NAT, and security-group references.
- [ ] Configure or import the Supabase project through the official Terraform provider; store its TLS session-pooler connection secret in Secrets Manager and keep project/account credentials out of Git.
- [ ] Implement encrypted S3/SQS/Secrets, Cognito, SES, ECR, ECS Fargate, ALB, CloudFront, logs, alarms, and CloudTrail.
- [ ] Run a reviewed target-account plan and record expected recurring-cost resources.
- [ ] Manually apply, run database migrations as a one-off ECS task, deploy immutable artifacts, and execute cloud smoke tests.
- [ ] Add GitHub OIDC deployment roles only after manual deployment is stable.
- [ ] Commit as `infra: provision Talon ATS AWS environment`.

## Task 13: Final Verification and Operational Handoff

**Files:**

- Complete architecture, API, security, Terraform, deployment, incident, backup/restore, queue replay, and local-development documentation.
- Add release checklist and environment inventory.

**Interfaces:**

- Produces a versioned release candidate and evidence bundle from the same Git commit.

- [ ] Run all frontend, backend, architecture, integration, Playwright, Docker, Terraform, and security checks from a clean checkout.
- [ ] Run a 25-concurrent-user workload and a 2,000-row import; record p95 application latency and import duration.
- [ ] Restore the production-tier Supabase backup to a temporary project/database and verify record counts/checksums; for the Free demo tier, verify a logical export/import while documenting that it is not a production backup substitute.
- [ ] Exercise an SQS DLQ replay and confirm idempotent recovery.
- [ ] Execute the AWS user journey from login through job, candidate, review, interview, offer, and reports.
- [ ] Verify every functional requirement maps to a passing acceptance test or documented external prerequisite.
- [ ] Tag the verified commit and publish the demo/runbook links.
- [ ] Commit as `docs: finalize ATS production handoff`.

## Initial 10-Hour Demonstration Checklist

- [ ] Repository, local PostgreSQL, frontend, backend, migrations, and Docker builds work.
- [ ] Cognito/local identity boundary and workspace onboarding work.
- [ ] Talon dashboard shell matches the supplied design direction.
- [ ] A recruiter creates and publishes a job through the wizard.
- [ ] A recruiter adds a candidate and moves the application on the Kanban board.
- [ ] The database records tenant-scoped job, application, transition, activity, and audit data.
- [ ] One Playwright flow proves sign-in/onboarding/job/candidate/pipeline behavior.
- [ ] Terraform formatting, validation, security scan, and target-account plan run.
- [ ] The Supabase project is reachable from the ECS deployment through TLS Supavisor session pooling, with its Free-to-production upgrade gate recorded.
- [ ] If AWS credentials and OAuth configuration are ready, the same vertical slice is smoke-tested on AWS.

## Definition of Done

A task is complete only when its tests passed in the current working tree, generated API/database artifacts are consistent, tenant and role checks are present, documentation reflects the implemented behavior, and the task's independently demonstrable workflow has been exercised. A successful local test does not substitute for required provider or AWS smoke tests.
