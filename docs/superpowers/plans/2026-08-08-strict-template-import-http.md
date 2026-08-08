# Strict Talon Template Import HTTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first authenticated end-to-end ATS import slice: select an existing job, download the strict Talon CSV template, upload and durably validate a CSV, and restore its preview through real frontend/backend HTTP integration.

**Architecture:** Spring Boot owns the import policy, workflow, PostgreSQL persistence, and HTTP contract behind focused application ports. PostgreSQL stores tenant-owned draft and preview state; an opaque source key addresses the private CSV through the existing `ObjectStorage` port. React composes one in-memory-token `ApiClient` into auth, job, and import HTTP gateways; local storage is only a runtime adapter, so a later private-S3/ECS/Terraform checkpoint changes infrastructure wiring without changing import behavior.

**Tech Stack:** Java 21, Spring Boot, Spring Security JWT resource server, Spring JDBC, PostgreSQL 17/Supabase-compatible SQL, Flyway, Apache Commons CSV, JUnit 5, Testcontainers, React 19, TypeScript 5.9, Vite, Vitest, Testing Library, Playwright.

## Global Constraints

- Accept only the 18 case-insensitive canonical headers defined in the approved spec; require `first_name`, `last_name`, `email`, and `resume_drive_url`.
- Reject unknown, missing-required, and case-insensitive duplicate headers before creating a database draft or stored object.
- Limit uploads to UTF-8 CSV, 10 MiB, and 2,000 data rows.
- Derive user ID, workspace ID, and role only from the verified JWT; only `WORKSPACE_ADMIN` and `RECRUITER` may use import endpoints.
- Keep the existing 15-minute HS256 JWT access-token behavior and 7-day opaque refresh cookie unchanged.
- Never store tokens, sessions, CSV contents, candidate rows, mappings, or Drive URLs in browser storage or logs.
- Keep application code PostgreSQL-portable and provider-neutral: no Supabase Auth, Storage, Edge Functions, or Data API coupling.
- Keep source CSV objects private. Local disk is the development adapter; AWS uses a private S3 adapter with IAM and presigned download URLs in a later checkpoint.
- Do not contact Google Drive, create candidates/applications, confirm jobs, process rows, export data, or implement natural-language search in this checkpoint.
- Use forward-only Flyway migration `V4`; never edit an already applied migration.
- Update the active implementation handoff with observed evidence before ending the session.

---

## File and Boundary Map

### Backend files

- `apps/api/src/main/java/com/talon/ats/imports/application/StrictTalonImportTemplate.java`: canonical header order, strict recognition, exact mapping verification, and synthetic template bytes.
- `apps/api/src/main/java/com/talon/ats/imports/application/ImportDraftRepository.java`: tenant-scoped persistence port for draft and preview replacement/readback.
- `apps/api/src/main/java/com/talon/ats/imports/application/ImportDraftService.java`: upload, validate, preview, authorization, storage/database compensation, and status transitions.
- `apps/api/src/main/java/com/talon/ats/imports/application/ImportDraft.java`: durable upload response model including display filename, source columns, mapping, source key, and status.
- `apps/api/src/main/java/com/talon/ats/imports/application/ImportPreviewSnapshot.java`: counts and safe issue data returned by validate/preview.
- `apps/api/src/main/java/com/talon/ats/imports/application/ImportProblem.java`: stable safe problem code and HTTP-neutral message.
- `apps/api/src/main/java/com/talon/ats/imports/api/ImportController.java`: `/api/v1/imports` multipart, validate, preview, and template endpoints.
- `apps/api/src/main/java/com/talon/ats/imports/api/ImportProblemHandler.java`: safe RFC-7807-style error response mapping.
- `apps/api/src/main/java/com/talon/ats/imports/infrastructure/ImportRuntimeConfiguration.java`: Spring wiring for ports and local runtime adapter.
- `apps/api/src/main/java/com/talon/ats/imports/infrastructure/persistence/JdbcImportDraftRepository.java`: transaction-local workspace context and atomic preview replacement.
- `apps/api/src/main/resources/db/migration/V4__durable_candidate_import_preview.sql`: import draft/row tables, indexes, constraints, grants, and forced RLS.
- `apps/api/src/main/java/com/talon/ats/files/application/ObjectStorage.java`: add idempotent deletion needed for upload compensation.
- `apps/api/src/main/java/com/talon/ats/files/infrastructure/storage/LocalObjectStorage.java`: implement safe root-confined deletion.
- `apps/api/src/main/resources/application.yml`: environment-driven local private-object root and storage provider selection.

