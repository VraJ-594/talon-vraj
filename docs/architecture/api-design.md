# Talon ATS Priority API Design

## 1. Conventions

- Base path `/api/v1`; JSON except CSV upload/download.
- UUID identifiers, ISO-8601 UTC timestamps, opaque cursors, default/max page sizes.
- Bearer access JWT; refresh token is transported in a Secure, HttpOnly, SameSite cookie where the
  frontend and API deployment topology permits it.
- Retryable commands require `Idempotency-Key`.
- Errors use RFC 9457 `application/problem+json` with `code`, `correlationId`, and safe field/row
  details. Provider payloads, SQL, tokens, Drive URLs, and resume text are never returned in errors.

## 2. Authentication

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/login` | Verify existing account and issue access/refresh pair |
| `POST` | `/auth/refresh` | Rotate refresh session and issue access token |
| `POST` | `/auth/logout` | Revoke current refresh session |
| `GET` | `/session` | Current user, workspace, role, and capabilities |

Login body contains `email` and `password`. Responses never contain hashes. Public registration,
OAuth, TOTP/2FA, password reset, invitations, and member-management endpoints are deferred.

## 3. Minimal jobs and candidates

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/jobs?status=&cursor=&limit=` | Jobs available as import targets |
| `POST` | `/jobs` | Create minimal job (Admin/Recruiter) |
| `GET` | `/applications?cursor=&limit=` | Newest-first application pipeline page |
| `GET` | `/candidates` | Typed candidate search/list query |
| `GET` | `/candidates/{candidateId}` | Candidate plus applications/profile summary |
| `GET` | `/applications/{applicationId}` | Selected application/form/resume summary |
| `GET` | `/applications/{applicationId}/resume-download` | Five-minute authorized private GET URL |

The application pipeline intentionally returns one row per application, so the same candidate can
appear under different jobs. It uses an opaque newest-first seek cursor and a bounded `limit` of
`1..100`. Candidate search accepts text, typed filters, allowlisted sort, cursor, and limit through
the endpoints in section 6. The server remains authoritative for workspace, role, and
sensitive-field visibility.

## 4. CSV template and import

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/candidate-imports/template` | Download canonical CSV headings/example |
| `POST` | `/candidate-imports/uploads` | Upload CSV up to 10 MB |
| `POST` | `/candidate-imports/{id}/mapping-preview` | Infer/validate submitted column mapping |
| `POST` | `/candidate-imports/{id}/preview` | Canonical validation preview for selected job |
| `POST` | `/candidate-imports/{id}/confirm` | Idempotently queue durable import |
| `GET` | `/candidate-imports/{id}` | Aggregate status/progress/counts |
| `GET` | `/candidate-imports/{id}/rows` | Paginated row status/errors/duplicate outcome |

Confirm snapshots `jobId`, mapping, canonical row data, and validation. Required canonical fields
are first name, last name, email, and public Drive resume URL. The versioned template additionally
supports phone, location, current title/company, skills, experience, source, notice period,
availability, current/expected compensation currency and annual minor units, plus preserved
allowlisted form answers. Each row creates/matches a workspace candidate and creates/matches an
application for the selected job.

Preview/row responses use stable codes such as `REQUIRED_VALUE_MISSING`, `INVALID_EMAIL`,
`INVALID_MONEY`, `UNSUPPORTED_RESUME_URL`, `DUPLICATE_IN_FILE`, `RESUME_NOT_PUBLIC`,
`RESUME_TOO_LARGE`, `RESUME_NOT_PDF`, `MALWARE_DETECTED`, and `ALREADY_APPLIED`.

## 5. Export

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/candidate-exports` | Snapshot validated search criteria and queue export |
| `GET` | `/candidate-exports/{id}` | Status, counts, expiry |
| `GET` | `/candidate-exports/{id}/download` | Five-minute authorized private CSV URL |

Export includes authorized candidate/application fields but excludes resume bytes, public Drive
URLs, private object keys, and presigned URLs. Artifacts expire after seven days.

## 6. Search

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/search/command?q=&limit=` | Deterministic Cmd+K navigation/candidate lookup |
| `POST` | `/candidate-search/interpret` | Translate natural language into validated criteria/chips |
| `POST` | `/candidate-search/query` | Execute explicit validated criteria |

`/interpret` accepts `{query, locale?, timezone?}`. It returns `{dslVersion, criteria, chips,
warnings}` only after backend validation. It does not execute automatically unless the client then
submits `criteria` to `/query`. `/query` supports text, predicates, allowlisted sort, cursor, and
limit. Standard Cmd+K never invokes Groq.

Natural-language errors distinguish `INTERPRETER_DISABLED`, `INTERPRETER_UNAVAILABLE`,
`INTERPRETATION_INVALID`, and `AMBIGUOUS_CURRENCY`. Clients retain the original query and explicit
filter controls so deterministic search remains usable.

## 7. Authorization matrix

| Capability | Admin | Recruiter |
|---|---:|---:|
| Sign in / session | yes | yes |
| List/create import-target jobs | yes | yes |
| Import applications/resumes | yes | yes |
| Candidate/profile/search | yes | yes |
| Compensation/resume access | yes | yes |
| Export candidate CSV | yes | yes |

Future Hiring Manager/Interviewer behavior is not part of the active API.

## 8. Compatibility

OpenAPI is the frontend/backend contract. Additive fields are allowed within v1; removing or
changing meaning requires a new version. DSL and CSV template versions are explicit and retained
long enough to finish durable jobs created under the prior version.
