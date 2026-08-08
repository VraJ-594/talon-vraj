# Browser Session Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the authenticated Talon session across page refreshes without placing tokens in browser storage, and end it through server-backed logout.

**Architecture:** The access JWT stays in `ApiClient` memory. A browser-session HttpOnly refresh cookie is rotated atomically through the existing PostgreSQL `refresh_session` table; the HTTP frontend calls refresh during bootstrap and logout when the user signs out.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security resource server, Spring JDBC/PostgreSQL, React 19, TypeScript, Vitest, MockMvc.

## Global Constraints

- Refresh cookie: `HttpOnly`, `Secure`, `SameSite=Strict`, path `/api/v1/auth`, with no login/refresh `Max-Age` or `Expires`.
- Access JWT lifetime remains 15 minutes; server-side refresh safety lifetime remains seven days.
- No token or authenticated session enters Local Storage or Session Storage.
- Refresh rotation and replay-family revocation are atomic in PostgreSQL.
- The user explicitly approved implementation-first for this urgent fix; focused regression tests follow each implementation slice before final verification.
- Do not change OAuth, 2FA, password-reset, bulk-import, S3, or search behavior.

---

### Task 1: Atomic backend refresh-session rotation

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/RefreshSessionRejectedException.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/application/IdentityAccountStore.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/application/AuthenticationService.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/infrastructure/persistence/JdbcIdentityAccountStore.java`
- Test: `apps/api/src/test/java/com/talon/ats/identity/AuthenticationServiceTests.java`
- Test: `apps/api/src/test/java/com/talon/ats/identity/SupabaseIdentityPersistenceIT.java`

**Interfaces:**
- Produce `Optional<AuthenticationAccount> rotateRefreshSession(String currentTokenHash, UUID nextSessionId, String nextTokenHash, Instant nextExpiresAt, Instant rotatedAt)`.
- Produce `void revokeRefreshSessionFamily(String currentTokenHash, Instant revokedAt)`.
- Produce `AuthenticationResult AuthenticationService.refresh(String rawRefreshToken)` and `void logout(String rawRefreshToken)`.

- [ ] Implement store contracts and generic refresh rejection.
- [ ] Implement service token hashing/generation, atomic store rotation, access-token issuance, and family logout.
- [ ] Implement JDBC row locking, active account/membership validation, one-use consumption, child insertion, replay/invalid-state family revocation, and idempotent logout.
- [ ] Add service and Supabase persistence regression tests for success, expiry, replay, inactive identity, and family revocation.
- [ ] Run `mvn -Dmaven.repo.local=E:\maven-repo -f apps\api\pom.xml -Dtest=AuthenticationServiceTests,SupabaseIdentityPersistenceIT test` and require zero failures.

### Task 2: Refresh/logout HTTP cookie contract

**Files:**
- Modify: `apps/api/src/main/java/com/talon/ats/identity/api/AuthController.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/api/AuthenticationExceptionHandler.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/infrastructure/security/SecurityConfiguration.java`
- Test: `apps/api/src/test/java/com/talon/ats/identity/AuthControllerTests.java`

**Interfaces:**
- Produce `POST /api/v1/auth/refresh`, consuming `talon_refresh` and returning the login/session projection plus a rotated cookie.
- Produce idempotent `POST /api/v1/auth/logout`, revoking when possible and returning `204` plus an expired cookie.

- [ ] Change login to emit a browser-session cookie without persistent lifetime attributes.
- [ ] Add public refresh/logout controller routes and stable `SESSION_EXPIRED` handling.
- [ ] Permit only login, refresh, logout, and health anonymously in Spring Security.
- [ ] Add MockMvc regression coverage for cookie flags, absent/invalid refresh, rotation response, logout clearing, and anonymous route access.
- [ ] Run `mvn -Dmaven.repo.local=E:\maven-repo -f apps\api\pom.xml -Dtest=AuthControllerTests test` and require zero failures.

### Task 3: Frontend reload restoration and server logout

**Files:**
- Modify: `apps/web/src/features/auth/httpAuthGateway.ts`
- Modify: `apps/web/src/features/auth/httpAuthGateway.test.ts`
- Test: `apps/web/src/features/auth/AuthFlow.test.tsx`

**Interfaces:**
- `restoreSession()` calls `POST /api/v1/auth/refresh` when the `ApiClient` has no access token, installs the returned token, and returns its session projection.
- `logout()` calls `POST /api/v1/auth/logout` and clears memory after a successful response.

- [ ] Implement cookie-backed refresh restoration while preserving the existing in-memory `/session` path.
- [ ] Implement server logout and safe response mapping.
- [ ] Add gateway/UI regression tests for reload restoration, 401 signed-out behavior, no browser storage, network failure, and logout.
- [ ] Run `npm run test:web -- --run` and require zero failures.
- [ ] Run `npm run lint:web` and `npm run build:web` and require zero failures.

### Task 4: End-to-end verification and handoff

**Files:**
- Modify: `docs/implementation/priority-import-export-search.md`
- Modify: `docs/implementation/frontend-web-handoff.md`

- [ ] Run the full backend `spotless:apply verify` gate and record the test count.
- [ ] Apply/verify the existing schema against configured Supabase without printing credentials.
- [ ] Restart the local API, verify health, and manually smoke login → refresh → page reload → logout.
- [ ] Confirm browser Local Storage and Session Storage remain empty.
- [ ] Record changed modules, design rationale, observed commands/results, blockers, and exact next step in both living handoffs.
- [ ] Commit only reviewed source/docs and push `codex/backend-api`; never add ignored environment/runtime files or unrelated candidate data.
