# Priority Deployment and Testing Strategy

## 1. Delivery philosophy

Develop and verify locally before AWS. Commit coherent green checkpoints; do not deploy on every
commit. The first cloud deployment is manual and repeatable through Terraform and immutable
artifacts. CI/CD automation follows only after the same priority journey is stable in AWS.

## 2. Local development

```text
Git working tree
  -> React/Vite web
  -> Spring Boot API + local worker
  -> Docker PostgreSQL (and provider fakes)
  -> unit/integration/Playwright verification
  -> production Docker image build
```

Docker data may remain on the E drive. Java 21 is required. Local providers include private-object,
Drive, scanner, queue, clock, and Grok fakes with contract-compatible behavior. Real credentials
belong in ignored local/runtime environment files.

## 3. Test layers

- Domain/unit: identity, authorization, normalization, money/LPA, mapping, validation, state
  transitions, idempotency, DSL allowlists.
- Module: Spring Modulith dependency verification and application-service slice tests.
- Database: Flyway on real PostgreSQL/Testcontainers, uniqueness/RLS, leasing/replay, query results
  and plans. H2 is not a substitute.
- Adapter contract: JWT/BCrypt, Drive URL/network/download cases, scanner, S3/private signing, local
  dispatcher/SQS, PDFBox, Grok structured output.
- Frontend: Vitest/Testing Library for forms, mapping, progress, chips, sorting, and states.
- E2E: Playwright through public UI/API with deterministic provider fakes; small real-provider smoke
  tests are separate and gated by credentials.
- IaC: Terraform format/validate, lint, policy/security scans, and assertions for S3 public access,
  encryption, IAM, queue/DLQ, secrets, logs, and parameterization.

Behavior changes start with a failing test. Completion claims require fresh observed verification.

## 4. Priority Playwright journey

1. Existing Admin signs in and sees the shared shell.
2. Select an existing job.
3. Download template, upload Google Form CSV, map columns, inspect validation/duplicate preview.
4. Confirm and observe durable progress, row errors, and completion.
5. Open imported candidate/application and obtain an authorized resume download.
6. Use Cmd+K keyword search without Grok.
7. Interpret “candidates with expected CTC below 40 LPA,” inspect/edit chips, execute and sort.
8. Request export, wait for completion, and download the private CSV.

Add focused negative flows for invalid auth, cross-workspace IDs, non-public/oversized/non-PDF
resume, import retry, Grok invalid/unavailable output, and expired/unauthorized downloads.

## 5. Initial vertical-slice gate

“First ten-hour vertical slice” means the thinnest complete path through UI, API, database, and
tests that a reviewer can actually operate; it is not a promise to implement the whole ATS in ten
hours. The current gate is:

- executable web/API with migrations;
- existing Admin login and workspace authorization;
- minimal job selector;
- one CSV preview/confirm/import through deterministic local provider fakes;
- candidate list plus Cmd+K and one NL-to-DSL result;
- one private export result;
- the priority Playwright journey or its earliest complete subset;
- Terraform validates even if AWS credentials are not yet available.

Blocked real-provider checks are recorded, never presented as passing.

## 6. Performance and resilience targets

- Candidate list/search API p95 target under 500 ms for initial realistic data and bounded page.
- Login p95 target under 750 ms excluding deliberate BCrypt cost variability.
- Accept 10 MB/2,000-row CSV without loading resumes/whole artifacts into memory.
- Drive limiter defaults to five starts/sec, capacity five, five in flight; honor `Retry-After`.
- Search limits predicates/page size/query length; Grok has short connect/read timeout and bounded
  retry only for safe transient errors.
- A worker/API restart must not lose confirmed imports/exports or duplicate applications.

## 7. Manual AWS deployment

1. Verify local tests, migrations, web/API builds, Docker images, and Terraform static checks.
2. Review target account/region, cost-bearing resources, names, and Terraform plan.
3. Apply parameterized Terraform; no console-created dependency is treated as source of truth.
4. Push immutable images/web artifact, run Flyway as a one-off task, then roll ECS services.
5. Run health/auth/import/search/export smoke tests with private S3 and SQS.
6. Capture commit/image digests, migration version, plan/apply output, URLs, and observed checks.

Rollback uses the previous immutable app image; database migrations remain forward-only and require
expand/contract compatibility. Object and queue behavior is kept compatible during rollout.

## 8. CI evolution and handoff

Future CI stages: formatting/static checks → unit/module/frontend → PostgreSQL/provider contracts →
build/images → Terraform plan → ephemeral Playwright → manual production approval/deploy → smoke.
Use GitHub OIDC rather than long-lived AWS keys.

Each implementation session updates `docs/implementation/priority-import-export-search.md` with
scope/status, changes, rationale, important paths, files, commands/results, blockers, and exact
next step. Test fixtures use synthetic people and safe PDFs; no candidate PII is committed.