### Frontend files

- `apps/web/src/lib/apiClient.ts`: preserve the detached native-fetch receiver fix and shared bearer behavior.
- `apps/web/src/features/auth/runtimeAuthGateway.ts`: accept the composed `ApiClient` instead of constructing a private client.
- `apps/web/src/features/jobs/httpJobGateway.ts`: authenticated job-list adapter with defensive response validation.
- `apps/web/src/features/imports/httpImportGateway.ts`: multipart upload, validation, and preview adapter with stable safe problems.
- `apps/web/src/features/imports/importGateway.ts`: add preview issue `code` and `JOB_NOT_IMPORTABLE`; retain future methods without invoking them.
- `apps/web/src/main.tsx`: compose all fixture gateways only in explicit fixture mode; otherwise share one HTTP client.
- `apps/web/src/features/jobs/httpJobGateway.test.ts`, `apps/web/src/features/imports/httpImportGateway.test.ts`, `apps/web/src/features/auth/runtimeAuthGateway.test.ts`, and `apps/web/src/lib/apiClient.test.ts`: contract and shared-token tests.

---

### Task 1: Preserve the Verified Browser Fetch Correction

**Files:**
- Modify: `apps/web/src/lib/apiClient.ts`
- Create: `apps/web/src/lib/apiClient.test.ts`
- Modify: `docs/implementation/frontend-web-handoff.md`

**Interfaces:**
- Consumes: existing `ApiFetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>`.
- Produces: unchanged `ApiClient.request(path, init, authenticated)` API that calls the injected function without rebinding its receiver.

- [ ] **Step 1: Review the focused regression test already present in the frontend worktree**

Ensure the test passes a receiver-sensitive function and asserts `ApiClient.request()` invokes it as a detached function while still adding `credentials: 'include'` and the bearer header.

- [ ] **Step 2: Run the focused test against the correction**

Run from the frontend worktree:

```powershell
npm --workspace @talon/web run test -- src/lib/apiClient.test.ts
```

Expected: the receiver regression and bearer-header cases pass.

- [ ] **Step 3: Run the complete frontend gate before checkpointing the existing work**

```powershell
npm run format:check:web
npm run lint:web
npm run test:web
npm run build:web
```

Expected: every command exits 0.

- [ ] **Step 4: Record only the observed test/build counts and commit the correction**

```powershell
git add apps/web/src/lib/apiClient.ts apps/web/src/lib/apiClient.test.ts docs/implementation/frontend-web-handoff.md
git commit -m "fix: preserve native fetch receiver"
```

Expected: the frontend worktree becomes clean before runtime composition changes.

---

### Task 2: Enforce the Strict Talon Template Policy

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/imports/application/StrictTalonImportTemplate.java`
- Create: `apps/api/src/test/java/com/talon/ats/imports/StrictTalonImportTemplateTests.java`
- Modify: `apps/api/src/main/java/com/talon/ats/imports/infrastructure/csv/CommonsCsvApplicationParser.java`
- Modify: `apps/api/src/test/java/com/talon/ats/imports/CsvApplicationParserTests.java`

**Interfaces:**
- Consumes: `CsvInspection`, `CanonicalField`, `ColumnMapping`, and `CsvParseException`.
- Produces:

```java
public final class StrictTalonImportTemplate {
  public List<String> canonicalHeaders();
  public byte[] downloadBytes();
  public Map<String, CanonicalField> recognize(CsvInspection inspection);
  public ColumnMapping requireExactMapping(
      List<String> sourceColumns,
      Map<String, CanonicalField> requested,
      boolean retainUnmapped);
}
```

- [ ] **Step 1: Write failing strict-policy tests**

Cover all of these concrete cases:

```java
assertThat(template.recognize(inspect("FIRST_NAME,last_name,email,resume_drive_url\nA,B,a@example.com,https://drive.google.com/file/d/x/view")))
    .containsEntry("FIRST_NAME", CanonicalField.FIRST_NAME);

