# Priority Candidate Import, Export, and Search Design

- Status: Approved in design discussion; pending written-spec review
- Date: 2026-08-07
- Governing priorities: candidate application CSV import/export and dual-mode candidate search
- Delivery model: focused Spring Boot modular monolith with React/Vite frontend

## 1. Objective

Deliver a complete demonstrable flow in which an administrator signs in, selects a job, imports Google Form application submissions from CSV, copies required public Google Drive PDF resumes into private object storage, reviews candidate/application records, searches them with deterministic filters or Grok-translated natural language, and exports filtered application data as CSV.

The design preserves AWS account and provider portability through application-owned ports and Terraform. It does not require AWS during ordinary local development and does not expose candidate files through a public S3 bucket.

## 2. Priority order

1. Minimal application-owned authentication and tenant authorization.
2. Minimal jobs, candidate profiles, and job applications required by imports.
3. Candidate application CSV import with required Drive resume ingestion.
4. Candidate/application list and profile projections.
5. Candidate/application CSV export.
6. Standard keyword/filter/sort search.
7. Grok natural-language translation into the same validated search DSL.
8. Terraform-backed private S3 and SQS adapters using the same application behavior.

## 3. Explicitly deferred scope

The following features remain product possibilities but are not part of the priority implementation path:

- Public sign-up, invitations, Google OAuth, Cognito, TOTP, 2FA, password reset, and member administration.
- Calendar synchronization, scheduling grids, interviews, and scorecards.
- Offers, approvals, letter generation, and offer delivery.
- Reports, notification center, email integration, and advanced Kanban editing.
- AI resume scoring, automatic ranking, embeddings, vector databases, and OpenSearch.
- Authenticated/private Google Drive integration.
- ZIP imports, archive extraction, and bulk resume bundles.
- Resume files or temporary resume URLs inside exports.

Deferred modules must not block or expand the priority flow. Existing architecture material will mark them deferred rather than present them as immediate delivery tasks.

## 4. Governing architecture

The backend remains one Spring Boot modular monolith. Each module owns its domain, application services, persistence adapters, and public facade.

```text
identity     application-owned login, JWTs, refresh sessions, request principal
jobs         minimal import-target job records and lookup
candidates   candidate profiles, job applications, form answers, projections
imports      upload, mapping, validation, preview, confirmation, row processing
files        source fetch, quarantine, scan, extraction, private object storage
exports      filtered CSV jobs and authorized artifact delivery
search       keyword search, DSL validation/compilation, Grok translation
platform     clocks, IDs, queue, rate limiting, JWT, storage/provider adapters
```

Allowed dependency direction remains API to application to domain. Infrastructure implements application-owned ports. Provider SDK and wire types do not enter domain types.

PostgreSQL is authoritative for job state, idempotency, tenant ownership, search fields, and durable processing status. Queue messages are wake-up signals; they are not the only record of import/export state.

## 5. Minimal authentication and authorization

### 5.1 Supported flow

The priority release has one pre-provisioned Workspace Admin demo account and a real sign-in flow. Public account creation is unavailable.

- Email lookup uses a normalized lowercase email.
- BCrypt password hashes are stored in PostgreSQL.
- Access JWT lifetime is 15 minutes.
- Access JWTs are signed with a secret supplied through environment configuration locally and AWS Secrets Manager on AWS.
- Access JWTs are returned to the frontend and retained in memory, not local storage.
- Refresh tokens are opaque random values with a seven-day lifetime.
- Only refresh-token hashes are stored in PostgreSQL.
- Refresh tokens use `HttpOnly`, `Secure` in deployed environments, and `SameSite=Strict` cookies.
- Logout revokes the refresh session.
- Raw passwords, access tokens, refresh tokens, and signed URLs are never logged.

The demo administrator is provisioned only when `DEMO_BOOTSTRAP_ENABLED=true`. Email and BCrypt hash enter through uncommitted local environment values or Secrets Manager. No password or reusable token is committed or embedded in frontend JavaScript.

### 5.2 Roles

The schema retains fixed roles `WORKSPACE_ADMIN`, `RECRUITER`, `HIRING_MANAGER`, and `INTERVIEWER`, but the first demo provisions only an administrator.

