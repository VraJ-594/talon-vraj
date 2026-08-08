# Database-Backed Candidate Roster Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the live candidate fixture with tenant-scoped application APIs, add safe resume delivery, seed 36 synthetic Supabase records, and clarify the two-stage AI search UI.

**Architecture:** The candidates module owns roster/detail query contracts and a JDBC adapter that executes under the verified JWT workspace and PostgreSQL RLS. React consumes those contracts through `HttpCandidateGateway`; search remains a separate deterministic/interpretation boundary over the same tables.

**Tech Stack:** Java 21, Spring Boot MVC/Security/JDBC, PostgreSQL/Flyway, React 19, TypeScript, Vitest/Testing Library, Groq Chat Completions adapter.

## Global Constraints

- Runtime development and production paths use authenticated Talon APIs; fixtures are test helpers only.
- Workspace ownership comes only from the verified JWT and transaction-local RLS context.
- No Supabase Auth, Storage, Edge Function, or Data API coupling.
- No credentials, real candidate PII, Drive URLs, or fake clean resume metadata enter seed data.
- Every behavior change follows a witnessed red-green test cycle.

---

### Task 1: Candidate roster application contract

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/candidates/application/CandidateApplicationQueryStore.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/application/CandidateApplicationQueryService.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/application/CandidateApplicationPage.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/application/CandidateApplicationSummary.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/application/CandidateApplicationDetail.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/application/CandidateResume.java`
- Test: `apps/api/src/test/java/com/talon/ats/candidates/CandidateApplicationQueryServiceTests.java`

**Interfaces:**
- Consumes: `Actor(UUID userId, UUID workspaceId, WorkspaceRole role)`, opaque cursor, bounded limit, and application UUID.
- Produces: `list(Actor, String, int)`, `detail(Actor, UUID)`, and `resume(Actor, UUID)` with safe not-found/not-clean failures.

- [x] **Step 1: Write service tests that fail because the query service does not exist**

```java
assertThat(service.list(actor, null, 50).items()).containsExactly(summary);
assertThatThrownBy(() -> service.detail(otherWorkspaceActor, applicationId))
    .isInstanceOf(CandidateQueryException.class);
assertThatThrownBy(() -> service.resume(actor, applicationWithoutCleanResume))
    .hasMessageContaining("not available");