assertProblem("UNSUPPORTED_SOURCE_COLUMN", "first_name,last_name,email,resume_drive_url,favorite_color");
assertProblem("MISSING_REQUIRED_COLUMN", "first_name,last_name,email");
assertProblem("DUPLICATE_SOURCE_COLUMN", "email,EMAIL,first_name,last_name,resume_drive_url");
```

Also assert that `downloadBytes()` is UTF-8, begins with the exact canonical header order, contains one synthetic row, and contains no database-derived data.

- [ ] **Step 2: Run the tests and verify the policy does not exist**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=StrictTalonImportTemplateTests,CsvApplicationParserTests" test
```

Expected: compilation fails because `StrictTalonImportTemplate` is absent.

- [ ] **Step 3: Implement one case-insensitive recognition ledger**

Use a `LinkedHashMap<String, CanonicalField>` keyed by the original source labels, compare normalized names with `Locale.ROOT`, and throw only stable `CsvParseException` codes. Require exact mapping equality and `retainUnmapped == false`; do not add aliases or editable mapping behavior.

- [ ] **Step 4: Make parser duplicate detection case-insensitive**

Change header uniqueness to track `header.trim().toLowerCase(Locale.ROOT)` while preserving the source header text in `CsvInspection`. This makes `email` and `EMAIL` a deterministic `DUPLICATE_SOURCE_COLUMN`.

- [ ] **Step 5: Run focused and module tests**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=StrictTalonImportTemplateTests,CsvApplicationParserTests,ImportMappingTests" test
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit the policy**

```powershell
git add apps/api/src/main/java/com/talon/ats/imports apps/api/src/test/java/com/talon/ats/imports
git commit -m "feat: enforce strict Talon import template"
```

---

### Task 3: Add Safe Object Deletion for Upload Compensation

**Files:**
- Modify: `apps/api/src/main/java/com/talon/ats/files/application/ObjectStorage.java`
- Modify: `apps/api/src/main/java/com/talon/ats/files/infrastructure/storage/LocalObjectStorage.java`
- Modify: `apps/api/src/test/java/com/talon/ats/files/PrivateObjectStorageTests.java`

**Interfaces:**
- Consumes: existing opaque `PrivateObjectKey` and root-confined local storage behavior.
- Produces:

```java
void delete(PrivateObjectKey key);
```

Deletion is idempotent and implementations must never resolve outside their configured private root.

- [ ] **Step 1: Write failing deletion contract tests**

Test deletion of an existing import object, repeated deletion of the same key, and traversal-resistant resolution. Assert the object no longer exists and no sibling file outside the configured root changes.

- [ ] **Step 2: Run the storage test and observe the missing method**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=PrivateObjectStorageTests" test
```

Expected: compilation fails on `ObjectStorage.delete`.

- [ ] **Step 3: Implement idempotent root-confined deletion**

Add `delete` to the port and use the same resolved-path guard as `put`, `open`, and `promote`; translate I/O failure to the storage adapter's existing safe runtime exception without logging a key or content.

- [ ] **Step 4: Run file-module tests**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=PrivateObjectStorageTests,CandidateFileProcessingTests,PublicGoogleDriveSourceTests" test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the compensation primitive**

```powershell
git add apps/api/src/main/java/com/talon/ats/files apps/api/src/test/java/com/talon/ats/files
git commit -m "feat: support private object cleanup"
```

---

### Task 4: Persist Tenant-Owned Import Drafts and Preview Rows

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V4__durable_candidate_import_preview.sql`
- Create: `apps/api/src/main/java/com/talon/ats/imports/application/ImportDraft.java`
- Create: `apps/api/src/main/java/com/talon/ats/imports/application/ImportPreviewSnapshot.java`
- Create: `apps/api/src/main/java/com/talon/ats/imports/application/ImportDraftRepository.java`
- Create: `apps/api/src/main/java/com/talon/ats/imports/infrastructure/persistence/JdbcImportDraftRepository.java`
- Create: `apps/api/src/test/java/com/talon/ats/imports/JdbcImportDraftRepositoryTests.java`
- Modify: `apps/api/src/test/java/com/talon/ats/identity/PrioritySchemaMigrationIT.java`

**Interfaces:**
- Consumes: `ImportStatus`, `ColumnMapping`, `CsvParseResult`, `PrivateObjectKey`, Spring `JdbcTemplate`, and `TransactionOperations`.
- Produces:

