# Database-Backed Candidate Roster Design

- Status: Approved in design discussion; pending written-spec review
- Date: 2026-08-08
- Scope: Replace the live Candidates fixture path with tenant-scoped application data from PostgreSQL and provide repeatable synthetic Supabase seed data

## 1. Objective

The Candidates page must display the same workspace-owned candidate and application records used by deterministic and Groq-assisted search. The page remains an application pipeline: one row represents one candidate/job application, so a candidate who applies to two jobs appears twice with job-specific stage, dates, compensation, and resume state.

Runtime development and production paths use authenticated Talon APIs. Fixture gateways are permitted only as test helpers and are not selected by the browser bootstrap or included as a live fallback.

## 2. API and application boundary

The candidates module owns an application-read port and a PostgreSQL adapter. Controllers derive the workspace and role from the verified JWT; clients cannot submit a workspace identifier.

The additive API surface is:

- `GET /api/v1/applications?cursor=&limit=` returns a newest-first, cursor-paged application roster.
- `GET /api/v1/applications/{applicationId}` returns the selected candidate profile, application fields, safe additional form answers, and resume state.
- `GET /api/v1/applications/{applicationId}/resume-download` returns or redirects to an authorized clean-file download. A non-clean or cross-workspace file is never exposed.

The list and detail queries run inside a transaction that sets `app.current_workspace_id` and uses `SET LOCAL ROLE talon_app`, preserving PostgreSQL row-level security. SQL is parameterized, results use bounded page sizes, and the cursor is opaque.

## 3. Projection behavior

Each roster row contains the existing frontend projection: application and candidate IDs, candidate name, job title, stage, location, experience, current role/company, skills, compensation, notice period, application date, and resume state.

Resume display state is derived from `candidate_file`:

- no row: `NO_RESUME`
- `QUARANTINED`: quarantined/processing
- `SCAN_PENDING`: scan pending
- `CLEAN`: clean and eligible for authorized download
- `UNSAFE`: unsafe and never downloadable
- `FAILED`: processing failed and never downloadable

Candidate detail adds display email, masked phone, source, availability, additional answers, safe file name, and download capability. The API never returns object keys, Drive URLs, extracted resume text, provider errors, storage credentials, or tenant identifiers supplied by the client.

The first page loads automatically. If an opaque next cursor is returned, the page exposes an explicit “Load more” action so production-sized workspaces do not require an unbounded query. Empty, loading, forbidden, unavailable, and retry states remain explicit.

## 4. Frontend runtime wiring

`HttpCandidateGateway` implements the existing candidate boundary through the shared authenticated `ApiClient`. `main.tsx` always chooses HTTP candidate, job, import, and search gateways for the running application. Runtime fixture selection is removed. Test fixtures remain importable only by test suites and component stories/helpers; they are not a recovery path when the API fails.

Cmd/Ctrl+K and the Search page keep their dedicated search endpoints. They need no duplicate candidate store because both their queries and the roster read from the same PostgreSQL `candidate`, `application`, `job`, and `candidate_file` records.

## 5. Synthetic Supabase seed

The repository includes an operator-run SQL script rather than a Flyway migration because sample records must never be created automatically in production.

The script:

- targets an existing workspace by an operator-edited slug;
- aborts if that slug is absent or ambiguous;
- inserts at least 32 synthetic candidates using reserved `.test` email addresses;
- creates a small set of synthetic active jobs and one application per candidate;
- varies names, locations, skills, experience, stages, application dates, notice periods, availability, and INR compensation so keyword, structured, and natural-language searches are meaningful;
- uses stable UUIDs and `ON CONFLICT` updates, making reruns idempotent;
- does not add fake clean resume metadata because no matching private object exists;
- sets transaction-local tenant context before tenant-owned writes;
- contains no credentials, real candidate PII, access tokens, Drive URLs, or object keys.

Running the script in the Supabase SQL Editor is an explicit non-production demo operation. The operator reviews the workspace slug before execution.

## 6. Error and authorization behavior

- Missing and cross-workspace application IDs both return a safe not-found response.
- Admin and Recruiter roles can read the current priority projection, including compensation.
- Resume download requires a `CLEAN` file row and successful object-storage lookup; all other states return a stable safe problem response.
- Database, storage, and provider details are not surfaced to the browser.
- The Candidates page does not fall back to fixture records after an API error.

## 7. Verification

Backend verification covers projection mapping, cursor bounds, workspace isolation, missing resume behavior, safe detail errors, and clean-only resume authorization. The Supabase schema smoke validates the existing migrations used by the query.

Frontend verification covers HTTP response mapping, authenticated roster loading, pagination, profile detail, no-resume display, clean resume download, and safe error/retry states. The full frontend lint, test, and production build gates run before completion.

A live manual check uses the ignored Supabase runtime environment to verify that seeded records appear consistently in Candidates, Cmd/Ctrl+K, keyword search, and Groq-interpreted search. Live evidence is recorded separately from automated evidence and is not claimed until observed.

## 8. Out of scope

- Candidate editing, deletion, bulk actions, stage mutation, and deduplication UI.
- Creating fake resume files or marking nonexistent objects clean.
- Direct browser access to Supabase tables, Auth, Storage, or the Data API.
- Replacing the validated search DSL or merging roster and search endpoints.