```

- [x] **Step 2: Run `mvn -f apps/api/pom.xml -Dtest=CandidateApplicationQueryServiceTests test` and observe compilation failure for the missing contract**
- [x] **Step 3: Add immutable projection records, Admin/Recruiter authorization, limit range `1..100`, opaque cursor validation, and safe exception codes**
- [x] **Step 4: Re-run the focused test and observe all service cases pass**

### Task 2: Tenant-scoped persistence and HTTP API

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/candidates/infrastructure/persistence/JdbcCandidateApplicationQueryStore.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/api/CandidateApplicationController.java`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/api/CandidateApplicationProblemHandler.java`
- Modify: `apps/api/src/main/java/com/talon/ats/candidates/infrastructure/CandidateRuntimeConfiguration.java`
- Test: `apps/api/src/test/java/com/talon/ats/candidates/CandidateApplicationControllerTests.java`
- Test: `apps/api/src/test/java/com/talon/ats/candidates/SupabaseCandidateApplicationPersistenceIT.java`

**Interfaces:**
- Consumes: task 1 query service and the files module’s `ObjectStorage` port.
- Produces: `GET /api/v1/applications`, `GET /api/v1/applications/{id}`, and `GET /api/v1/applications/{id}/resume-download`.

- [x] **Step 1: Write controller tests for verified-JWT ownership, cursor page shape, detail redaction, safe problems, local streaming, and presigned redirects**

```java
mockMvc.perform(get("/api/v1/applications").with(actorJwt()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.items[0].applicationId").value(APPLICATION_ID.toString()))
    .andExpect(jsonPath("$.workspaceId").doesNotExist());
```

- [x] **Step 2: Run the focused controller test and observe failure because the endpoints are absent**
- [x] **Step 3: Implement parameterized newest-first seek pagination over `application`, `candidate`, `job`, and `candidate_file`; call `set_config` plus `SET LOCAL ROLE talon_app` in every read transaction**
- [x] **Step 4: Implement controller mapping and clean-only download: redirect when storage can presign, otherwise stream from the application-owned storage port**
- [x] **Step 5: Run focused unit/controller tests, then the Supabase query integration test, and record the exact observed results**

### Task 3: Real frontend candidate gateway and roster pagination

**Files:**
- Create: `apps/web/src/features/candidates/httpCandidateGateway.ts`
- Create: `apps/web/src/features/candidates/httpCandidateGateway.test.ts`
- Modify: `apps/web/src/features/candidates/candidateGateway.ts`
- Modify: `apps/web/src/features/candidates/CandidateWorkspace.tsx`
- Modify: `apps/web/src/features/candidates/CandidateProfilePanel.tsx`
- Modify: `apps/web/src/features/candidates/CandidateWorkspace.test.tsx`
- Modify: `apps/web/src/main.tsx`

**Interfaces:**
- Consumes: task 2 JSON/page endpoints through authenticated `ApiClient`.
- Produces: `listApplications(cursor?) -> CandidateApplicationPage`, real profile loading, clean resume blob download, and an explicit Load more action.

- [x] **Step 1: Write HTTP gateway tests for authorization headers, page mapping, stable problem codes, detail, and binary resume response**
- [x] **Step 2: Write component tests for `NO_RESUME`, cumulative Load more results, and retry without fixture fallback**
- [x] **Step 3: Run focused Vitest files and observe failures from the missing HTTP gateway/page contract**
- [x] **Step 4: Implement the gateway, switch `main.tsx` to HTTP candidate runtime wiring, and add bounded roster pagination/no-resume copy**
- [x] **Step 5: Re-run focused tests and observe them pass**

### Task 4: Search action hierarchy and request-flow clarity

**Files:**
- Modify: `apps/web/src/features/search/SearchWorkspace.tsx`
- Modify: `apps/web/src/features/search/SearchWorkspace.test.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes: existing `SearchGateway.interpret` and `SearchGateway.query` methods.
- Produces: visually explicit `1 Describe`, `2 Review`, `3 Search` states without changing API semantics.

- [x] **Step 1: Add a failing UI test that requires separate “Build AI filters” and “Search candidates” actions plus the three-step guidance**
- [x] **Step 2: Run the focused Search workspace test and observe the missing guidance/action-label failure**
- [x] **Step 3: Refine the query composition area, primary/secondary button hierarchy, disabled/loading copy, filter review surface, and mobile/focus styles while preserving the existing Talon palette**
- [x] **Step 4: Re-run the focused test and observe deterministic search still bypasses interpretation while AI waits for explicit execution**

### Task 5: Idempotent Supabase demo seed and handoff

**Files:**
- Create: `scripts/supabase/seed-candidate-search-demo.sql`
- Modify: `docs/implementation/priority-import-export-search.md`
- Modify: `docs/architecture/api-design.md`

**Interfaces:**
- Consumes: an operator-reviewed existing workspace slug.
- Produces: 36 stable `.test` candidates, active jobs, and application rows with no fake file objects.

- [x] **Step 1: Write the SQL as one transaction with a guarded workspace lookup, stable UUIDs, `ON CONFLICT` behavior, tenant context, and a final count summary**
- [x] **Step 2: Validate the script against the current migration schema without persisting external rows; leave execution in the Supabase SQL Editor to the operator as requested**
- [x] **Step 3: Run backend `spotless:apply verify`, frontend lint/test/build, `git diff --check`, and a credential/PII scan**
- [x] **Step 4: Record only observed test, database, and manual evidence plus the exact live restart/manual-search next step in the implementation handoff**
