# Candidate Import and Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import job applications from mapped Google Forms CSV data with required public Drive PDFs, private storage, durable processing, and filtered CSV export.

**Architecture:** PostgreSQL owns import/export state and idempotency. Application ports isolate CSV parsing, public Drive fetch, rate limiting, scanning, text extraction, object storage, and queues. Local workers and SQS execute identical handlers.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Spring JDBC, streaming CSV parser, Java HTTP client, PDFBox, ClamAV-compatible scanner, local storage, AWS SDK v2 S3/SQS, Testcontainers.

## Global Constraints

- CSV only; ZIP/archive behavior is forbidden.
- Maximum 2,000 rows and 10 MB CSV.
- Every row requires a publicly downloadable Google Drive PDF of at most 10 MB.
- Drive starts are limited to 5/second, burst 5, maximum 5 in flight.
- S3 Block Public Access and quarantine authorization are mandatory.
- Resume binaries and URLs never appear in exports.

---

### Task 1: Import state machine and canonical mapping

**Files:** Create `apps/api/src/main/java/com/talon/ats/imports/domain/**`, `application/**`, and `apps/api/src/test/java/com/talon/ats/imports/ImportMappingTests.java`.

**Interfaces:** Produces `ImportJob`, `ImportRow`, `CanonicalField`, `ColumnMapping`, `ImportStatus`, and `ImportRowStatus` matching the approved spec.

- [x] Write failing tests for required mappings, duplicate assignments, 2,000-row boundary, email/date/experience/notice validation, and LPA-to-paise normalization.
- [x] Run focused tests and confirm missing types cause failure.
- [x] Implement immutable domain state transitions and validation without persistence/provider code.
- [x] Run focused/full Maven verification and commit `feat: add candidate import domain`.

### Task 2: Streaming CSV validation and preview

**Files:** Create `imports/application/CsvApplicationParser.java`, `imports/infrastructure/csv/**`, and parser fixtures containing synthetic data only. Wire the HTTP DTO/controller to the durable PostgreSQL import job in Task 5 so the API does not depend on a temporary in-memory upload store.

**Interfaces:** Consumes a bounded stream and `ColumnMapping`; produces row-numbered normalized payload/errors without loading resume files.

- [x] Test BOM, quoting, malformed rows, unknown columns, UTF-8, file/row limits, formula-like content, duplicate candidate/application preview, and partial errors.
- [x] Implement the bounded streaming parse/inspection application boundary; defer only HTTP persistence/wiring to Task 5.
- [x] Verify the bounded 2,000-row behavior, full tests, and formatting. Commit as `feat: add CSV import preview`.

### Task 3: Public Drive PDF source and leaky-bucket policy

**Files:** Create `files/application/ExternalFileSource.java`, `files/application/FetchPolicy.java`, `files/infrastructure/drive/PublicGoogleDriveSource.java`, `platform/ratelimit/LeakyBucket.java`, and contract tests.

**Interfaces:** `ExternalFileSource.fetch(SourceReference, BoundedObjectSink)` returns metadata or stable source/file failure codes.

- [x] Test allowed link shapes, HTTPS/host/redirect/DNS rejection, auth interstitial, 429 Retry-After, permanent/transient classification, 10 MB streaming cutoff, PDF signature, rate 5/burst 5/concurrency 5.
- [x] Implement no-HTML-scraping anonymous fetch and bounded stream copy.
- [x] Run adapter tests/full verification and commit `feat: add rate limited public Drive PDF source`.

### Task 4: Private object storage, quarantine, scan, and extraction

**Files:** Create storage/scanner/extractor ports, local/S3 adapters, Flyway file metadata migration, Terraform module plan inputs, and contract/integration tests.

**Interfaces:** Produces opaque quarantine/clean objects, five-minute authorized downloads, `FileScanStatus`, and bounded resume search text.

- [x] Implement and verify opaque object keys plus a bounded, integrity-reporting local storage adapter with quarantine-to-clean promotion.
- [x] Implement and verify fail-closed scan/promote/extract orchestration and clean-only workspace download policy.
- [ ] Test private keys, no PII, no quarantine download, clean-only authorization, signature age, scanner fail-closed, 50-page/500k-character/10-second extraction bounds, and seven-day lifecycle categories.
- [ ] Implement local adapter first, then S3/SQS adapters behind identical contracts.
- [ ] Verify S3 public-access Terraform assertions, tests, and commit `feat: add private candidate file storage`.

### Task 5: Durable import worker and APIs

**Files:** Create import persistence migration/adapters, worker handler, local queue adapter, SQS port adapter, controllers, and integration tests.

**Interfaces:** Implements `/api/v1/imports` upload/mapping/validate/preview/confirm/status/rows/retry/errors.csv` contracts.

- [ ] Test idempotent confirmation, batch claiming, row retry, duplicate application, object orphan reconciliation, redelivery, partial completion, cancellation, and RLS.
- [ ] Implement PostgreSQL-authoritative state with local worker wake-up.
- [ ] Verify integration tests and commit `feat: process durable candidate imports`.

### Task 6: Filtered asynchronous CSV export

**Files:** Create `exports/**`, migration, streaming writer, APIs, storage integration, and tests.

**Interfaces:** Implements create/status/download export APIs and consumes the validated candidate search filter contract.

- [ ] Test Admin/Recruiter authorization, filter snapshot, streaming, spreadsheet-injection escaping, compensation visibility, no resume/source/S3 fields, five-minute download, and seven-day expiry.
- [ ] Implement local/AWS-neutral export job handling.
- [ ] Run full verification and commit `feat: add secure candidate CSV export`.
