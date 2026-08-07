# Talon ATS High-Level Design

## 1. Purpose

Talon is a multi-tenant, internal Applicant Tracking System for recruiting teams. It supports job creation, candidate intake, configurable hiring pipelines, review scoring, interviews, scorecards, calendar scheduling, offers, communication history, search, notifications, and recruiting reports.

The initial system targets approximately 50 concurrently active staff and imports of up to 2,000 candidates while retaining a path to horizontal growth. The architecture favors a small number of managed components, explicit module boundaries, reliable asynchronous processing, and low operational overhead.

## 2. Scope boundaries

### Included

- Workspace sign-up, invitations, Google federation, local credentials, and TOTP.
- Fixed internal roles and tenant isolation.
- Internal job postings grouped by department and a four-step creation wizard.
- Candidate/application model and per-job Kanban pipeline.
- Unified activity, emails, interviews, scorecards, and files.
- CSV plus ZIP imports and audited bulk actions.
- Automated resume parsing and Grok-backed job-fit assessment, with an optional Gemini fallback and manual-review mode.
- Google Calendar free/busy and ATS-owned event synchronization.
- Sequential offer approvals, PDF letter handling, and SES delivery.
- Cmd+K search, in-app notifications, and specified hiring reports.
- Terraform-managed AWS environment and Playwright acceptance tests.

### Excluded

- Public career site or public application form.
- Candidate authentication or self-service portal.
- Gmail mailbox ingestion and Microsoft Calendar.
- Candidate e-signature and HRIS integration.
- Custom permission builders, mobile redesign, Kafka, Kubernetes, Redis, and OpenSearch.

## 3. System context

Recruiters, hiring managers, interviewers, and workspace administrators use a React SPA delivered by CloudFront. Cognito authenticates staff. The SPA calls the Spring Boot REST API, which persists domain state in Supabase-hosted PostgreSQL and emits durable work through an outbox. An SQS worker processes imports, files, AI assessments, email, calendar reconciliation, report maintenance, and retention.

External systems are isolated behind ports:

- Cognito and Google Identity for authentication.
- Google Calendar for scheduling.
- xAI Grok for semantic resume assessment when a funded API key is configured; optional Gemini fallback.
- Supabase for managed PostgreSQL, reached from ECS through TLS session pooling.
- SES for outbound ATS messages.
- S3 for private files and import bundles.

## 4. Logical components

| Component | Responsibility | Scale model |
|---|---|---|
| React SPA | Routes, forms, Kanban, scheduling grid, dashboards, client cache | Static assets via CloudFront |
| Spring API | Authentication context, authorization, commands, queries, OpenAPI | Stateless ECS tasks |
| Spring Worker | SQS consumers, provider calls, retry/reconciliation jobs | Independently scalable ECS tasks |
| Supabase PostgreSQL | Transactions, tenant data, RLS, audit history, search, report queries | Free demo tier; paid compute/backups before production |
| SQS and DLQs | Durable work delivery and failure isolation | Queue depth-based scaling |
| S3 | Resumes, ZIP imports, offer PDFs, exports | Native object scale |
| Cognito | User pool, TOTP, Google federation, tokens | AWS-managed |
| CloudWatch/CloudTrail | Logs, metrics, traces, alarms, platform audit | AWS-managed |

## 5. Primary data flows

### Transactional command

1. SPA sends a JWT-authenticated command with workspace context and optional idempotency/version headers.
2. API verifies the Cognito JWT, resolves active membership, and authorizes the role and resource scope.
3. One PostgreSQL transaction applies domain invariants, appends activity/audit history, and writes an outbox event.
4. The API returns the committed representation and version.
5. The outbox dispatcher publishes the event to SQS and marks the outbox record published.

### Bulk resume intake

1. Recruiter creates an import and uploads CSV/ZIP through presigned S3 URLs.
2. Worker scans and parses the bundle, then writes a validation preview without creating candidates.
3. Recruiter confirms the preview with an idempotency key.
4. Confirmed rows create or match candidates and applications in bounded batches.
5. Resume files are scanned, parsed, and assessed asynchronously; queue progress is visible in the UI.

### Scheduling

1. Recruiter records candidate availability and selects interviewers.
2. API queries cached or live Google free/busy through `CalendarProvider`.
3. A selected slot creates a short-lived hold.
4. Worker/API rechecks conflicts immediately before creating ATS-owned Google events.
5. Webhooks and reconciliation jobs update changes and surface failures.

### Offer approval

1. Recruiter drafts structured terms and uploads or generates an offer document.
2. Submission freezes an offer version and activates the first approval step.
3. Ordered approval commands advance one step at a time and notify the next approver.
4. A material edit or document replacement creates a new version and resets approvals.
5. Final approval permits one idempotent SES delivery; acceptance remains manually recorded.

## 6. Multi-tenancy

Tenant-owned records carry a non-null `workspace_id`. The request principal is resolved from Cognito subject plus active membership. Service methods require workspace-aware identifiers; repository queries include workspace scope; PostgreSQL RLS supplies defense in depth. Global identities never make candidate or job data global.

S3 keys follow `workspaces/{workspaceId}/{category}/{objectId}/{version}/{filename}`. Presigned access is produced only after resource authorization, is short-lived, and never permits prefix listing.

## 7. Reliability model

- API requests do not wait for imports, AI, email, or calendar reconciliation.
- External calls use timeouts, bounded exponential backoff, circuit breaking, and idempotency.
- Each consumer records processing keys so SQS redelivery is safe.
- Poison events move to a named DLQ with operator-visible alarms and replay tooling.
- Optimistic versions prevent silent overwrites of applications, jobs, and offers.
- Immutable transition/activity/audit records allow metric reconstruction and incident review.

## 8. Scalability path

The initial scale does not justify cache or search clusters. PostgreSQL receives composite tenant indexes, full-text/trigram search, bounded pagination, and query-plan tests. API and worker scale independently on request utilization and queue age/depth. If growth later demands extraction, the outbox event contracts and module ports become service boundaries without changing the product model.

The database integration remains provider-portable: JDBC/JPA, Flyway, SQL, and PostgreSQL extensions only. Supabase Auth, Storage, Edge Functions, and generated Data APIs are intentionally excluded. The long-lived ECS services use Supavisor session mode over TLS; pool sizing is bounded across API and worker task counts.

## 9. Observability

All services emit structured JSON containing request/correlation ID, workspace ID, user ID where allowed, module, operation, outcome, latency, and error code. Resume content, email bodies, credentials, tokens, and candidate PII are excluded from logs.

Required dashboards and alarms cover:

- API availability, p95 latency, 4xx/5xx rates, and ECS health.
- Database connections, pool saturation, CPU/storage signals available from Supabase, slow queries, and backup/restore status.
- Queue depth, oldest-message age, retry count, and DLQ messages.
- Import throughput and row failure rates.
- AI/calendar/email provider latency and failure rates.
- Authentication failures and authorization denials.

## 10. Key quality attributes

- **Security:** least privilege, tenant isolation, encryption, auditability, private files, and retention.
- **Maintainability:** domain modules, generated clients, forward migrations, ADRs, and small vertical tasks.
- **Availability:** stateless compute, durable queues, provider failure isolation, and recoverable database backups.
- **Performance:** indexed tenant queries, asynchronous bulk work, cursor pagination, and explicit load targets.
- **Explainability:** versioned AI rubrics/prompts and evidence-bearing assessments without automatic rejection.

Grok's consumer Free plan is not an API entitlement. The production integration requires an xAI API team with usable credits or invoiced billing; provider cost and regional payment support are release prerequisites, not architecture assumptions.
