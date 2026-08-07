# ADR 0006: Supabase-Hosted PostgreSQL

- Status: Accepted
- Date: 2026-08-07

## Context

The project must avoid AWS RDS while retaining PostgreSQL features such as transactions, Flyway migrations, full-text search, `pg_trgm`, and row-level security. The first demo should minimize recurring cost, but the production architecture must not depend on a database that pauses after inactivity or lacks automated backups.

## Decision

Use Supabase-hosted PostgreSQL as the managed database. Development and the first demo may use the Supabase Free plan. Production launch is gated on upgrading the same project to a paid, non-pausing tier with automated backups/PITR, or receiving approval for another managed PostgreSQL service with equivalent recovery controls.

The application connects through the TLS-enabled Supavisor session pooler on port 5432 because ECS runs a persistent JVM workload and may require IPv4 connectivity. It uses standard JDBC/JPA, PostgreSQL SQL, and Flyway only. Supabase Auth, Storage, Edge Functions, and generated Data APIs are not application dependencies.

Terraform uses the official `supabase/supabase` provider to create or import the project and configure supported settings. Because the provider is currently pre-GA and some secrets can enter encrypted state, project creation may be an explicit bootstrap/import step if provider stability or organizational policy requires it. Runtime database credentials are copied to AWS Secrets Manager and never committed.

## Consequences

- AWS RDS, RDS subnets, RDS security groups, and RDS alarms are removed.
- Database traffic crosses the AWS boundary; TLS, egress controls, regional placement, vendor terms, and data residency require review.
- Supabase Free is suitable for development/demo but is not represented as production-ready.
- Local development and deterministic integration tests continue to use Docker/Testcontainers PostgreSQL.
- Avoiding Supabase-specific application APIs keeps a future PostgreSQL migration low-risk.
