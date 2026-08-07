# Talon ATS High-Level Design

## 1. Purpose

Talon ATS is a multi-tenant recruiting application. The current delivery slice provides secure
candidate application intake from Google Form CSV data, private resume handling, export, and
deterministic plus natural-language candidate search. The architecture retains clean module and
provider seams for later ATS workflows without implementing them prematurely.

## 2. Active and deferred scope

Active:

- existing Admin/Recruiter application accounts and workspace authorization;
- minimal jobs required to target an import;
- candidates and one application per candidate/job;
- CSV template, mapping, preview, durable import, progress, row errors, duplicates, and results;
- required public Google Drive PDF ingestion into private object storage;
- private candidate CSV export;
- candidate/application list and basic profile;
- Cmd+K keyword search, typed filters/sorting, and Grok-to-validated-DSL interpretation;
- Terraform for portable AWS runtime and Playwright for the priority journey.

Deferred: public sign-up, OAuth, 2FA, invitations, calendar, interviews, scorecards, offers,
reports, notifications, editable Kanban, AI resume scoring, career pages, email sync, and mobile
redesign.

## 3. System context

```text
Recruiter/Admin browser
        |
 CloudFront + private web origin
        |
       ALB
        |
 Spring Boot API / worker profiles
    |          |             |
PostgreSQL  private S3   SQS in AWS
                           |
               Google Drive public-source adapter
                           |
                 malware scanner / PDFBox

Natural-language query -> Grok adapter -> validated DSL -> PostgreSQL
Cmd+K/explicit filters -----------------> typed criteria -> PostgreSQL
```

Supabase may host PostgreSQL. Application code uses ordinary PostgreSQL, Flyway, JPA/JDBC, and a
TLS connection string so another PostgreSQL host can replace it without changing domain behavior.

## 4. Logical components

| Component | Responsibility |
|---|---|
| Web SPA | Sign-in, shell, job selection, import wizard, candidate views, export, and search |
| Identity | Password verification, JWT/refresh lifecycle, request principal, workspace roles |
| Jobs | Minimal job read/create model required by import |
| Candidates | Candidate identity, job applications, compensation/profile data |
| Imports | CSV validation/mapping, durable jobs/rows, idempotent orchestration |
| Files | Quarantine, scan state, PDF extraction, private-object authorization |
| Search | Typed criteria, PostgreSQL query, DSL validation, Grok interpretation |
| Worker | Claims durable work locally or consumes SQS using the same handlers |
| Terraform | Parameterized AWS edge, compute, storage, messaging, secrets, and observability |

Spring Modulith verifies module boundaries. External providers implement application-owned ports;
domain/application packages do not import provider SDKs.

## 5. Primary flows

### Authentication

The API normalizes email, verifies its BCrypt hash, creates a workspace/role principal, returns a
short-lived signed access JWT, and rotates a random refresh token whose hash is stored in
PostgreSQL. Logout revokes the refresh session. Credentials and signing keys come from runtime
secrets.

### Candidate import

The user selects a job, uploads a CSV up to 10 MB/2,000 rows, maps arbitrary Form headings to a
versioned canonical schema, and previews row validation. Confirmation writes a durable import job
and row records. The worker rate-limits public Drive downloads, validates the network target and
PDF, scans quarantine content, extracts searchable text, stores the clean PDF privately, and then
creates/matches the workspace candidate and job application idempotently. A row can fail without
losing successful rows.

### Candidate export

Authorized criteria create a durable export job. The worker streams a CSV into private storage;
resume data/URLs are excluded. An authorized request creates a five-minute exact-object GET URL,
and lifecycle policy removes the artifact after seven days.

### Search

Cmd+K and explicit filters build typed criteria directly. Natural-language mode gives Grok only
the query and restricted DSL schema. The backend rejects unknown/invalid fields, operators,
values, money semantics, and sort keys before mapping the accepted criteria to parameterized
PostgreSQL queries. Provider failure never disables deterministic search.

## 6. Tenancy and authorization

- Every tenant-owned table includes immutable `workspace_id`.
- Repositories require workspace criteria; PostgreSQL RLS is defense in depth.
- Candidate email matching is unique on normalized email within a workspace.
- Application uniqueness is workspace + job + candidate.
- Admin and Recruiter can import, export, search, and see compensation/resume data.
- File access rechecks workspace, role, scan status, and exact object ownership before signing.

## 7. Reliability and scale

- Transactional changes and job/outbox records commit together.
- Import/export handlers are idempotent and retryable; rows expose explicit terminal states.
- Local deployment uses a bounded in-process dispatcher. AWS uses SQS/DLQ through the same queue
  port and handler contracts.
- Drive starts default to five/second, capacity five, and five concurrent; `Retry-After` and
  bounded exponential backoff are honored.
- Stateless API/worker tasks scale horizontally. PostgreSQL remains the source of truth.
- PostgreSQL full-text/trigram indexes are sufficient initially; OpenSearch, embeddings, Redis,
  Kafka, Kubernetes, and microservices are not justified now.

## 8. Security and observability

S3 Block Public Access, disabled ACLs, encryption, opaque keys, least-privilege IAM, and private
network paths protect candidate files. Logs contain correlation/workspace/job identifiers but not
passwords, tokens, resume text, public Drive URLs, or candidate PII. Metrics cover auth failures,
import row states, download throttling/retries, scan results, worker lag, search latency, Grok
failures, and export expiry. Audit events cover sensitive commands and downloads.

## 9. Quality attributes

- Security by design: deny-by-default authorization and untrusted-file isolation.
- Portability: PostgreSQL and provider ports avoid Supabase/AWS business-code coupling.
- Maintainability: modules own behavior and data; HTTP/provider details remain adapters.
- Testability: deterministic clocks/IDs/providers, migration integration tests, contract tests,
  and one Playwright vertical flow.
- Pragmatism: KISS/YAGNI keeps deferred ATS workflows out of the active implementation.
