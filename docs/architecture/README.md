# Talon ATS Architecture Documentation

This directory is the source of truth for the approved ATS design. Documents describe intended behavior; the implementation plan controls delivery order and acceptance gates.

## Reading order

1. [High-Level Design](./hld.md)
2. [Low-Level Design](./lld.md)
3. [Database Design](./database-design.md)
4. [API Design](./api-design.md)
5. [Security and Threat Model](./security-threat-model.md)
6. [AWS and Terraform Design](./aws-terraform-design.md)
7. [Deployment and Testing](./deployment-and-testing.md)
8. [Architecture Decision Records](./adr/)

## Editable diagrams

- `eraser/system-architecture.eraserdiagram`
- `eraser/domain-erd.eraserdiagram`
- `eraser/resume-scoring-sequence.eraserdiagram` (legacy filename; now shows priority import/search)
- `eraser/scheduling-sequence.eraserdiagram` (deferred reference)
- `eraser/offer-approval-sequence.eraserdiagram` (deferred reference)

Open `architecture.html` for a browser-friendly index. Each figure names the corresponding Eraser source and expected SVG export path.

## Governing decisions

- React/Vite with strict TypeScript for the authenticated desktop SPA.
- Java 21 Spring Boot modular monolith for API and worker profiles.
- Supabase-hosted PostgreSQL with shared-schema multi-tenancy, application authorization, and RLS; Free for demo and a backed-up paid tier for production.
- Durable PostgreSQL jobs/outbox plus a local dispatcher or SQS for retryable work.
- AWS ECS Fargate as the initial container runtime.
- Application-owned basic authentication for the priority slice; advanced auth is deferred.
- Provider ports for Drive resume source, private object storage, scanner, queue, and Groq query interpretation.
- Deterministic Cmd+K plus validated Groq-to-DSL natural-language search; no AI resume scoring.
- Parameterized AWS account and region, with local Docker Compose development.

The active delivery order is intentionally limited to authentication, CSV/Drive/private-S3
import/export, and candidate search. Later ATS workflows remain backlog, not active dependencies.

## Change discipline

Any implementation change that contradicts these documents requires an ADR. Schema changes require a forward Flyway migration. Public API changes require OpenAPI updates and regenerated TypeScript clients in the same change.
