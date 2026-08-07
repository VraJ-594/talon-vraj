# Backend: Identity and Workspace

- Status: In progress
- Branch: `codex/backend-api`
- Started: 2026-08-07
- Plan task: Task 2 in `docs/plans/talon-ats-implementation-plan.md`

## Scope

Implement the backend identity/workspace module in test-driven checkpoints: first-user workspace bootstrap, fixed roles, invitations, request principal resolution, persistence, PostgreSQL tenant isolation, Cognito JWT validation, and versioned REST contracts.

The frontend is owned by the separate `codex/frontend-web` workstream. This backend branch owns `/api/v1` contracts, Java modules, Flyway migrations, persistence adapters, and OpenAPI output.

## Checkpoint 1: First workspace bootstrap

### Why

An authenticated identity does not receive ATS data access merely by existing in Cognito. The first valid tenant boundary is an atomic bootstrap that creates the application user, workspace, and initial administrator membership together. Keeping this rule behind a persistence port makes it testable before Docker is available and lets PostgreSQL later enforce the same invariants transactionally.

### Planned design

- Pure application service under `com.talon.ats.identity`.
- Command carries verified identity claims plus workspace display configuration; it does not accept an arbitrary workspace ID or role.
- The service normalizes identity/workspace inputs and creates `WORKSPACE_ADMIN` membership only.
- IDs and time enter through injectable ports for deterministic tests.
- One atomic repository command persists the bootstrap result; a later PostgreSQL adapter owns the transaction.
- Existing membership rejects repeat first-workspace bootstrap before persistence.

### Implemented

- Added the `com.talon.ats.identity` Spring Modulith boundary with separate `application` and `domain` packages.
- Added immutable user, workspace, membership, role/status, and atomic bootstrap value types.
- Added `WorkspaceBootstrapService` with injected ID generation and clock ports so production adapters can use UUID/time sources while unit tests remain deterministic.
- Normalized Cognito subject whitespace, email casing, workspace slug, and time-zone validation at the application boundary.
- Fixed the initial membership to `WORKSPACE_ADMIN` and the retention default to 24 months; callers cannot choose a privileged role through the bootstrap command.
- Added a single `IdentityWorkspaceBootstrapStore.save(WorkspaceBootstrap)` operation so the future PostgreSQL adapter must persist the three related records as one transaction rather than exposing partial-save steps.
- Added repeat-bootstrap rejection based on existing identity membership.

### Verification evidence

| Check | Result |
|---|---|
| Focused red phase | Failed compilation because identity application/domain packages did not exist, as intended |
| Focused green phase | 2 workspace-bootstrap tests passed |
| Full Maven verification | 5 tests passed; executable JAR packaging and Spotless verification passed |
| Spring Modulith verification | Passed with the new identity module boundary |

The tests prove normalized atomic bootstrap data, administrator membership linkage, deterministic timestamps/IDs, and rejection without persistence when the identity already has a membership. Persistence transactionality and database uniqueness remain integration responsibilities and are not claimed yet.

## Environment dependency

Docker Desktop/CLI remains unavailable to the shell. PostgreSQL schema, RLS, Flyway, Compose, and Testcontainers verification must remain pending until Docker is launched from its existing E-drive installation or its executable path is supplied.

## Next steps

1. Add invitation expiry, email binding, single-use acceptance, and fixed-role policy tests.
2. Add request-principal/session resolution contracts without coupling them to HTTP transport.
3. Add Flyway schema and PostgreSQL adapters only after Docker is reachable, then prove transactionality and RLS with integration tests.
4. Add Cognito/local identity adapters and REST endpoints after the application contracts are stable.
