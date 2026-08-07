# Talon ATS Priority Database Design

## 1. Principles

- PostgreSQL is the source of truth; Supabase is hosting only.
- Forward-only Flyway migrations own schema changes.
- Tenant tables carry `workspace_id UUID NOT NULL` and tenant-aware unique/index keys.
- UUID primary keys, `timestamptz` UTC timestamps, normalized text columns where uniqueness matters.
- Money is ISO currency plus `BIGINT` minor units; JSONB is limited to versioned form answers,
  mappings, criteria snapshots, and safe provider metadata.
- Foreign keys and unique constraints enforce replay safety; RLS is defense in depth.

## 2. Identity and tenancy

### `workspace`

`id`, `name`, `slug`, `default_timezone`, `status`, timestamps. Unique normalized slug.

### `app_user`

`id`, `email`, `normalized_email`, `display_name`, `password_hash`, `status`, `last_login_at`,
timestamps. Global unique normalized email for the initial account model. Hash is BCrypt and never
selected into general profile projections.

### `workspace_membership`

`id`, `workspace_id`, `user_id`, `role` (`ADMIN|RECRUITER` active slice), `status`, timestamps.
Unique `(workspace_id,user_id)`.

### `refresh_session`

`id`, `user_id`, `workspace_id`, `token_hash`, `family_id`, `parent_id`, `expires_at`, `used_at`,
`revoked_at`, `created_at`, safe device/IP audit hashes. Unique token hash. Rotation/reuse handling
locks the family in one transaction.

## 3. Job, candidate, and application

### `job`

`id`, `workspace_id`, `title`, `department_name`, `status`, `version`, timestamps. Index active jobs
by workspace/title.

### `candidate`

`id`, `workspace_id`, `email`, `normalized_email`, `first_name`, `last_name`, `phone`, `location`,
`current_title`, `current_company`, `skills_text`, `experience_months`, timestamps. Unique
`(workspace_id,normalized_email)`.

### `application`

`id`, `workspace_id`, `candidate_id`, `job_id`, `stage`, `source`, `applied_at`, `notice_days`,
`available_from`, `current_ctc_currency`, `current_ctc_minor`, `expected_ctc_currency`,
`expected_ctc_minor`, `form_schema_version`, `form_answers JSONB`, `resume_file_id`, `version`,
timestamps. Unique `(workspace_id,candidate_id,job_id)` and checks require valid currency/money
pairs.

## 4. Files and durable work

### `file_object`

`id`, `workspace_id`, `purpose`, `storage_provider`, `bucket_ref`, `object_key`, `original_filename`,
`declared_media_type`, `detected_media_type`, `size_bytes`, `sha256`, `scan_status`, `storage_state`,
`source_kind`, encrypted/redacted source metadata, `retention_until`, timestamps. Keys contain opaque
IDs, not PII. Quarantine and promoted objects have distinct states/locations.

### `import_job`

`id`, `workspace_id`, `job_id`, `created_by`, `source_file_id`, `csv_schema_version`,
`mapping JSONB`, `status`, total/valid/processed/succeeded/failed/duplicate counts, idempotency key,
lease/retry metadata, timestamps. Unique `(workspace_id,created_by,idempotency_key)`.

### `import_row`

`id`, `workspace_id`, `import_job_id`, `row_number`, `canonical_values JSONB`, `status`,
`error_code`, safe `error_details JSONB`, `candidate_id`, `application_id`, `resume_file_id`, attempt/
lease metadata, timestamps. Unique `(import_job_id,row_number)`.

### `export_job`

`id`, `workspace_id`, `created_by`, `criteria_version`, `criteria JSONB`, `status`, `row_count`,
`file_id`, `expires_at`, idempotency/lease/retry metadata, timestamps.

### `outbox_event` and `processed_message`

Outbox contains event ID/type, workspace, aggregate reference, versioned payload, publish attempts,
and timestamps. Processed message records consumer + message ID for idempotency. Local dispatcher
and SQS both consume these records/contracts.

## 5. Search projection and indexes

Candidate/application search remains relational. Maintain a generated or transactionally updated
`tsvector` across normalized candidate profile, allowlisted form answers, and extracted resume
text. Use GIN full-text indexes and `pg_trgm` indexes for names/email. Add B-tree indexes beginning
with `workspace_id` for job, stage, source, dates, notice/availability, and currency + CTC minor
units. Cursor ordering always includes unique ID as a tie-breaker.

Extracted resume text may be stored in a restricted `resume_search_document` table with
`workspace_id`, application/file ID, language/config, text, vector, and extraction version. General
profile queries do not select it.

## 6. Audit and RLS

`audit_event` stores append-only workspace, actor, action, target, correlation, safe metadata, and
timestamp. Audit login outcomes without passwords/tokens; audit import confirmation, export,
resume download authorization, and sensitive searches without raw query PII where avoidable.

At transaction start the adapter sets the workspace context. RLS policies require row
`workspace_id` to match it. Background work sets context from a validated durable job and never
accepts workspace ID from an untrusted queue payload as sole authority.

## 7. Migration and retention rules

- Prefer expand/backfill/contract; do not edit applied migrations.
- Test migrations against empty and representative existing schemas.
- Index foreign keys and frequent tenant predicates; inspect query plans using realistic fixtures.
- Quarantine objects have short failure retention; clean resumes follow candidate retention;
  exports expire after seven days; refresh sessions are removed after security retention needs.
- Deletion uses application orchestration plus object-store tombstone/retry state so database and
  object lifecycle converge safely.
