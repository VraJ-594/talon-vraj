# Architecture Decision Records

- [0001: Modular Monolith and Worker](./0001-modular-monolith-and-worker.md)
- [0002: React/Vite and Spring Boot](./0002-react-vite-and-spring-boot.md)
- [0003: Shared-Schema Multi-tenancy](./0003-shared-schema-multitenancy.md)
- [0004: ECS Fargate](./0004-ecs-fargate.md)
- [0005: External Provider Ports](./0005-external-provider-ports.md)
- [0006: Supabase-Hosted PostgreSQL](./0006-supabase-hosted-postgresql.md)

ADRs capture decisions whose reversal changes system boundaries, security posture, deployment topology, or team workflow.

| ADR | Decision | Status |
|---|---|---|
| [0001](./0001-modular-monolith-and-worker.md) | Spring Boot modular monolith with worker profile | Accepted |
| [0002](./0002-react-vite-and-spring-boot.md) | React/Vite TypeScript and Spring Boot | Accepted |
| [0003](./0003-shared-schema-multitenancy.md) | Shared-schema multi-tenancy | Accepted |
| [0004](./0004-ecs-fargate.md) | ECS Fargate for initial AWS compute | Accepted |
| [0005](./0005-external-provider-ports.md) | External provider ports | Accepted |

New ADRs use the next four-digit number and include Context, Decision, and Consequences. Accepted ADRs are not rewritten to hide a superseded decision; a new ADR supersedes the old record.
