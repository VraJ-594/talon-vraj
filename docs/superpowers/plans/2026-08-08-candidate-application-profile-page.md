# Candidate Application Profile Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every database-backed candidate application open as a reliable, deep-linkable profile page, including imported records with nullable fields.

**Architecture:** Wouter routes candidate rows to a protected application-detail path. A focused page component owns detail loading/retry/download state and reuses the existing profile presentation; the HTTP gateway remains the only browser boundary to the authenticated Talon API.

**Tech Stack:** React 19, TypeScript, Wouter, Vitest/Testing Library, existing Spring Boot candidate application API.

## Global Constraints

- Do not change import history/recovery or Terraform in this slice.
- Do not weaken clean-only resume delivery or workspace/RLS authorization.
- Treat nullable database values as explicit nullable browser contract fields.
- Follow witnessed red-green tests for every behavior change.
- Leave all changes uncommitted and unstaged per the user's live-build instruction.

---

### Task 1: Nullable detail contract and profile rendering

**Files:**
- Modify: `apps/web/src/features/candidates/candidateGateway.ts`
- Modify: `apps/web/src/features/candidates/CandidateProfilePanel.tsx`
- Test: `apps/web/src/features/candidates/CandidateWorkspace.test.tsx` or the new page test from task 2

**Interfaces:**
- Consumes: `CandidateApplicationDetail` returned by `HttpCandidateGateway.getApplication`.
- Produces: safe rendering for `availableFrom: string | null` and nullable compensation.

- [x] **Step 1: Add a failing component test using an imported application with `availableFrom: null`, null compensation, blank source, no answers, and no resume**
- [x] **Step 2: Run the focused test and observe the date/compensation rendering failure**
- [x] **Step 3: Change the TypeScript contract to explicit nullable fields and render “Not provided” without calling formatters for null values**
- [x] **Step 4: Re-run the focused test and observe it pass**

### Task 2: Dedicated protected application profile route

**Files:**
- Create: `apps/web/src/features/candidates/CandidateApplicationProfilePage.tsx`
- Create: `apps/web/src/features/candidates/CandidateApplicationProfilePage.test.tsx`
- Modify: `apps/web/src/features/candidates/CandidateWorkspace.tsx`
- Modify: `apps/web/src/app/App.tsx`
- Modify: `apps/web/src/app/App.test.tsx`
- Modify: `apps/web/src/styles.css`

**Interfaces:**
- Consumes: `candidateGateway.getApplication(applicationId)` and `downloadResume(applicationId)`.
- Produces: `/candidates/applications/{applicationId}`, Back to candidates, retry, and clean-only download.

- [x] **Step 1: Add failing tests proving a roster selection navigates to the application URL and a direct protected URL loads the correct profile**
- [x] **Step 2: Add tests for loading, retry, back navigation, and an invalid identifier**
- [x] **Step 3: Run the focused candidate/App tests and observe missing-route/page failures**
- [x] **Step 4: Implement the focused page, route classification, session-restore preservation, sidebar active state, and roster links**
- [x] **Step 5: Add compact full-page profile spacing while preserving current Talon visual tokens and responsive behavior**
- [x] **Step 6: Re-run focused tests and observe them pass**

### Task 3: Verification and handoff

**Files:**
- Modify: `docs/implementation/priority-import-export-search.md`

**Interfaces:**
- Consumes: completed profile route and observed verification output.
- Produces: exact live manual-test instructions and deferred Drive/S3/history boundary.

- [x] **Step 1: Run all candidate/App frontend tests**
- [x] **Step 2: Run `npm run lint:web`, `npm run test:web -- --run`, and `npm run build:web`**
- [x] **Step 3: Run `git diff --check` and confirm no candidate PII, credentials, staged files, commit, or push were introduced**
- [x] **Step 4: Confirm the live API and Vite profile URL are available; leave authenticated profile clicking as the explicit user manual gate**
- [x] **Step 5: Record only observed results and remaining external/manual prerequisites in the implementation handoff**