Priority import, export, natural-language search, and compensation fields require Admin or Recruiter. Backend authorization is authoritative; frontend visibility is only a usability aid.

### 5.3 Identity model change

The current Cognito-specific subject in the initial identity scaffold is replaced by application-owned credentials before an HTTP authentication endpoint is exposed. Cognito adapters and Cognito-specific claims are not retained in the priority domain model.

## 6. Candidate and application model

One candidate exists per normalized email within a workspace. One candidate can have multiple job applications, with at most one application per job.

### 6.1 Candidate profile fields

- First name and last name.
- Normalized email and display email.
- Phone.
- Location.
- Total experience in months.
- Current company and current title.
- Normalized skills.
- Created and updated timestamps.
- Optimistic version.

### 6.2 Application fields

- Workspace, candidate, and selected job identifiers.
- Pipeline status/stage required by the minimal job model.
- Source and application date.
- Current annual compensation amount in minor currency units.
- Expected annual compensation amount in minor currency units.
- ISO currency.
- Notice period in days.
- Availability date.
- Structured additional form answers in `jsonb`.
- Resume file identifier and resume-processing status.
- Created/updated timestamps and optimistic version.

Compensation belongs to an application because it can change between roles and over time. `LPA` always means annual INR. For example, 40 LPA is INR 4,000,000 per year and is stored as `400000000` paise. Cross-currency comparison and exchange-rate conversion are excluded.

## 7. CSV import contract

### 7.1 Input constraints

- Maximum 2,000 data rows.
- Maximum CSV file size 10 MB.
- UTF-8 with an optional byte-order mark.
- Comma-delimited RFC 4180-compatible parsing.
- A target job is selected before upload.
- A required public Google Drive PDF resume link exists on every row.
- Repeated confirmation uses an idempotency key.

### 7.2 Canonical columns

Required mappings:

- `first_name`
- `last_name`
- `email`
- `resume_drive_url`

Supported canonical mappings:

- `phone`
- `location`
- `total_experience_years`
- `current_company`
- `current_title`
- `skills`
- `current_ctc`
- `expected_ctc`
- `ctc_unit`
- `ctc_currency`
- `notice_period_days`
- `availability_date`
- `source`
- `application_date`

The mapping UI supports arbitrary Google Form headers. Unmapped optional columns can be retained as additional form answers after the user explicitly selects that behavior. Required fields cannot be assigned twice, and one source column cannot map to multiple canonical fields.

`ctc_unit` initially accepts `LPA` or `ANNUAL`. `LPA` requires INR. `ANNUAL` uses the supplied ISO currency and whole currency units, which are normalized to minor units.

### 7.3 Import state machine

```text
UPLOADED
  -> MAPPED
  -> VALIDATING
  -> PREVIEW_READY
  -> CONFIRMED
  -> PROCESSING
  -> COMPLETED | COMPLETED_WITH_ERRORS | FAILED | CANCELLED
```

Row states are:

```text
PENDING
  -> VALIDATED
  -> FETCHING_RESUME
  -> RESUME_QUARANTINED
  -> APPLICATION_CREATED
  -> SCAN_PENDING
  -> EXTRACTING_TEXT
  -> COMPLETED
```

Terminal row alternatives include `INVALID`, `DUPLICATE_APPLICATION`, `SOURCE_AUTH_REQUIRED`, `RESUME_FETCH_FAILED`, `UNSAFE_FILE`, `PERSISTENCE_FAILED`, and `CANCELLED`.

### 7.4 Processing behavior

1. Upload stores import metadata and the source CSV privately.
2. Mapping validation checks missing, duplicate, and incompatible mappings.
3. Validation parses all rows and produces valid, invalid, and duplicate previews.
4. Confirmation atomically changes the job to `CONFIRMED` and records an outbox/queue request.
5. Workers claim bounded row batches without holding the upload HTTP request.
6. The required resume is downloaded and stored in quarantine before candidate/application creation.
7. A transaction matches or creates the candidate, creates the application, and attaches file metadata.
8. An existing candidate application for the selected job returns `DUPLICATE_APPLICATION` without mutation.
9. Scanning promotes safe objects from quarantine to the clean prefix.
10. Bounded PDF text extraction updates the search document.
11. Progress counts derive from durable row states.

