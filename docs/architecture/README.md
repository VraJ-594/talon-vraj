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
- `eraser/resume-scoring-sequence.eraserdiagram`
- `eraser/scheduling-sequence.eraserdiagram`
- `eraser/offer-approval-sequence.eraserdiagram`

Open `architecture.html` for a browser-friendly index. Each figure names the corresponding Eraser source and expected SVG export path.

## Governing decisions

- React/Vite with strict TypeScript for the authenticated desktop SPA.
- Java 21 Spring Boot modular monolith for API and worker profiles.
- Supabase-hosted PostgreSQL with shared-schema multi-tenancy, application authorization, and RLS; Free for demo and a backed-up paid tier for production.
- Outbox plus SQS for slow, retryable, or externally integrated work.
- AWS ECS Fargate as the initial container runtime.
- Provider ports for AI, calendar, mail, identity, and object storage.
- Grok is the preferred AI adapter when funded API access exists; Gemini is an optional fallback, and neither API is assumed permanently free.
- One AWS environment in `ap-south-1`, with local Docker Compose development.

## Change discipline

Any implementation change that contradicts these documents requires an ADR. Schema changes require a forward Flyway migration. Public API changes require OpenAPI updates and regenerated TypeScript clients in the same change.