```java
public interface ImportDraftRepository {
  ImportDraft create(ImportDraft draft);
  Optional<ImportDraft> find(UUID workspaceId, UUID importId);
  ImportPreviewSnapshot replacePreview(
      UUID workspaceId,
      UUID importId,
      ColumnMapping mapping,
      CsvParseResult result,
      Instant changedAt);
  Optional<ImportPreviewSnapshot> findPreview(UUID workspaceId, UUID importId);
}
```

`ImportDraft` contains `id`, `workspaceId`, `jobId`, `createdBy`, `fileName`, `sourceObjectKey`, `rowCount`, `sourceColumns`, `suggestedMapping`, `status`, `version`, `createdAt`, and `updatedAt`. `ImportPreviewSnapshot` contains `importId`, counts, safe issues, and status; normalized valid row payload is persisted for the next confirmation checkpoint but is never returned by the preview endpoint.

- [ ] **Step 1: Write failing schema and repository tests**

Add assertions that V4 creates `candidate_import` and `candidate_import_row`, enforces `(workspace_id, id)` ownership keys, links the selected job and creator, forces RLS, and grants only `talon_app`. Repository tests must demonstrate:

```java
repository.create(draft);
assertThat(repository.find(workspaceA, draft.id())).contains(draft);
assertThat(repository.find(workspaceB, draft.id())).isEmpty();
repository.replacePreview(workspaceA, draft.id(), mapping, first, NOW);
repository.replacePreview(workspaceA, draft.id(), mapping, replacement, LATER);
assertThat(repository.findPreview(workspaceA, draft.id())).contains(replacementSnapshot);
```

- [ ] **Step 2: Run migration/repository tests and verify failure**

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=PrioritySchemaMigrationIT,JdbcImportDraftRepositoryTests" test
```

Expected: tests fail because V4 and repository types do not exist.

- [ ] **Step 3: Add the forward migration**

Use `jsonb` for source-column/mapping and normalized-row snapshots, bounded `varchar` for status, filename, issue code/kind/message, `CHECK` constraints for counts and JSON shapes, `UNIQUE(workspace_id,id)`, composite foreign keys to job, membership/user ownership as supported by the existing schema, indexes for workspace/status and import/row number, forced RLS policies using `talon_current_workspace_id()`, and explicit `talon_app` grants. Do not store raw CSV or provider credentials in PostgreSQL.

- [ ] **Step 4: Implement the JDBC repository with transaction-local tenancy**

Every public operation must execute within `TransactionOperations`, then call:

```sql
SELECT set_config('app.current_workspace_id', ?, true);
SET LOCAL ROLE talon_app;
```

`replacePreview` must lock the import row, reject missing imports, delete the prior preview rows, insert replacement rows, update mapping/counts/status/version, and commit atomically. Map JSON using the project's configured Jackson `ObjectMapper`; no SQL string concatenation.

- [ ] **Step 5: Run the focused persistence gate**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=PrioritySchemaMigrationIT,JdbcImportDraftRepositoryTests" test
```

Expected: migration, RLS isolation, draft replay, and atomic replacement tests pass.

- [ ] **Step 6: Commit persistence**

```powershell
git add apps/api/src/main/resources/db/migration/V4__durable_candidate_import_preview.sql apps/api/src/main/java/com/talon/ats/imports apps/api/src/test/java/com/talon/ats/imports apps/api/src/test/java/com/talon/ats/identity/PrioritySchemaMigrationIT.java
git commit -m "feat: persist candidate import previews"
```

---

### Task 5: Implement the Import Application Workflow

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/imports/application/ImportDraftService.java`
- Create: `apps/api/src/main/java/com/talon/ats/imports/application/ImportProblem.java`
- Create: `apps/api/src/test/java/com/talon/ats/imports/ImportDraftServiceTests.java`
- Modify: `apps/api/src/main/java/com/talon/ats/jobs/application/JobRepository.java`
- Modify: `apps/api/src/main/java/com/talon/ats/jobs/infrastructure/persistence/JdbcJobRepository.java`
- Modify: `apps/api/src/test/java/com/talon/ats/jobs/JobPersistenceTests.java`

**Interfaces:**
- Consumes: `CsvApplicationParser`, `StrictTalonImportTemplate`, `ImportDraftRepository`, `ObjectStorage`, `JobRepository`, `Clock`, and `Supplier<UUID>`.
- Produces:

```java
public final class ImportDraftService {
  public ImportDraft upload(Actor actor, UUID jobId, String fileName, ReopenableUpload csv);
  public ImportPreviewSnapshot validate(
      Actor actor, UUID importId, Map<String, CanonicalField> mapping, boolean retainUnmapped);
  public ImportPreviewSnapshot preview(Actor actor, UUID importId);
  public byte[] template(Actor actor);
}

