# ADR 0002: React/Vite TypeScript and Spring Boot

- Status: Accepted
- Date: 2026-08-07

## Context

The supplied product is a logged-in desktop application and does not require public SEO rendering. Backend maintainability benefits from the owner's Java familiarity and Spring's transaction/security ecosystem.

## Decision

Use a React SPA built by Vite with strict TypeScript. Use Java 21 Spring Boot for REST, security, domain services, persistence, worker consumers, metrics, and provider adapters. Generate the frontend API client from backend OpenAPI.

## Consequences

- Static S3/CloudFront frontend hosting is simple and inexpensive.
- Spring Boot supplies mature database/security/integration foundations.
- The two languages require generated contracts rather than directly shared types.
- Server-side rendering/public career pages require a separate future decision.
