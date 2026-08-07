# Strict Talon Template Import HTTP Design

## Status and scope

This focused design implements the first real frontend/backend candidate-import slice after basic
authentication. An authenticated Workspace Admin or Recruiter selects an existing active job,
uploads a strict Talon CSV, reviews the server-recognized columns, and receives a durable validation
preview.

This checkpoint includes job listing, template download, CSV upload, mapping recognition, durable
draft storage, validation, preview restoration, and real frontend HTTP adapters. Confirmation,
background Drive processing, scanning/extraction adapters, candidate creation, progress/retry,
export, and search remain subsequent checkpoints.

Authentication is unchanged. The existing HS256 JWT access token remains in the shared frontend
`ApiClient` memory for 15 minutes. Refresh renewal, persistent login after reload, and server logout
remain deferred.

## Decisions

### Strict template now, configurable mapping later

The MVP accepts only the following case-insensitive canonical column names:

```text
first_name,last_name,email,resume_drive_url,phone,location,
total_experience_years,current_company,current_title,skills,current_ctc,
expected_ctc,ctc_unit,ctc_currency,notice_period_days,availability_date,
source,application_date
```

`first_name`, `last_name`, `email`, and `resume_drive_url` are required. Unknown columns, missing
required columns, and case-insensitive duplicate columns are rejected before a durable draft is
created. The server returns a read-only one-to-one recognition ledger; recruiters cannot edit it in
this checkpoint.

The existing application-owned `ColumnMapping` boundary remains. A future workspace/recruiter
mapping policy can add aliases or editable mappings without changing candidate/application tables,
CSV parsing, import-row processing, or candidate profile APIs.

### One authenticated client

Auth, jobs, and imports use one `ApiClient` instance. Login places the bearer token in that client;
the job and import gateways therefore receive the same `Authorization` header without browser
storage. Explicit development fixture mode may still compose all fixture gateways, but production
and normal development default to HTTP.

The verified native-fetch receiver correction is preserved and committed before gateway
composition changes.

### Durable drafts without premature processing

PostgreSQL is authoritative for import identity, tenant/job ownership, status, mapping, counts, and
preview rows. The uploaded CSV is stored through the application-owned `ObjectStorage` port at the
opaque `imports/{workspaceId}/{importId}/source.csv` key. Local development uses
`LocalObjectStorage`; AWS later uses the private S3 adapter with identical behavior.

Upload and preview do not contact Google Drive or create candidates/applications. Confirmation will
snapshot the validated draft and wake the worker in the next checkpoint.

## HTTP contract

All endpoints are rooted at `/api/v1`, require the existing bearer authentication except where
noted, and derive workspace/role from the verified JWT rather than request data.

### Jobs

`GET /api/v1/jobs` uses the existing endpoint and returns active import targets with `id`, `title`,
`department`, `location`, and frontend status `OPEN`.

### Template

`GET /api/v1/imports/template` returns an attachment named `talon-candidate-import.csv`. It contains
the canonical header row and one clearly synthetic example row. No candidate data is read.

### Upload

`POST /api/v1/imports` consumes multipart form data:

- `jobId`: UUID of an active job in the authenticated workspace;
- `file`: UTF-8 CSV, maximum 10 MB and 2,000 data rows.

On success it returns HTTP 201:

```json
{
  "id": "uuid",
  "jobId": "uuid",
  "fileName": "applications.csv",
  "rowCount": 25,
  "sourceColumns": ["first_name", "last_name", "email", "resume_drive_url"],
  "suggestedMapping": {
    "first_name": "first_name",
    "last_name": "last_name",
    "email": "email",
    "resume_drive_url": "resume_drive_url"
  },
  "status": "UPLOADED"
}
```

The filename is a bounded display value only and never becomes an object key. A rejected upload
creates neither a database draft nor a stored object.

### Validate and preview

`POST /api/v1/imports/{importId}/validate` accepts the server-returned mapping plus
`retainUnmapped=false`. The backend verifies that it is the exact strict mapping for the uploaded
header, parses and normalizes rows, stores the durable preview snapshot, and returns:

```json
{
  "validCount": 20,
  "invalidCount": 3,
  "duplicateCount": 2,
  "issues": [
    {
      "rowNumber": 4,
      "kind": "INVALID",
      "code": "INVALID_EMAIL",
      "message": "email must be valid"
    }
  ]
}
```

`GET /api/v1/imports/{importId}/preview` restores the same tenant-scoped preview. Validation is
repeatable before confirmation and replaces the prior preview atomically.

Stable upload/mapping failures include `FILE_TOO_LARGE`, `TOO_MANY_ROWS`, `INVALID_CSV`,
`DUPLICATE_SOURCE_COLUMN`, `UNSUPPORTED_SOURCE_COLUMN`, `MISSING_REQUIRED_COLUMN`,
`DUPLICATE_MAPPING`, `MISSING_REQUIRED_MAPPING`, `JOB_NOT_IMPORTABLE`, and `IMPORT_NOT_FOUND`.
Responses contain safe messages only and never echo candidate rows, Drive URLs, SQL, object keys,
or provider details.

## Persistence and tenancy

A forward Flyway migration adds tenant-owned import draft and preview-row tables. The import table
contains IDs, workspace/job/creator ownership, status, private source-object key, bounded display
filename, canonical mapping JSON, row/count metadata, timestamps, and optimistic version. Preview
rows contain import/workspace ownership, source row number, normalized canonical payload or safe
validation issue data, and row status.

Foreign keys, uniqueness, indexes, forced row-level security, and transaction-local workspace
context follow the existing V2/V3 patterns. Candidate PII is never written to logs, Terraform,
fixtures, or Git. Rejected uploads are cleaned up; reconciliation handles a storage/database failure
between the two systems.

## Frontend integration

The existing `ImportWizard`, strict-template recognition UI, and gateway interfaces remain the
presentation contract. New `HttpJobGateway` and `HttpImportGateway` adapters map and validate server
responses defensively and translate stable problems into existing safe UI messages.

Runtime composition creates one `ApiClient`, then supplies it to the HTTP auth, job, and import
gateways. Candidate screens remain fixtures until the confirmation/worker checkpoint produces real
applications. No token, session, CSV, row data, or mapping is placed in Local Storage or Session
Storage.

## Verification

- Backend TDD: strict header recognition, required/unknown/duplicate rejection, active-job and role
  authorization, rejected-upload cleanup, tenant isolation, repeatable preview replacement, and safe
  problem responses.
- PostgreSQL integration: Flyway migration, RLS isolation, durable draft/row persistence, and replay.
- Frontend TDD: shared bearer client, job response mapping, multipart upload, strict draft mapping,
  validation/preview mapping, malformed-response rejection, and safe problems.
- End-to-end smoke: real login, select job, download template, upload synthetic CSV, inspect
  recognized columns, and view valid/invalid/duplicate preview counts.
- Full Maven and frontend formatting/lint/test/build gates must remain green.

## Implementation order

1. Preserve and commit the verified frontend native-fetch auth correction.
2. Add the backend migration and import draft/preview persistence port.
3. Implement template, upload, validate, and preview application/API behavior test-first.
4. Add frontend HTTP job/import adapters using the shared authenticated client.
5. Run contract, full-stack, and E2E verification; update both implementation handoffs.
6. Continue with confirmation and durable worker processing as the next checkpoint.