public interface ReopenableUpload {
  InputStream open();
}
```

Add `JobRepository.findImportTarget(UUID workspaceId, UUID jobId): Optional<Job>` so the service can reject jobs outside the JWT workspace or statuses other than `ACTIVE`/`ON_HOLD` as `JOB_NOT_IMPORTABLE`.

- [ ] **Step 1: Write failing service tests for authorization and upload ordering**

Test admin/recruiter success and unauthorized-role rejection. For accepted uploads assert this exact order through fakes: inspect, strict recognition, active-job lookup, private object put at `PrivateObjectKey.importSource(workspaceId, importId)`, repository create. Assert a normalized, bounded display filename is returned while the filename never determines the key.

- [ ] **Step 2: Write failing compensation tests**

Assert parser/header/job rejection calls neither `put` nor `create`; object-write failure calls no repository; repository-create failure calls `delete(key)` exactly once and returns a safe provider-neutral `IMPORT_STORAGE_FAILED` response. Never include object keys or CSV fields in exception messages.

- [ ] **Step 3: Write failing validation/replay tests**

Assert validate loads only a tenant-owned draft, verifies the exact returned mapping with `retainUnmapped=false`, opens its opaque source object, parses it, and atomically replaces the preview. Assert a second validation replaces rather than appends rows, and cross-workspace/missing IDs return `IMPORT_NOT_FOUND` without revealing existence.

- [ ] **Step 4: Run tests and observe missing workflow**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=ImportDraftServiceTests,JobPersistenceTests" test
```

Expected: compilation fails on the new service/port methods.

- [ ] **Step 5: Implement the minimal provider-neutral workflow**

Use a single generated import UUID for the database row and opaque object key. Call `inspect` before any durable write, recognize strict columns before the active-job lookup result is exposed, reopen the multipart stream for `ObjectStorage.put`, and compensate only after a confirmed successful object write. Validation returns only counts and safe issue metadata; it does not fetch Drive resumes or create applications.

- [ ] **Step 6: Run the focused service gate**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=ImportDraftServiceTests,JobPersistenceTests,StrictTalonImportTemplateTests" test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit the workflow**

```powershell
git add apps/api/src/main/java/com/talon/ats/imports apps/api/src/test/java/com/talon/ats/imports apps/api/src/main/java/com/talon/ats/jobs apps/api/src/test/java/com/talon/ats/jobs
git commit -m "feat: add durable import draft workflow"
```

---

### Task 6: Expose the Authenticated Import HTTP Contract

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/imports/api/ImportController.java`
- Create: `apps/api/src/main/java/com/talon/ats/imports/api/ImportProblemHandler.java`
- Create: `apps/api/src/main/java/com/talon/ats/imports/infrastructure/ImportRuntimeConfiguration.java`
- Create: `apps/api/src/test/java/com/talon/ats/imports/ImportControllerTests.java`
- Create: `apps/api/src/test/java/com/talon/ats/imports/ImportRuntimeConfigurationTests.java`
- Modify: `apps/api/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Task 5 `ImportDraftService` and existing JWT claims `sub`, `workspace_id`, and `role`.
- Produces:
  - `GET /api/v1/imports/template`
  - `POST /api/v1/imports` with multipart `jobId` and `file`
  - `POST /api/v1/imports/{importId}/validate` with `{mapping,retainUnmapped}`
  - `GET /api/v1/imports/{importId}/preview`

- [ ] **Step 1: Write failing MockMvc contract tests**

Use `SecurityMockMvcRequestPostProcessors.jwt()` and assert unauthenticated requests are 401; admin/recruiter claims become `ImportDraftService.Actor`; client workspace IDs are ignored/rejected; multipart success is 201; template uses `text/csv` with `attachment; filename="talon-candidate-import.csv"`; validate and preview use the approved JSON shapes.