If storage succeeds but the database transaction fails, the object remains in quarantine and reconciliation/lifecycle removes it. Retrying a row reuses idempotency identifiers and cannot create a duplicate application.

## 8. Public Google Drive source adapter

### 8.1 Demo-only boundary

The CSV must reference a Drive PDF configured for anonymous public reading/downloading. Google’s supported Drive API download mechanism requires OAuth; authenticated Drive is deferred. The anonymous adapter is therefore an explicit demo migration source rather than the production long-term integration.

Google Forms file-upload questions copy uploaded files into the form owner’s Drive, which gives the form owner access but does not authenticate the Talon server. The demo therefore makes this explicit precondition: before exporting/importing the response CSV, the form owner configures every referenced PDF with anonymous “anyone with the link” download permission. The implementation assumes that precondition for the demo dataset and still verifies anonymous downloadability row by row.

The adapter:

- Accepts only recognized Google Drive share-link shapes.
- Allows only HTTPS.
- Validates every redirect against an explicit Google-host allowlist.
- Rejects loopback, link-local, private-network, metadata-service, non-Google, and non-HTTPS destinations.
- Does not scrape confirmation/interstitial HTML.
- Returns `SOURCE_AUTH_REQUIRED` when Google requires cookies, login, confirmation, or unavailable permissions.
- Does not store or return the source link in ordinary candidate responses or exports.
- Records a minimized source reference for retry/audit, protected as candidate data.
- Requires the operator to revoke the public Drive permission after successful migration.

Production must use authenticated Drive OAuth or direct private upload because a public Drive resume exposes candidate PII outside Talon.

### 8.2 Download limits

- PDF only.
- Maximum 10 MB enforced while streaming, regardless of `Content-Length`.
- PDF magic bytes and content type are checked.
- Response bodies are streamed into object storage with bounded buffers.
- Connect, response, and total-operation timeouts are bounded.
- Known abusive-file bypass flags are never sent.

### 8.3 Rate limiting and retry

- Leaky-bucket start rate: five downloads per second.
- Bucket capacity: five.
- Maximum in-flight Drive downloads: five.
- Initial local and AWS worker replica count: one.
- HTTP 429 honors `Retry-After`.
- Transient retry uses bounded exponential backoff with full jitter.
- Permanent 4xx, authentication, file-type, and size failures are not blindly retried.
- Rate, burst, concurrency, retry count, and timeout configuration enter through environment variables and Terraform.

If worker replicas later increase, Terraform divides the provider-wide budget across replicas or the rate-limiter port receives a distributed implementation.

## 9. Private file storage and scanning

The application owns an `ObjectStorage` port. Local development uses a filesystem or compatible local adapter; AWS uses S3. Domain/application code never depends on S3 SDK types.

### 9.1 Object key structure

Object keys use opaque identifiers and no names, emails, or original Drive URL:

```text
quarantine/{workspaceId}/resumes/{fileId}/{version}.pdf
clean/{workspaceId}/resumes/{fileId}/{version}.pdf
imports/{workspaceId}/{importId}/source.csv
exports/{workspaceId}/{exportId}/candidates.csv
```

### 9.2 S3 controls

Terraform enforces:

- All four bucket-level S3 Block Public Access settings.
- Bucket-owner-enforced object ownership with ACLs disabled.
- No public website configuration or anonymous principal.
- Encryption at rest and denial of non-TLS requests.
- Separate least-privilege API and worker IAM roles.
- No application-wide unrestricted bucket listing.
- Versioning and lifecycle cleanup appropriate to object category.
- Private quarantine and clean prefixes.

The backend checks authentication, workspace, role/resource visibility, object ownership, and `CLEAN` scan status before generating a download URL. Quarantine objects never receive download URLs.

Presigned downloads are GET-only, target one exact object, expire after five minutes, and are treated as bearer secrets. They are not logged, persisted in export data, or returned before authorization. A bucket policy restricts excessive signature age.

### 9.3 Scanning and extraction

