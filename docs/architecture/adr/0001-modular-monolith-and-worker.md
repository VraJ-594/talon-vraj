# ADR 0001: Spring Boot Modular Monolith with Worker Profile

- Status: Accepted
- Date: 2026-08-07

## Context

The ATS contains multiple workflows but targets one product, one team, and a small initial load. Imports, AI, email, and calendar work need asynchronous reliability without forcing distributed transactions.

## Decision

Use one Spring Boot codebase and PostgreSQL database. Enforce domain modules through public facades and architecture tests. Build one container image that runs as an API or SQS worker profile. Persist outbox events in the same transaction as domain state.

## Consequences

- Transactions and local development remain simple.
- API and background capacity can scale separately.
- Module/event boundaries provide later extraction points.
- A shared release coordinates all backend modules.
- Independent service deployment is deliberately unavailable until measured need justifies it.