- [ ] **Step 2: Write safe-problem response tests**

For every stable code in the spec plus `IMPORT_STORAGE_FAILED`, assert status and body contain only `code`, `title`, and safe `detail`. Assert response text does not contain sample email, Drive URL, SQL, local root, object key, or exception class.

- [ ] **Step 3: Run controller tests and observe missing endpoints**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=ImportControllerTests,ImportRuntimeConfigurationTests" test
```

Expected: compilation fails because controller/runtime types are absent.

- [ ] **Step 4: Implement controller DTOs and exception mapping**

Convert JWT claims with the same actor pattern as `JobController`. Pass `MultipartFile::getInputStream` as `ReopenableUpload`; never load the entire 10 MiB file solely for routing. Map malformed UUID/body/multipart input to safe 400 responses, authorization to 403, missing tenant-owned import to 404, and storage/provider failures to 503.

- [ ] **Step 5: Wire local private storage through environment properties**

Add configuration equivalent to:

```yaml
talon:
  files:
    provider: ${TALON_FILES_PROVIDER:local}
    local-root: ${TALON_FILES_LOCAL_ROOT:${java.io.tmpdir}/talon-private-files}
```

Create `LocalObjectStorage` only when provider is `local`; fail startup clearly for an unsupported provider instead of silently using public storage. Keep `ImportDraftService` dependent on `ObjectStorage`, never `LocalObjectStorage`. A future `s3` profile supplies the same port.

- [ ] **Step 6: Run controller, runtime, and security tests**

```powershell
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml "-Dtest=ImportControllerTests,ImportRuntimeConfigurationTests,AuthControllerTests,JobControllerTests" test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit the HTTP slice**

```powershell
git add apps/api/src/main/java/com/talon/ats/imports apps/api/src/test/java/com/talon/ats/imports apps/api/src/main/resources/application.yml
git commit -m "feat: expose candidate import preview API"
```

---

### Task 7: Share One Frontend API Client Across Auth and Jobs

**Files:**
- Modify: `apps/web/src/features/auth/runtimeAuthGateway.ts`
- Modify: `apps/web/src/features/auth/runtimeAuthGateway.test.ts`
- Create: `apps/web/src/features/jobs/httpJobGateway.ts`
- Create: `apps/web/src/features/jobs/httpJobGateway.test.ts`
- Modify: `apps/web/src/main.tsx`

**Interfaces:**
- Consumes: `ApiClient`, `AuthGateway`, and `JobGateway`.
- Produces:

```ts
export function createRuntimeAuthGateway(
  environment: RuntimeAuthEnvironment,
  apiClient: ApiClient,
  fixtureFactory?: () => AuthGateway,
): AuthGateway;

export class HttpJobGateway implements JobGateway {
  constructor(private readonly client: ApiClient);
  listImportTargets(): Promise<readonly ImportTargetJob[]>;
}
```

- [ ] **Step 1: Write failing shared-client and job mapping tests**