A `FileScanner` port controls quarantine promotion. The portable scanner adapter uses ClamAV-compatible scanning in local and container deployments. Scanner failure leaves the object quarantined.

After a clean verdict, a `ResumeTextExtractor` port uses a PDFBox adapter with:

- Maximum 50 pages.
- Maximum 500,000 extracted characters.
- Ten-second processing timeout.
- Maximum extraction concurrency of two.
- No outbound network access.

Extracted text supports PostgreSQL search only. AI scoring remains deferred.

## 10. Durable local and AWS processing

The queue is an application-owned port.

- Local adapter: an in-process worker polls/claims durable PostgreSQL jobs in bounded batches.
- AWS adapter: SQS messages wake the same import/export application handlers.
- Database state and idempotency keys remain authoritative.
- SQS redelivery is safe because handlers claim/check durable state before effects.
- Poison messages enter a DLQ after bounded retries.
- The local adapter and SQS adapter pass the same contract tests.

This permits deployment into a different AWS account or region without behavior changes.

## 11. Candidate CSV export

Admins and Recruiters can create an asynchronous export from the current validated candidate/application search filters.

- The worker streams rows directly to the export writer/object storage.
- Export does not load the complete result set into memory.
- Resume binaries, Drive URLs, S3 keys, and presigned URLs are excluded.
- Compensation columns appear only for authorized roles.
- Cells beginning with spreadsheet formula characters are escaped.
- The generated CSV is stored through `ObjectStorage`.
- An authorized download endpoint creates a five-minute presigned URL or redirects to it.
- Export artifacts expire after seven days.
- Export status and row count remain in PostgreSQL after artifact expiry for audit/operational visibility.
- Additional future formats implement an export-writer port without changing authorization or storage.

## 12. Search architecture

### 12.1 One validated DSL

Standard filters and Grok interpretations use the same backend-owned DSL.

Allowed initial fields:

- `NAME`
- `LOCATION`
- `CURRENT_COMPANY`
- `CURRENT_TITLE`
- `SKILLS`
- `RESUME_TEXT`
- `TOTAL_EXPERIENCE_MONTHS`
- `JOB_ID`
- `JOB_TITLE`
- `PIPELINE_STAGE`
- `SOURCE`
- `APPLICATION_DATE`
- `CURRENT_ANNUAL_COMPENSATION`
- `EXPECTED_ANNUAL_COMPENSATION`
- `NOTICE_PERIOD_DAYS`
- `AVAILABILITY_DATE`

Allowed operators:

- `EQUALS`
- `NOT_EQUALS`
- `LT`
- `LTE`
- `GT`
- `GTE`
- `IN`
- `CONTAINS`
- `BETWEEN`
- `IS_EMPTY`
- `IS_NOT_EMPTY`

Allowed sorts:

- Relevance.
- Application date.
- Candidate name.
- Total experience.
- Expected annual compensation for authorized users.

Every sort includes a deterministic identifier tie-breaker for seek pagination.

### 12.2 Standard search

Cmd+K does not call Grok. It searches candidates and jobs using keyword, full-text, and trigram matching plus explicit filter/sort controls.

### 12.3 Natural-language interpretation

`POST /api/v1/search/interpret` sends only the user’s search sentence and the allowed DSL schema to Grok. Candidate data, resumes, compensation records, and tenant details are not included.

Grok must return schema-conforming JSON containing keywords, filters, sort, confidence, and display-safe warnings. The backend then:

1. Deserializes with unknown fields rejected.
2. Validates field/operator/value compatibility.
3. Normalizes LPA and date expressions.
4. Applies role restrictions.
5. Enforces workspace scope, bounds, and page size.
6. Compiles only allowlisted constructs into parameterized SQL.

Grok never emits or controls SQL, table names, column names, joins, or tenant identifiers.

Natural query maximum length is 500 characters. Interpretation timeout is three seconds. The initial per-user limit is ten interpretations per minute. Raw queries are not written to ordinary application logs.

If Grok is disabled, unavailable, times out, or returns invalid output, the standard keyword/filter search remains available. The frontend shows the failure and offers the original text as a normal keyword search; it never silently executes a different interpretation.

### 12.4 PostgreSQL execution

