# ADR 0009: Validated Dual-Mode Candidate Search

- Status: Accepted
- Date: 2026-08-07

## Context

Recruiters need quick Cmd+K lookup and natural-language filters such as “candidates with expected
CTC below 40 LPA.” Allowing an LLM to emit SQL or receive candidate data creates security,
privacy, correctness, and availability risks.

## Decision

Use PostgreSQL full-text/trigram search, typed filters, cursor pagination, and allowlisted sorting.
Cmd+K uses this deterministic engine directly and never invokes AI. Natural-language mode sends
only the user text and a versioned restricted DSL schema to Groq behind an application-owned port.
The backend validates fields, operators, types, ranges, currency semantics, and sort keys, then
maps the accepted criteria to parameterized repository queries. It never executes model SQL.

Money is currency plus integer minor units. LPA is parsed only as annual INR; 40 LPA becomes INR
4,000,000 or 400,000,000 paise. No cross-currency conversion is inferred. Groq failure returns a
clear interpretation error while explicit filters and Cmd+K remain operational.

## Consequences

- Search remains useful without Groq availability or quota.
- Interpreted filters can be shown as editable chips and resubmitted deterministically.
- Adding a field requires an intentional DSL, validation, repository, and UI contract change.
- Embeddings and OpenSearch are unnecessary for the initial scale.
