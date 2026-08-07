# Deployment and Testing Strategy

## 1. Delivery philosophy

Develop locally, verify continuously, and deploy coherent vertical slices. Do not build a full continuous-deployment system before the first working product slice, but do not postpone all AWS integration until every feature is complete.

The first milestone proves one path through browser, authentication, API, database, audit history, tests, Docker, and Terraform. Later increments reuse those boundaries.

## 2. Local development

Docker Compose provides PostgreSQL and a mail catcher. Frontend and backend may run on the host for fast reload or in containers for parity. Local provider interfaces use deterministic fakes for Cognito, xAI Grok, optional Gemini, Google Calendar, S3, SQS, and SES where a real sandbox would make tests slow or flaky.

Real provider smoke tests run separately and never replace deterministic tests.

The Grok consumer Free plan does not provide free production API usage. A real xAI smoke test requires an API team with usable prepaid credits or invoiced billing. Because xAI currently documents limitations with Indian payment cards, billing readiness is checked before the demo; otherwise `AI_PROVIDER=DISABLED` and the deterministic adapter keep the review workflow demonstrable.

Expected developer commands after scaffolding:

```text
docker compose up -d postgres mail
./mvnw -f apps/api/pom.xml spring-boot:run
npm --prefix apps/web run dev
./mvnw -f apps/api/pom.xml test
npm --prefix apps/web test
npm --prefix apps/web run build
npx playwright test
```

## 3. Test layers

### Backend unit tests

Test aggregates, policies, command handlers, permissions, state transitions, metric calculations, AI schema validation, time-zone logic, and retention decisions without network or database.

### Module architecture tests

Spring Modulith/ArchUnit verifies module visibility and dependency direction. Tests fail if one feature accesses another feature's repository or persistence entity directly.

### Database integration tests

Testcontainers PostgreSQL runs Flyway migrations and repository/service tests. Coverage includes RLS, constraints, indexes, search, concurrency, outbox atomicity, reports, and migration from the previous schema.

### Adapter contract tests

WireMock or provider sandboxes verify Cognito metadata/JWT expectations, xAI OpenAI-compatible structured-output handling, optional Gemini fallback handling, Google Calendar free/busy/events/webhooks, and SES delivery events. Provider fixtures are sanitized and committed.

### Frontend tests

Vitest, Testing Library, and MSW verify routing, forms, keyboard behavior, accessibility, loading/empty/error states, role visibility, filters, drag alternatives, and conflict recovery.

### Playwright tests

Core browser flows run against deterministic local adapters. A smaller smoke project targets the AWS environment with dedicated test users and isolated workspace data.

### Infrastructure tests

Terraform formatting, validation, linting, policy/security scans, plan review, container scans, and post-deploy smoke checks are required.

## 4. Playwright acceptance suite

1. Sign up, enroll TOTP, create workspace, invite staff, and enforce roles.
2. Create a job through all wizard steps and publish it.
3. Add a candidate and move the application through the Kanban with audit history.
4. Detect an optimistic conflict between two browser sessions.
5. Import CSV/ZIP, preview invalid/duplicate rows, confirm, and download errors.
6. Show a resume assessment, use keyboard triage, and prove no automatic rejection.
7. Create interviews, submit scorecards, and enforce peer visibility.
8. Find availability, detect a conflict, hold a slot, and create/cancel a calendar event.
9. Build an offer, upload PDF, complete ordered approvals, reset after edit, and send once.
10. Search with Cmd+K, read notifications, and filter reports.
11. Prove cross-workspace IDs and unauthorized compensation/file access fail.
12. Purge an eligible candidate while preserving anonymized report totals and audit evidence.

## 5. First approximately 10-hour vertical slice

This is a milestone, not the full ATS. It must demonstrate:

- Working frontend/backend/PostgreSQL baseline, with local PostgreSQL and the same Flyway migrations used against Supabase.
- Authentication boundary and workspace onboarding.
- Faithful shared Talon layout.
- Job creation/publish.
- Candidate creation/application.
- One valid and one conflicting Kanban transition.
- Persisted activity/audit history.
- One Playwright happy path.
- Docker builds and Terraform validate/plan.
- AWS smoke deployment when account/OAuth inputs are available.