Log in through `HttpAuthGateway` using one `ApiClient`, then call `HttpJobGateway.listImportTargets()` and assert the second request contains `Authorization: Bearer <access-token>`. Also assert malformed job UUID/status/shape rejects with a safe error rather than reaching the UI.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
npm --workspace @talon/web run test -- src/features/auth/runtimeAuthGateway.test.ts src/features/jobs/httpJobGateway.test.ts
```

Expected: tests fail because runtime auth owns a separate client and `HttpJobGateway` is absent.

- [ ] **Step 3: Refactor runtime composition**

Construct exactly one `ApiClient(import.meta.env.VITE_API_BASE_URL, fetch)` in `main.tsx`. In explicit `DEV && VITE_AUTH_MODE === 'fixture'`, compose all fixture gateways. Otherwise pass the same client to `HttpAuthGateway`/runtime auth, `HttpJobGateway`, and the Task 8 import gateway. Keep candidates on their existing fixture gateway until confirmation creates real applications.

- [ ] **Step 4: Implement defensive job mapping**

Call `client.request('/api/v1/jobs', {method: 'GET'}, true)`, require an array of `{id,title,department,location,status}`, and accept only `OPEN`/`ON_HOLD`. Translate 401/403/5xx/malformed JSON to the existing safe UI error path without logging response bodies.

- [ ] **Step 5: Run focused and app tests**

```powershell
npm --workspace @talon/web run test -- src/features/auth/runtimeAuthGateway.test.ts src/features/jobs/httpJobGateway.test.ts src/app/App.test.tsx
```

Expected: shared bearer and existing application tests pass.

- [ ] **Step 6: Commit shared runtime composition**

```powershell
git add apps/web/src/features/auth apps/web/src/features/jobs apps/web/src/main.tsx
git commit -m "feat: load import jobs through authenticated API"
```

---

### Task 8: Connect the Frontend Import Wizard to the HTTP API

**Files:**
- Create: `apps/web/src/features/imports/httpImportGateway.ts`
- Create: `apps/web/src/features/imports/httpImportGateway.test.ts`
- Modify: `apps/web/src/features/imports/importGateway.ts`
- Modify: `apps/web/src/main.tsx`
- Modify: `apps/web/src/features/imports/ImportWizard.test.tsx`

**Interfaces:**
- Consumes: `ApiClient` and the existing `ImportGateway` presentation contract.
- Produces:

```ts
export class HttpImportGateway implements ImportGateway {
  constructor(private readonly client: ApiClient);
  uploadCsv(input: {jobId: string; file: File}): Promise<ImportDraft>;
  validate(input: {
    importId: string;
    mapping: ColumnMapping;
    retainUnmapped: boolean;
  }): Promise<ImportPreview>;
  getPreview(importId: string): Promise<ImportPreview>;
}
```

Add `getPreview(importId: string): Promise<ImportPreview>` to `ImportGateway`. The future `confirm`,
`getImport`, `retryRow`, and `downloadErrors` methods remain explicit deferred-operation rejections
and must not be reachable in this checkpoint's wizard state.

- [ ] **Step 1: Write failing multipart/upload tests**

Assert `uploadCsv` creates `FormData` with the original `File` and selected `jobId`, does not set a manual multipart content type, calls `/api/v1/imports` authenticated, and defensively validates `id`, `jobId`, `fileName`, `rowCount`, `sourceColumns`, `suggestedMapping`, and `status === 'UPLOADED'`.

- [ ] **Step 2: Write failing validate/preview/problem tests**

Assert validate sends:

```json
{"mapping":{"first_name":"first_name"},"retainUnmapped":false}
```

to `/api/v1/imports/{encoded-id}/validate`, maps issue `rowNumber`, `kind`, `code`, and safe `message`, and rejects malformed counts or unknown kinds. Assert `getPreview` performs authenticated `GET /api/v1/imports/{encoded-id}/preview` through the same response validator. Map backend stable problem codes to `ImportProblem`; convert network/non-JSON failures to `API_UNAVAILABLE` without exposing response bodies.

- [ ] **Step 3: Run the focused tests and verify failure**

```powershell
npm --workspace @talon/web run test -- src/features/imports/httpImportGateway.test.ts src/features/imports/ImportWizard.test.tsx
```

Expected: tests fail because `HttpImportGateway` is absent.

- [ ] **Step 4: Implement the adapter and normal runtime selection**

Use authenticated `ApiClient.request()` for upload, validate, and preview. Encode path IDs, validate all response fields before returning them, add `JOB_NOT_IMPORTABLE` to the problem union, and include preview issue `code`. In `main.tsx`, use `HttpImportGateway(sharedClient)` outside explicit fixture mode.

- [ ] **Step 5: Verify strict read-only mapping UI behavior**

Keep the detected mapping visible but non-editable. Assert upload success advances to mapping preview, validate advances to counts/issues, and refresh restoration invokes `GET /api/v1/imports/{id}/preview` only through React state/routing—not Local Storage or Session Storage.

- [ ] **Step 6: Run frontend gates**

```powershell
npm run format:web
npm run lint:web
npm run test:web
npm run build:web
```

Expected: formatting, lint, all Vitest tests, TypeScript, and Vite production build pass.

- [ ] **Step 7: Commit the import HTTP adapter**

```powershell
git add apps/web/src/features/imports apps/web/src/main.tsx
git commit -m "feat: connect strict CSV import preview"
```

---

### Task 9: Verify the Full Slice and Record the Handoff

**Files:**
- Modify: `docs/implementation/backend-api-handoff.md`
- Modify: `docs/implementation/frontend-web-handoff.md`
- Modify: `docs/architecture/api-design.md`
- Modify: `docs/plans/talon-ats-implementation-plan.md` only to record observed checkpoint status
- Create: `apps/web/playwright.config.ts`
- Create: `apps/web/e2e/import-preview.spec.ts`
- Modify: `apps/web/package.json`

**Interfaces:**
- Consumes: Tasks 1-8 and a synthetic CSV containing no real candidate PII.
- Produces: reproducible evidence for local PostgreSQL/Testcontainers, optional Supabase smoke, real browser/API integration, and the exact AWS/S3 follow-up.

- [ ] **Step 1: Run backend formatting and the full test gate**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
mvn "-Dmaven.repo.local=E:\maven-repo" -f apps\api\pom.xml spotless:apply verify
```

