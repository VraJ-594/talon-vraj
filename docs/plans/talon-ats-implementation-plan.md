# Talon ATS Priority Implementation Plan

> Execution uses `superpowers:executing-plans`, test-first behavior changes, and a living handoff under `docs/implementation/`.

## Goal

Deliver one production-oriented ATS slice that can be demonstrated end to end:

1. an existing Admin signs in;
2. selects a job;
3. previews and confirms a Google Form candidate CSV;
4. required public Google Drive PDF resumes are validated and copied into private S3;
5. imported candidates/applications can be listed, filtered, sorted, searched, and exported; and
6. natural-language search is translated by Grok into a restricted backend-owned DSL.

This slice deliberately favors complete behavior over broad screen coverage. Calendar, interviews,
scorecards, offers, reports, notifications, editable Kanban, AI resume scoring, OAuth, 2FA, public
sign-up, and advanced user administration are deferred. Their future boundaries remain compatible
with the modular monolith and provider-port architecture.

## Architecture constraints

- React/Vite TypeScript SPA and Java 21 Spring Boot modular monolith.
- PostgreSQL through JPA/JDBC and Flyway; Supabase may host it, but application code must remain
  portable and must not use Supabase Auth, Storage, Edge Functions, or Data APIs.
- Application-owned email/password authentication with BCrypt, short-lived JWT access tokens, and
  hashed refresh sessions. Seed one Admin account for the demo.
- Shared-schema tenancy: every business row carries `workspace_id`; authorization is enforced in
  application services and supported by PostgreSQL RLS.
- CSV is the only import format. ZIP import is rejected.
- Every import row has a public, anonymously downloadable Google Drive PDF URL. The source adapter
  accepts PDF only, up to 10 MB, and is guarded against SSRF and redirect abuse.
- Import jobs are durable PostgreSQL records. A local in-process dispatcher and AWS SQS adapter
  implement the same application-owned queue port.
- Candidate files and CSV exports use private object storage. AWS S3 has Block Public Access, ACLs
  disabled, encryption, non-PII keys, least-privilege IAM, and five-minute authorized GET URLs.
- Candidate export files contain no resume URL and expire after seven days.
- Standard Cmd+K search is deterministic and never calls Grok. Natural-language search sends only
  user query plus DSL schema to Grok; the backend validates the returned DSL before PostgreSQL.
- Money is `currency` plus integer minor units. `40 LPA` means INR 4,000,000 annually, represented
  as 400,000,000 paise. No implicit currency conversion occurs.
- Terraform uses variables and provider-owned identifiers so a new AWS account/region can be used
  without application behavior changes.

## Execution order and gates

### Phase 0 — executable baseline

- [x] Frontend and backend projects exist.
- [x] Frontend baseline tests pass.
- [x] Backend domain/module baseline tests pass.
- [ ] Docker Compose PostgreSQL health check passes when Docker is available from this shell.

### Phase 1 — priority foundation

Detailed steps: [priority-foundation](../superpowers/plans/2026-08-07-priority-foundation.md).

- [ ] Refactor identity from provider-subject assumptions to application-owned accounts.
- [ ] Add Flyway identity/workspace schema and seed mechanism that reads credentials from runtime
  configuration, never Git.
- [ ] Implement login, refresh rotation, logout, request principal, Admin/Recruiter policies, and
  RFC 9457 errors.
- [ ] Add the minimum job, candidate, and application model needed by the priority slice.
- [ ] Verify focused tests, full Maven tests, migrations, and module boundaries.

Gate: an existing Admin can authenticate and access only their workspace.

### Phase 2 — CSV import and private export

Detailed steps: [candidate import/export](../superpowers/plans/2026-08-07-candidate-import-export.md).

- [ ] Add import/file/outbox schema and application-owned ports for object storage, resume source,
  malware scanning, queue dispatch, and clock.
- [ ] Implement CSV template, upload, arbitrary column mapping, validation preview, confirmation,
  progress, row errors, duplicate handling, and results for at most 2,000 rows/10 MB.
- [ ] Match candidates by normalized workspace email and create at most one application per
  selected job.
- [ ] Fetch required public Drive PDFs with HTTPS/host/redirect/DNS validation; limit starts to
  five/second, burst five, and five concurrent downloads.
- [ ] Quarantine, verify PDF/max-size, require a clean scan, extract text with PDFBox, then promote
  to private object storage.
- [ ] Implement candidate CSV export as an asynchronous private artifact with seven-day expiry.
- [ ] Verify retry/idempotency, partial-row failure, security, and 2,000-row fixtures.

Gate: the full import and export workflow is replay-safe and never exposes a public object.

### Phase 3 — dual-mode candidate search

Detailed steps: [candidate search](../superpowers/plans/2026-08-07-candidate-search.md).

- [ ] Define one typed search criteria model and allowlisted DSL shared by deterministic filters and
  natural-language interpretation.
- [ ] Implement PostgreSQL full-text/trigram candidate/application/resume-text search, filters,
  cursor pagination, and allowlisted sorting.
- [ ] Implement Cmd+K without an AI call.
- [ ] Implement Grok interpretation behind `NaturalLanguageQueryInterpreter`; send no candidate
  records or SQL and validate every field/operator/value/sort before execution.
- [ ] Return interpreted filter chips that the frontend can edit and resubmit deterministically.
- [ ] Preserve deterministic search when Grok is disabled, unavailable, or returns invalid output.

Gate: `candidates with expected CTC < 40 LPA` produces validated INR criteria and correct results,
while malformed/provider-failed interpretations cannot become SQL.

### Phase 4 — portable AWS environment

- [ ] Terraform private S3/quarantine/export lifecycle, SQS/DLQ, ECS Fargate API/worker, ECR,
  ALB, CloudFront frontend, Secrets Manager, IAM, logs, and alarms.
- [ ] Keep Supabase PostgreSQL connection in Secrets Manager and run Flyway as an ECS one-off task.
- [ ] Validate `terraform fmt`, `validate`, lint/security checks, reviewed target-account plan, and
  manual first deployment before adding deployment automation.
- [ ] Run the same acceptance flow locally and in AWS.

Gate: changing Terraform account/region/bucket names requires configuration only, not code changes.

### Phase 5 — end-to-end verification and handoff

- [ ] Playwright: sign in → select job → upload/map/confirm CSV → inspect candidate → keyword
  search → natural-language CTC search → export.
- [ ] Record backend/frontend/build/IaC commands and observed results.
- [ ] Record external prerequisites: Docker availability, AWS access, Supabase connection, S3/SQS
  resources, malware scanner, and funded xAI key.

## Parallel work

After contracts in the approved design are stable:

- Frontend implements the import/search workflow against typed fixtures and then the API.
- Backend implements domain/schema/API/provider ports test first.
- Terraform implements private storage/queue/runtime without application-specific behavior.
- Playwright fixtures follow the stable API and UI contracts.

The frontend handoff is [frontend-parallel-session.md](../prompts/frontend-parallel-session.md).

## Definition of done

A phase is complete only when its required tests pass in the current tree, migrations and contracts
match implementation, tenant/role checks are present, and the living handoff records commands and
observed results. Provider or AWS work is not called complete until its real smoke test runs.
