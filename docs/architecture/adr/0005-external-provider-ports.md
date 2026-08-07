# ADR 0005: External Provider Ports

- Status: Accepted
- Date: 2026-08-07

## Context

Cognito, xAI Grok, optional Gemini, Google Calendar, SES, and S3 are useful initial providers, but their SDKs and wire models must not become the domain model. AI provider price, quota, and availability are especially uncertain.

## Decision

Declare application-owned ports for identity claims, AI assessment, calendar, mail, and object storage. Implement provider adapters in infrastructure packages. Persist internal normalized results and provider references/version metadata.

## Consequences

- Local deterministic fakes and contract tests are straightforward.
- Grok is preferred when funded xAI API access is configured; Gemini may be enabled as a fallback, and either can later be replaced without changing domain rules.
- `DISABLED` mode keeps deterministic signals and manual review available when no paid/quota-backed AI provider is configured.
- Microsoft Calendar or a unified provider can be added without rewriting interview rules.
- Adapter mapping and contract tests add intentional code at each external boundary.
