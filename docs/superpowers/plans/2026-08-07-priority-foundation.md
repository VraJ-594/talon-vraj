# Priority ATS Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Cognito-oriented scaffold with minimal application-owned authentication and add the workspace, job, candidate, and application foundation required by import/export and search.

**Architecture:** The Spring Boot modular monolith owns password verification, JWT/refresh sessions, roles, and PostgreSQL persistence. Only Admin/Recruiter priority operations are exposed. Domain/application ports remain independent of Spring Security and PostgreSQL adapters.

**Tech Stack:** Java 21, Spring Boot 3.5.4, Spring Security, Nimbus JOSE through Spring Security, BCrypt, PostgreSQL 17, Flyway, Spring JDBC/JPA, JUnit 5, AssertJ, Testcontainers.

## Global Constraints

- No Cognito, OAuth, TOTP, sign-up, password reset, or invitation HTTP flows.
- Never commit a password, BCrypt hash, signing key, token, or candidate PII.
- One demo Admin is provisioned only through secret environment configuration.
- Access JWT lifetime is 15 minutes; hashed refresh sessions expire after seven days.
- All tenant tables use non-null `workspace_id`; PostgreSQL RLS is mandatory before database completion.
- Every behavior follows red, green, refactor, full verification, handoff update, and narrow commit.

---

### Task 1: Align architecture and delivery documentation

**Files:**
- Modify: `docs/plans/talon-ats-implementation-plan.md`
- Modify: `docs/architecture/hld.md`
- Modify: `docs/architecture/lld.md`
- Modify: `docs/architecture/api-design.md`
- Modify: `docs/architecture/database-design.md`
- Modify: `docs/architecture/security-threat-model.md`
- Modify: `docs/architecture/deployment-and-testing.md`
- Modify: `docs/architecture/adr/0005-external-provider-ports.md`
- Create: `docs/architecture/adr/0007-priority-scope-and-application-auth.md`
- Create: `docs/architecture/adr/0008-private-candidate-file-transfer.md`
- Create: `docs/architecture/adr/0009-validated-dual-mode-search.md`
- Create: `docs/implementation/priority-import-export-search.md`

**Interfaces:**
- Consumes: approved design spec.
- Produces: one consistent source of truth and living implementation handoff.

- [ ] Replace immediate Cognito/2FA and broad feature delivery language with minimal application authentication followed by import/export and search.
- [ ] Remove all ZIP/archive import requirements and mark unrelated product modules deferred.
- [ ] Record private S3, public-Drive demo limitation, durable worker, and Grok-to-DSL decisions in ADRs.
- [ ] Run `rg -n -i "CSV/ZIP|ZIP candidate|Cognito resource|TOTP enrollment" docs/architecture docs/plans` and verify every remaining mention is historical/deferred rather than an active requirement.
- [ ] Update the implementation handoff and commit `docs: prioritize import export and search`.

### Task 2: Refactor identity model to application-owned accounts

**Files:**
- Modify: `apps/api/src/main/java/com/talon/ats/identity/domain/AppUser.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/application/BootstrapWorkspaceCommand.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/application/WorkspaceBootstrapService.java`
- Modify: `apps/api/src/main/java/com/talon/ats/identity/application/IdentityWorkspaceBootstrapStore.java`
- Modify: `apps/api/src/test/java/com/talon/ats/identity/WorkspaceBootstrapServiceTests.java`

**Interfaces:**
- Consumes: a normalized email and externally supplied BCrypt hash from controlled provisioning.
- Produces: `AppUser(id, email, normalizedEmail, displayName, passwordHash, status, createdAt, lastLoginAt)` and email-scoped bootstrap lookup.

- [ ] Update the bootstrap test first to expect normalized email/password-hash account data and `hasMembershipByEmail`.
- [ ] Run `mvn -Dmaven.repo.local=E:\maven-repo -f apps/api/pom.xml -Dtest=WorkspaceBootstrapServiceTests test` and verify compilation/assertion failure references the old Cognito contract.
- [ ] Refactor the minimum domain/application types without adding an HTTP sign-up route.
- [ ] Run the focused test and verify it passes.
- [ ] Run `mvn -Dmaven.repo.local=E:\maven-repo -f apps/api/pom.xml spotless:apply verify` and commit `refactor: use application owned identity accounts`.

### Task 3: Add login and refresh-session application contracts

**Files:**
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/AuthenticateCommand.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/AuthenticationResult.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/AuthenticationService.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/IdentityAccountStore.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/PasswordVerifier.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/application/TokenIssuer.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/domain/RefreshSession.java`
- Create: `apps/api/src/test/java/com/talon/ats/identity/AuthenticationServiceTests.java`

**Interfaces:**
- Consumes: normalized email, raw request password, clock, token issuer, refresh-session store.
- Produces: short-lived access token metadata and one raw refresh token returned only to transport.

- [ ] Write failing tests for valid login, generic invalid email/password, suspended account, refresh-token hashing, and seven-day expiry.
- [ ] Run the focused tests and verify missing application types are the failure reason.
- [ ] Implement constant-path password verification using a dummy BCrypt hash for absent users and never expose account-existence differences.
- [ ] Run focused tests, then the full Maven verification.
- [ ] Update the handoff and commit `feat: add application authentication contracts`.

### Task 4: Add Spring Security and JWT adapters

**Files:**
- Modify: `apps/api/pom.xml`
- Create: `apps/api/src/main/java/com/talon/ats/identity/infrastructure/security/SecurityConfiguration.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/infrastructure/security/BCryptPasswordVerifier.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/infrastructure/security/JwtTokenIssuer.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/api/AuthController.java`
- Create: `apps/api/src/main/java/com/talon/ats/identity/api/AuthDtos.java`
- Create: `apps/api/src/test/java/com/talon/ats/identity/AuthControllerTests.java`

**Interfaces:**
- Consumes: `AuthenticationService`, refresh session facade, signing secret/issuer/audience configuration.
- Produces: `/api/v1/auth/login|refresh|logout` and `/api/v1/session`.

- [ ] Add Spring Security starter, OAuth2 JOSE support, and security-test dependencies managed by Spring Boot.
- [ ] Write MockMvc tests for public health/login, protected session, generic 401, refresh cookie flags, logout revocation, and Admin role claims.
- [ ] Implement stateless bearer access validation and strict refresh-cookie handling.
- [ ] Run controller tests and full Maven verification.
- [ ] Update the handoff and commit `feat: expose basic authentication API`.

### Task 5: Add PostgreSQL identity, job, candidate, and application schema

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V2__priority_identity_candidate_schema.sql`
- Create: `apps/api/src/test/java/com/talon/ats/identity/IdentityPersistenceIntegrationTests.java`
- Create: `apps/api/src/test/java/com/talon/ats/candidates/CandidateRlsIntegrationTests.java`
- Create: `apps/api/src/main/java/com/talon/ats/jobs/**`
- Create: `apps/api/src/main/java/com/talon/ats/candidates/**`

**Interfaces:**
- Produces: tenant-safe repositories and minimal job/candidate/application facades used by later plans.

- [ ] Write Testcontainers tests for migration, normalized account uniqueness, refresh revocation, candidate-email uniqueness, one application per job, missing/wrong/correct RLS context, and compensation minor-unit checks.
- [ ] Confirm tests fail before the migration/adapters exist.
- [ ] Implement forward migration, RLS policies, tenant transaction context, and repository adapters.
- [ ] Run integration tests with Docker, then all Maven tests.
- [ ] Update the handoff and commit `feat: add priority ATS persistence foundation`.
