# Candidate Application Profile Page Design

## Status and scope

Approved for immediate implementation on 2026-08-08. This slice fixes candidate application
profiles only. Import history, automatic import recovery, and aggregate import terminal semantics
remain deferred until public Drive resume transfer to private S3 and the scan/promotion contract are
verified with the parallel Terraform/deployment work.

## Problem

The database-backed application roster loads, but selecting a candidate does not provide a reliable
profile experience. The current profile is appended after the entire roster, which can put it below
dozens of rows, and the browser contract treats nullable database fields such as `available_from` as
required. An imported application with a missing availability date can therefore fail while the
profile is rendered.

## Design

- Candidate names navigate to `/candidates/applications/{applicationId}`.
- The protected application shell recognizes that route as part of Candidates, preserves it through
  authentication restoration, and keeps Candidates highlighted in the sidebar.
- A focused `CandidateApplicationProfilePage` loads `GET /api/v1/applications/{applicationId}`,
  renders loading/not-found/unavailable states, and provides a Back to candidates action.
- The existing profile presentation is reused, but it is rendered as the page's primary content
  rather than after the roster.
- `availableFrom` and compensation values are explicitly nullable across the TypeScript HTTP
  contract. Missing values render as “Not provided” instead of reaching date/currency formatters.
- Clean-resume download behavior remains unchanged: only Admin/Recruiter users with a backend
  `CLEAN` file and `resumeDownloadAllowed=true` receive the download action.
- Workspace ownership continues to come only from the verified JWT and PostgreSQL RLS. The route
  contains an application UUID, never a workspace identifier.

## Error behavior

- Invalid route identifiers render a safe unavailable/not-found profile state and never reach the
  backend with an unbounded value.
- Backend 404 and 403 responses produce safe profile states without provider, SQL, object-key, or
  candidate-file details.
- A transient detail failure offers Retry; Back to candidates always remains available.
- Missing availability, source, compensation, form answers, phone, or resume metadata does not crash
  rendering.

## Verification

- Witness failing frontend tests for deep-link routing and nullable imported fields before changing
  production code.
- Run focused candidate/App tests, then full frontend lint/test/build.
- Rebuild/restart the local web runtime and manually open an imported application plus a synthetic
  application. Backend APIs and schema do not change in this slice.

## Deferred dependencies

The Terraform development foundation already owns the private candidate bucket, lifecycle prefixes,
and runtime IAM policy; ECS receives the bucket/region through environment variables and the task
role. History/recovery work resumes only after Drive PDFs reliably reach `quarantine/`, scanning can
promote verified files to `clean/`, and exact clean-only download is proven against S3.
