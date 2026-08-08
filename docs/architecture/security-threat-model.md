# Talon ATS Priority Security and Threat Model

## 1. Objectives and trust boundaries

Protect candidate PII, credentials, tenant isolation, private files, and search confidentiality.
Trust boundaries exist at browser/API, API/PostgreSQL, API/worker/queue, worker/public Drive,
worker/scanner/object storage, API/Groq, and operator/Terraform/cloud account.

All browser input, CSV cells, URLs, model output, queue messages, object metadata, and provider
responses are untrusted. TLS is required across network boundaries.

## 2. Authentication

- Existing accounts only; normalize email consistently and store BCrypt hashes.
- Rate-limit by account and source, use generic login failure responses, and audit outcomes.
- Access JWTs are short-lived and validate algorithm, signature, issuer, audience, expiry, and
  required claims. Signing keys come from a secret manager/runtime secret and support rotation.
- Refresh tokens are high-entropy opaque values; only keyed hashes are stored. Rotation is atomic,
  reuse revokes the family, and logout revokes the current session.
- Cookies, if used, are Secure, HttpOnly, SameSite, narrowly scoped, and protected from CSRF.
- Demo seeding reads a BCrypt hash/credential from runtime secrets; no default password enters Git.

Public sign-up, OAuth, TOTP/2FA, password reset, and invitation flows are deferred and must receive
a new threat review before activation.

## 3. Authorization and tenancy

Every authenticated request resolves server-owned user, workspace, role, and capabilities. Admin
and Recruiter are the only active roles and can access import/export/search/compensation/resume
features. Controllers do not authorize by hiding UI controls; application services enforce policy.
Tenant-aware repository criteria, unique keys, foreign keys, and PostgreSQL RLS prevent cross-
workspace reads/writes. Queue consumers rehydrate workspace ownership from durable database state.

## 4. Threats and controls

| Threat | Primary controls |
|---|---|
| Credential stuffing/user discovery | BCrypt, generic failures, account/IP limiter, audit/alerts |
| Token theft/replay | short JWT TTL, hashed rotating refresh token, family reuse revocation |
| Cross-tenant object/API access | server principal, workspace queries, RLS, exact-object authorization |
| CSV formula injection | neutralize spreadsheet-leading formulas in exports; treat cells as data |
| Oversized/malformed CSV | 10 MB/2,000-row hard limits, streaming parser, bounded field sizes |
| SSRF/DNS rebinding/redirect abuse | HTTPS Google allowlist, validate every resolution/redirect, block non-public IPs |
| Malicious/non-PDF resume | byte limit, signature/MIME checks, quarantine, malware scan, bounded PDFBox parsing |
| Public object exposure | S3 Block Public Access, disabled ACLs, no public principal/policy/site, IaC tests |
| Presigned URL leakage | authorization first, exact key, GET only, five-minute TTL, redact logs |
| Import replay/race | idempotency keys, database uniqueness, row leases, processed-message records |
| LLM prompt injection/SQL injection | no candidate data/SQL to model, restricted DSL, strict validator, parameters |
| Sensitive logs/errors | structured allowlist, PII/token/URL/resume redaction, safe problem codes |

## 5. File handling policy

Every import row requires a demo-public Google Drive PDF link. The worker does not scrape HTML or
send browser cookies. It streams through a rate-limited source adapter into inaccessible
quarantine, verifies size/type/hash, requires a clean scanner result, and promotes to private
storage before linking the application. Unknown/unavailable scan status fails closed.

AWS buckets enable all four S3 Block Public Access settings, bucket-owner-enforced object ownership
(ACLs disabled), encryption, version/lifecycle settings as appropriate, and IAM scoped by action,
bucket, and prefix. Object keys contain opaque IDs. Exports omit resume locations and expire after
seven days. A presigned URL does not make a bucket public, but it acts as a temporary bearer
capability and is protected accordingly.

## 6. Natural-language search privacy

Groq receives the user's natural-language query, the restricted DSL schema/version, and minimal
locale context only. It receives no candidate rows, resumes, emails, object keys, SQL, credentials,
or tenant secrets. Output is data, never instructions: reject unknown fields/operators, invalid
types/ranges/currency, excessive predicates, and non-allowlisted sorts. Timeouts/retries are bounded
and deterministic search remains available when the provider is disabled or fails.

## 7. Secrets, network, and operations

Secrets Manager/runtime secret injection supplies database credentials, JWT keys, Groq key, and
provider configuration. Terraform state is encrypted/access-controlled and never committed. ECS
tasks use least-privilege roles; private subnets and security-group references restrict traffic;
CloudFront/ALB apply TLS, headers, size limits, and rate controls. Production logs/metrics/audit
events use retention and access control appropriate for candidate data.

## 8. Verification and residual risk

Required checks include auth/token abuse tests, tenant isolation integration tests, malicious CSV/
URL/PDF fixtures, object policy/IaC scans, presigned authorization tests, import replay tests, DSL
property/allowlist tests, dependency/container scans, and Playwright role journeys.

Residual risks/gates:

- anonymous Drive links can be shared outside Talon; this is a demo prerequisite, not final intake;
- real malware scanner effectiveness and Groq data-processing terms need operational approval;
- app-owned auth lacks MFA/reset/federation until those deferred flows are implemented;
- Supabase Free is a demo tier and needs approved production backup/PITR availability before launch.
