# ADR 0005: External Provider Ports

- Status: Accepted
- Date: 2026-08-07

## Context

Google Drive, Groq, S3-compatible object storage, malware scanners, and SQS are useful initial
providers, but their SDKs and wire models must not become the domain model. Provider price, quota,
availability, and deployment account are uncertain. Calendar and mail integrations are deferred.

## Decision

Declare application-owned ports for resume sources, object storage, malware scanning, queue
dispatch, and natural-language query interpretation. Implement providers in infrastructure
adapters and persist normalized internal results plus provider/model metadata. Authentication is
application-owned in the priority slice and therefore has no external identity-provider port.

## Consequences

- Local deterministic fakes and contract tests are straightforward.
- Groq can be replaced or disabled without changing the search criteria or query engine.
- Deterministic Cmd+K and explicit-filter search remain available without AI credentials.
- SQS can replace the local dispatcher without changing import behavior.
- A future authenticated Drive or direct-upload adapter can replace the demo public-link source.
- Adapter mapping and contract tests add intentional code at each external boundary.