Expected: Maven exits 0; record the observed test count. A Docker/Testcontainers outage is a blocker, not a pass.

- [ ] **Step 2: Run the optional Supabase portability smoke only when `.env.supabase` is locally present**

Load its variables into the process without printing them, then run the existing `supabase-smoke` profile. Expected: Flyway V4 applies and persistence smoke tests pass against the shared pooler. If network/account access blocks it, record the exact blocker without exposing credentials and keep the local PostgreSQL gate authoritative.

- [ ] **Step 3: Run frontend gates from the frontend worktree**

```powershell
npm run format:check:web
npm run lint:web
npm run test:web
npm run build:web
```

Expected: every command exits 0; record observed test/build results.

- [ ] **Step 4: Run a real browser smoke with synthetic data**

Add `@playwright/test` as a pinned development dependency, configure `baseURL` from
`PLAYWRIGHT_BASE_URL` with `http://127.0.0.1:5173` as the local default, and retain trace only on
failure. Start the API with PostgreSQL and private local storage, start Vite on
`127.0.0.1:5173`, then run `npx playwright test e2e/import-preview.spec.ts` to:

1. sign in with the local demo recruiter;
2. open candidate import;
3. select an existing active job;
4. download and inspect `talon-candidate-import.csv`;
5. upload a synthetic strict CSV;
6. verify the read-only recognition ledger;
7. validate and see valid/invalid/duplicate counts;
8. navigate away/back through the supported in-app route and restore preview from the API.

Expected: all network requests use `/api/v1`, jobs/imports include the bearer token, no auth/session/CSV data appears in browser storage, and no Drive fetch occurs.

- [ ] **Step 5: Review provider portability explicitly**

Use `rg` to confirm `ImportDraftService` and `ImportController` do not import `LocalObjectStorage`, S3 SDK types, or Supabase types. Confirm object keys are opaque, source files are not web-served, and no public-bucket ACL/configuration exists. Record private S3 adapter + IAM + ECS/Terraform wiring as the next infrastructure checkpoint, not as completed work.

- [ ] **Step 6: Update living implementation records**

For both handoffs record scope/status, medium-high-level changes, rationale, important flows, affected modules, exact verification commands/results, blockers/prerequisites, and the next step. Standardize architecture references to `/api/v1/imports`; do not claim confirm/Drive/S3/search behavior exists.

- [ ] **Step 7: Commit verification and documentation**

```powershell
git add docs/implementation docs/architecture/api-design.md docs/plans/talon-ats-implementation-plan.md apps/web/playwright.config.ts apps/web/e2e/import-preview.spec.ts apps/web/package.json package-lock.json
git commit -m "docs: record strict import preview checkpoint"
```

Expected: only reviewed documentation/e2e changes are staged; environment files, build output, Terraform state, candidate data, and `initial_requirements.txt` remain unstaged.

---

## Next Checkpoint After This Plan

Implement import confirmation and the durable same-deployment worker using the existing queue port, then connect the already-built rate-limited public Google Drive adapter and quarantined file-processing pipeline. The AWS checkpoint supplies the `ObjectStorage` port with a private S3 adapter, ECS task IAM, Secrets Manager references, networking, and Terraform variables; it must preserve the same import HTTP/domain behavior and never enable a public bucket. Natural-language search remains a separate approved design: Grok translates text to a restricted application-owned DSL, and the backend validates that DSL before PostgreSQL filtering/sorting.