No static placeholder screen counts as a completed workflow. The slice is valuable because later imports, review, scheduling, offers, and reporting attach to already-tested identities, tenants, APIs, migrations, deployment artifacts, and UI patterns.

## 6. Performance targets

- 25 concurrently active staff.
- Import acceptance of 2,000 rows without holding an HTTP request open for processing.
- Normal indexed read p95 under 500 ms at the target dataset.
- Normal command p95 under 1 second, excluding asynchronous provider work.
- Cmd+K first page under 500 ms for the target dataset.
- No unbounded list, file read, archive extraction, or provider concurrency.

Performance fixtures include multiple tenants, 100 jobs, tens of thousands of applications, stage history, interviews, messages, and a 2,000-row import. Report queries are verified with `EXPLAIN (ANALYZE, BUFFERS)` on representative data.

## 7. Manual-first AWS deployment

1. Produce clean frontend and backend builds from one commit.
2. Build/tag/push Spring image using commit SHA.
3. Upload immutable frontend assets to a release prefix.
4. Run Terraform checks and review the target-account plan.
5. Apply the platform stack manually, creating or importing the Supabase project as the reviewed plan specifies.
6. Store the TLS session-pooler connection secret in Secrets Manager and run Flyway as an ECS one-off task.
7. Deploy API/worker task definition using the immutable image.
8. Publish frontend assets and switch/invalidate CloudFront.
9. Run health, auth, data, queue, file, email, calendar, and AI smoke checks relevant to the release.
10. Record deployed commit, Terraform state version, migration version, task definition, and smoke evidence.

After this is repeatable, encode steps 2–10 in GitHub Actions using OIDC and protected environment approval.

## 8. Deployment safety

- Use rolling ECS deployment with ALB health gating.
- Apply backward-compatible schema changes before code that requires them.
- Run destructive schema contraction only after the previous code is no longer deployed.
- Do not use mutable `latest` image tags for release identity.
- Frontend assets are content hashed; HTML release switching is reversible.
- Rollback reuses the previous task definition/frontend release only when schema compatibility permits.
- Provider feature flags may disable a failing integration while preserving core candidate review.
- Supabase Free is permitted only for development/demo. A production apply is blocked until the project uses a non-pausing tier with automated backups/PITR and the restore exercise has passed.

## 9. CI stages

Lightweight CI begins with the repository:

1. Formatting and linting.
2. Frontend typecheck/unit/build.
3. Backend unit/module/integration/package.
4. OpenAPI generation and breaking-change check.
5. Docker build and vulnerability/SBOM scan.
6. Terraform format/validate/lint/security checks.
7. Playwright local core project.

Deployment jobs are added only after manual cloud deployment is stable. Production apply/deploy requires protected environment approval.

## 10. Test data and cleanup

- Factories create tenant-scoped deterministic fixtures.
- E2E creates a unique workspace per run and deletes or expires it through an administrative test cleanup path.
- Provider sandboxes use dedicated calendars/senders/test recipients.
- Logs and screenshots redact candidate contact data and tokens.
- CI never uses production resumes or real candidate PII.

## 11. Operational exercises before handoff

- Restore the paid-tier Supabase backup to a separate project/database and compare expected data; on Free, exercise logical export/import only and label it non-production evidence.
- Replay a DLQ message and prove idempotency.
- Rotate xAI/optional Gemini/Google credentials and restart tasks safely.
- Revoke a calendar connection and surface reconnect state.
- Simulate SES bounce/complaint.
- Set `AI_PROVIDER=DISABLED` and prove manual review remains available.
- Scale worker from queue depth and observe recovery.
- Execute candidate export/purge.
- Review CloudWatch alarms and the incident escalation information supplied by the company.

## 12. Release evidence

A release record contains commit SHA, build/test summaries, OpenAPI version, Flyway version, Terraform plan/apply reference, image digest, frontend release identifier, security scan summaries, provider smoke results, known residual risks, and rollback instructions.
