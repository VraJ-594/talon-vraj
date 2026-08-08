# Candidate Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide deterministic Cmd+K search and Groq-translated natural-language candidate/application search through one validated, tenant-safe DSL.

**Architecture:** An application-owned DSL validator and allowlisted compiler generate parameterized PostgreSQL queries. Groq receives only the user sentence plus DSL schema and cannot emit SQL. Keyword/filter search remains independent of Groq.

**Tech Stack:** Java 21, Spring Boot, Spring JDBC, PostgreSQL `tsvector`/`pg_trgm`, Groq HTTP adapter, JUnit/Testcontainers, React/Vitest/Playwright in the parallel frontend workstream.

## Global Constraints

- Search candidates/applications and jobs only.
- Cmd+K never calls Groq.
- Natural query maximum is 500 characters, timeout 3 seconds, rate 10/user/minute.
- Compensation filters/results require Admin or Recruiter.
- Groq never receives candidate data and never controls SQL identifiers/fragments.
- No OpenSearch, embeddings, vector database, or cross-currency conversion.

---

### Task 1: Search DSL and authorization

**Files:** Create `apps/api/src/main/java/com/talon/ats/search/domain/**`, `application/SearchDslValidator.java`, and `apps/api/src/test/java/com/talon/ats/search/SearchDslValidatorTests.java`.

**Interfaces:** Produces enums/value types for approved fields, operators, sorts, typed values, pagination, warnings, and validated query.

- [ ] Write failing parameterized tests for every allowed field/operator combination, invalid values, LPA normalization, bounds, compensation denial, and deterministic sort tie-breakers.
- [ ] Implement exhaustive allowlist validation.
- [ ] Run focused/full verification and commit `feat: add validated candidate search DSL`.

### Task 2: PostgreSQL search documents and compiler

**Files:** Create Flyway search migration, `search/infrastructure/postgres/**`, compiler/query tests, and representative fixtures.

**Interfaces:** Consumes only `ValidatedCandidateSearch`; produces cursor-paged display-safe projections through parameterized SQL.

- [ ] Test workspace-first/RLS behavior, injection payloads as values, full-text/trigram matching, structured filters, stable cursors, and role projections.
- [ ] Add `tsvector`, `pg_trgm`, composite/partial indexes, and bounded compiler.
- [ ] Verify query plans with `EXPLAIN (ANALYZE, BUFFERS)` and commit `feat: add PostgreSQL candidate search`.

### Task 3: Cmd+K search API

**Files:** Create search controller/DTOs and MockMvc/integration tests.

**Interfaces:** Implements `GET /api/v1/search?q=&types=&cursor=` without any AI dependency.

- [ ] Test candidates/jobs, permissions, empty/minimum query, pagination, typo matching, and provider independence.
- [ ] Implement endpoint and run full verification.
- [ ] Commit `feat: add deterministic ATS command search`.

### Task 4: Groq interpretation adapter

**Files:** Create `NaturalLanguageSearchInterpreter` port, Groq adapter, schema prompt/response DTOs, configuration, and contract tests.

**Interfaces:** Consumes query text plus fixed schema; produces an untrusted DSL candidate that must pass `SearchDslValidator`.

- [ ] Test valid CTC/location/experience/date interpretations, unknown JSON rejection, forbidden fields, timeout, quota, malformed output, disabled provider, and no candidate-data request body.
- [ ] Implement strict structured-output request, 3-second timeout, and stable failures.
- [x] Run full verification and commit `feat: translate natural language search with Groq`.

### Task 5: Interpret and candidate-search APIs

**Files:** Create `/search/interpret` and `/candidate-search` controller handlers, rate limiter, problem mappings, and tests.

**Interfaces:** Returns editable filter chips/warnings and executes only validated DSL.

- [ ] Test 500-character/10-per-minute bounds, provider fallback response, no silent execution, compensation role denial before SQL, and raw-query log redaction.
- [ ] Implement endpoints, run full backend verification, and commit `feat: expose natural language candidate search`.

### Task 6: End-to-end priority search verification

**Files:** Update backend/frontend handoffs and Playwright fixtures/tests owned by the frontend branch.

- [ ] Prove Admin sign-in, Cmd+K, `< 40 LPA` interpretation, editable chips, filtered results, Groq failure fallback, and CSV export filter reuse.
- [ ] Record PostgreSQL plan/latency evidence and provider-disabled behavior.
- [ ] Commit `test: verify priority candidate search flow`.
