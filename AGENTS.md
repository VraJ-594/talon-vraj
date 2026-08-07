# Talon ATS Working Agreement

## Source of truth

- Product and architecture decisions live under `docs/architecture/`.
- Delivery order and acceptance gates live in `docs/plans/talon-ats-implementation-plan.md`.
- Every implementation phase must have a living handoff document under `docs/implementation/`.

## Required implementation record

Before ending an implementation session, update the current phase document with:

1. Scope and current status.
2. What changed, at a medium-high level.
3. Why the approach was chosen.
4. How the important paths work.
5. Files or modules affected.
6. Verification commands and observed results.
7. Known blockers, external prerequisites, and the exact next step.

Record only evidence observed in the current working tree. Do not mark a phase complete while required checks are blocked or failing.

## Engineering workflow

- Use test-first development for behavior changes.
- Keep provider integrations behind application-owned ports.
- Keep application code PostgreSQL-portable; do not introduce Supabase Auth, Storage, Edge Function, or Data API coupling.
- Never commit credentials, local environment files, generated build output, Terraform state, or candidate PII.
- Use forward Flyway migrations for database changes.
