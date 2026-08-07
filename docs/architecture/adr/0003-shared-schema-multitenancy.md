# ADR 0003: Shared-Schema Multi-Tenancy

- Status: Accepted
- Date: 2026-08-07

## Context

The product should support multiple companies without operating a database per small tenant. Tenant leakage would be a critical security failure.

## Decision

Use one PostgreSQL schema with mandatory `workspace_id` on tenant-owned rows. Enforce workspace scope in service authorization, repository queries, relational constraints/indexes, PostgreSQL RLS, and S3 key prefixes. Never deduplicate candidates across workspaces.

## Consequences

- Onboarding and operations remain efficient.
- Every data access path must carry tenant context and receive negative isolation tests.
- Large tenants can later be partitioned or moved using explicit workspace keys.
- Database-per-tenant customizations are excluded from v1.