An allowlisted compiler uses parameterized Spring JDBC queries. Search includes workspace-first predicates and PostgreSQL RLS.

- `tsvector` indexes cover candidate/application/resume search documents.
- `pg_trgm` indexes support names, emails, job titles, companies, and typo tolerance.
- Structured filters use composite/partial indexes beginning with `workspace_id`.
- Cursor/seek pagination avoids unbounded offsets.
- Representative query-plan tests use `EXPLAIN (ANALYZE, BUFFERS)`.
- No OpenSearch, embeddings, vector extension, or generated SQL from an AI provider.

## 13. Priority API surface

Authentication:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/session`

Jobs:

- `GET /api/v1/jobs`

Imports:

- `POST /api/v1/imports` with target job and CSV
- `PUT /api/v1/imports/{importId}/mapping`
- `POST /api/v1/imports/{importId}/validate`
- `GET /api/v1/imports/{importId}/preview`
- `POST /api/v1/imports/{importId}/confirm`
- `GET /api/v1/imports/{importId}`
- `GET /api/v1/imports/{importId}/rows`
- `POST /api/v1/imports/{importId}/rows/{rowNumber}/retry`
- `GET /api/v1/imports/{importId}/errors.csv`

Candidates/applications:

- `GET /api/v1/candidates`
- `GET /api/v1/candidates/{candidateId}`
- `GET /api/v1/applications/{applicationId}`
- `GET /api/v1/files/{fileId}/download`

Exports:

- `POST /api/v1/exports/candidates`
- `GET /api/v1/exports/{exportId}`
- `GET /api/v1/exports/{exportId}/download`

Search:

- `GET /api/v1/search` for Cmd+K keyword results
- `POST /api/v1/search/interpret`
- `POST /api/v1/candidate-search` for validated DSL execution

All failures use `application/problem+json` with stable codes, a correlation ID, and field/row details where safe.

## 14. Frontend priority workstream

The frontend implementation is restricted to:

```text
features/
  auth/
  jobs/
  imports/
  candidates/
  exports/
  search/
```

### 14.1 Sign-in

The sign-in screen follows the supplied PDF design direction and includes email, password, password visibility, loading, generic invalid credentials, rate-limited/locked, unavailable, expired-session, restoration, logout, and protected-route behavior.

It contains no sign-up, OAuth, TOTP, password reset, or hardcoded credentials.

### 14.2 Shell and job selection

The PDF-directed sidebar/header exposes Candidates, Import, and Search as primary destinations. A minimal Jobs selector scopes every import. Deferred destinations are hidden or clearly unavailable rather than represented as completed workflows.

### 14.3 Import wizard

```text
Select job
  -> Upload/template
  -> Map columns
  -> Validate/preview
  -> Confirm
  -> Progress
  -> Results
```

The UI explains canonical fields and LPA normalization, requires the resume mapping, shows valid/invalid/duplicate counts, preserves progress across refresh, exposes safe row errors/retry, displays fetch/quarantine/scan/extraction states, and downloads error CSV.

### 14.4 Candidate/application and export screens

Candidate lists show job, stage, experience, CTC for authorized roles, notice period, and resume status. Candidate profiles show the basic profile, application, normalized form fields, additional answers, and file-processing state.

Export UI creates filtered jobs, shows progress/expiry, and performs authorized download without exposing resume links.

### 14.5 Dual search UI

- Cmd+K performs keyword candidate/job search and navigation.
- Candidate Search accepts natural language.
- Interpretations appear as editable/removable filter chips with warnings.
- Explicit filters and sorting work without Grok.
- Loading, empty, invalid interpretation, provider unavailable, forbidden compensation, and retry states are implemented.
- Shareable deterministic filters/sorts use URL parameters; the raw natural-language sentence does not.

Frontend fixtures implement the same typed gateway interfaces as the generated API client. The frontend workstream records contract requests rather than inventing backend fields.

## 15. Terraform and deployment portability

Terraform is the only supported AWS resource-creation path.

- Account ID, region, bucket names, queue names, domains, and ARNs are variables or resource outputs.
- State bootstrap is separate from the application stack.
- Private S3, SQS/DLQ, IAM, encryption, secrets, logs, alarms, and container runtime are modules.
- Runtime code selects queue/storage adapters through configuration.
- The same immutable application images deploy to a different AWS account without recompilation.
- The initial worker desired count is one to preserve the configured Drive rate budget.
- Scaling worker count requires explicit provider-budget calculation in Terraform variables.
- Supabase PostgreSQL remains accessed through portable JDBC/Flyway contracts; no Supabase Storage or Auth coupling is introduced.

## 16. Error handling and recovery

- Import-level validation failure does not enqueue processing.
- Row-level failures do not abort unrelated valid rows.
- Duplicate application results are explicit and non-destructive.
- Provider failures retain durable retry state and safe reason codes.
- Idempotency protects confirmation, row retry, file persistence, and export creation.
- SQS/local redelivery cannot repeat completed effects.
- Scanner or extractor failure never makes an unsafe file downloadable.
- Search interpretation failure never disables deterministic search.
- Unauthorized compensation search/export fails before SQL execution.
- Cross-workspace identifiers return not found without revealing existence.

## 17. Verification strategy

### 17.1 Unit and domain tests

- Login/password verification, JWT/refresh lifetime, revocation, and role policy.
- CSV normalization, header mapping, LPA conversion, formula escaping, and DSL validation.
- Import/export state machines and idempotency.
- Drive link allowlist, redirect validation, size cutoff, PDF signature, rate limiter, and retry classification.
- Search field/operator compatibility and compensation authorization.

### 17.2 PostgreSQL/Testcontainers tests

- Flyway migrations, uniqueness, constraints, tenant RLS, and transaction rollback.
- Candidate matching and duplicate application handling.
- Durable job claiming/redelivery.
- Full-text/trigram result quality and tenant/role filtering.
- Query plans on representative fixtures.

### 17.3 Adapter contract tests

- Local and S3 object storage.
- Local and SQS queue adapters.
- Public Drive source fixtures for success, redirect, auth interstitial, 429, oversized, and spoofed files.
- ClamAV-compatible scanner and PDFBox extractor.
- Grok valid/invalid/timeout/quota interpretations.

### 17.4 Frontend tests

- Sign-in and protected routes.
- Import mapping, validation, duplicates, progress, retry, and results.
- Candidate/application projections and compensation restrictions.
- Export lifecycle.
- Cmd+K keyboard behavior.
- Natural interpretation chips, filter editing, fallback, and error states.
- Accessibility and responsive behavior.

### 17.5 First Playwright journey

```text
sign in
  -> select job
  -> upload/map/confirm CSV
  -> wait for durable completion
  -> inspect candidate/application
  -> run keyword search
  -> run natural-language CTC search
  -> export filtered CSV
  -> download authorized artifact
```

## 18. Observability and operational evidence

- Import/export job duration, row throughput, duplicate count, failure codes, and retry count.
- Drive response category, throttle delay, 429 count, and download duration without source URLs.
- Quarantine age, scan failures, extraction failures, and cleanup count.
- Search latency, result count, DSL validation failure, Grok timeout/quota failure, and deterministic fallback use.
- SQS depth, oldest-message age, retry count, and DLQ messages.
- Authentication failures and authorization denials without passwords/tokens.

## 19. Documentation changes after written approval

The implementation planning step will revise the architecture source of truth to:

- Make import/export and search the first product priorities after minimal authentication.
- Remove ZIP import and archive-related acceptance criteria.
- Replace immediate Cognito/Google/TOTP work with application-owned basic authentication.
- Add ADRs for private object storage/import sources and dual-mode validated search.
- Mark calendar, offers, interviews, reports, notifications, and AI scoring deferred.
- Rewrite the parallel frontend prompt to the six priority feature modules.
- Split implementation into independently demonstrable prerequisite, import/export, and search plans.

## 20. Reference constraints

- AWS S3 Block Public Access guidance: <https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html>
- AWS S3 presigned URL guidance: <https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html>
- Google Drive supported downloads require authorized access: <https://developers.google.com/workspace/drive/api/guides/manage-downloads>

The anonymous public Drive source is accepted only as the explicitly documented demo limitation described in this specification.
