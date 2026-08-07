# Talon ATS Security and Threat Model

## 1. Security objectives

Talon stores resumes, contact information, interview feedback, compensation, offer documents, OAuth tokens, and hiring decisions. The primary objectives are tenant isolation, least-privilege access, confidentiality of candidate and compensation data, integrity of hiring history, safe file/AI processing, and recoverable operations.

The design is SOC 2-aligned but is not evidence of certification.

## 2. Trust boundaries

1. User browser and public internet.
2. CloudFront/Cognito/ALB public AWS edge.
3. Private ECS application runtime.
4. AWS S3/SQS/Secrets data plane.
5. External Supabase PostgreSQL, Google Identity, Google Calendar, and AI-provider APIs.
6. SES recipients and provider event callbacks.
7. GitHub Actions and Terraform administrative plane.

Data crossing a boundary is authenticated, authorized, encrypted, minimized, validated, and correlated where feasible.

## 3. Authentication

- Cognito stores local credentials and handles password reset, verification, TOTP enrollment, brute-force defenses, and Google federation.
- Local email/password users must enroll TOTP before entering the application.
- Federated Google users rely on the Google/Workspace MFA policy; the application does not claim a second Talon-owned factor for federated sessions.
- Spring Security validates JWT issuer, signature, audience/client, expiry, and token use.
- Authentication establishes identity only. Database membership establishes workspace access and role.
- Invitation tokens are random, single use, time limited, email bound, and stored only as hashes.
- Sensitive membership changes and last-admin protections require fresh authorization checks.

## 4. Authorization

Authorization occurs in backend application services before domain commands or data access. UI hiding is convenience only.

Controls include:

- Fixed role policy matrix.
- Job assignment checks for hiring managers/interviewers.
- Compensation permission separate from ordinary candidate access.
- Scorecard visibility policy based on interview assignment and own submission.
- Workspace-scoped identifiers and repository queries.
- PostgreSQL RLS using transaction-local workspace context.
- Private S3 with authorization before presigned URLs.
- Admin-only integration, retention, member, privacy, and raw AI prompt controls.

Cross-tenant resource IDs return not found rather than confirming existence.

## 5. Threat analysis

| Threat | Example | Required controls |
|---|---|---|
| Tenant data leakage | Manipulated candidate/job UUID | Service scope checks, RLS, tenant indexes, negative integration tests |
| Broken role access | Interviewer reads compensation | Explicit permission matrix, compensation projection, Playwright role tests |
| Token theft | Leaked browser/session token | Short token lifetime, HTTPS, secure storage strategy, CSP, logout/revocation procedures |
| CSRF | Attacker triggers state change | Bearer token in authorization header, strict CORS, no ambient API cookies |
| XSS | Resume/note/email HTML injects script | React escaping, sanitization, CSP, no unsafe HTML rendering |
| Malicious files | Malware, MIME spoof, ZIP bomb | Size/count/ratio limits, path normalization, signature inspection, quarantine scan |
| SSRF | Resume URL fetch reaches metadata service | v1 uses CSV+ZIP, no arbitrary resume URL downloader |
| Prompt injection | Resume instructs model to ignore rubric | Delimit untrusted text, schema output, fixed system policy, no tool access, evidence validation |
| AI bias/false score | Model penalizes protected traits | Exclude protected traits, evidence/rubric versions, no automatic rejection, audit/evaluation datasets |
| Calendar overreach | ATS reads private event details | Free/busy-only scopes, minimal fields, ATS-owned event mutation only |
| OAuth token disclosure | Refresh token in DB/log | Secret storage/encryption, redaction, restricted IAM, rotation/revocation |
| Duplicate external effects | SQS redelivery sends two offers | Idempotency keys, processed-message table, provider request IDs |
| Audit tampering | User hides rejection/approval | Append-only audit/stage/approval history, restricted DB roles, CloudTrail |
| Supply-chain compromise | Malicious dependency/image | Lockfiles, dependency review, SBOM, image/IaC/secret scans, pinned CI actions |
| Infrastructure takeover | Long-lived AWS key leak | GitHub OIDC, least-privilege roles, protected environments, CloudTrail |

## 6. File handling policy

- Supported resume types: PDF and DOCX.
- Supported offer attachment: PDF.
- Validate extension, declared content type, magic bytes, size, checksum, and parser behavior.
- ZIP import enforces entry count, total expanded bytes, per-entry bytes, compression ratio, normalized relative paths, and duplicate filenames.
- Uploads enter `PENDING_SCAN`; users cannot download or process them until `CLEAN`.
- Infected/suspicious files enter quarantine, create an audit event, and expose a safe error without returning file content.
- Parsers run with bounded memory/time and no outbound network capability.

## 7. AI data handling

- External AI processing is allowed for v1 and is disclosed in workspace/privacy documentation.
- Enable a provider only after organizational review of its data-processing terms, retention/ZDR configuration, region, and billing account.
- Send extracted resume text and job rubric only; do not send unrelated activity, messages, compensation, or tenant details.
- Do not request or infer protected characteristics.
- xAI credentials and any optional Gemini fallback credentials reside in Secrets Manager and are accessed only by the worker task role.
- Store the validated structured result and necessary evidence, not hidden reasoning.
- Version provider/model/prompt/rubric for evaluation and rescoring.
- Apply timeout, quota, concurrency, and retry controls; provider failure leaves the application reviewable without a score.
- Model output never directly invokes commands or automatically rejects candidates.

## 8. Encryption and secrets

- TLS for browser, AWS service, database, and provider traffic.
- KMS-backed encryption for S3, SQS, Secrets Manager, and logs where configured; Supabase database encryption is governed by its selected plan and provider controls.
- Secrets are injected as references/runtime values, never built into images or frontend bundles.
- Terraform receives secret references or sensitive protected CI/operator values; plaintext is not committed or intentionally printed. Cognito's declarative Google provider configuration may retain its client secret in encrypted remote state, so state access is treated as secret access.
- S3 blocks public access, enforces TLS, and uses versioning/lifecycle rules.

## 9. Network controls

- CloudFront is the public application entry.
- ALB is public but restricted to expected CloudFront traffic where supported and protected by a shared origin secret/header strategy.
- ECS tasks use private subnets and reach Supabase only over TLS through controlled outbound routing.
- The application uses the Supabase Supavisor session-pooler endpoint; database credentials are runtime secrets and application pools are bounded.
- Worker has no inbound application traffic.
- Task IAM roles grant module-specific S3/SQS/SES/Secrets actions and no administrative permissions.
- VPC endpoints are used for high-volume AWS services where cost-beneficial; NAT is used for Supabase, Google, and AI-provider egress.

## 10. Logging and audit

Application logs may contain identifiers and normalized error codes but not resumes, email bodies, passwords, access/refresh tokens, API keys, offer documents, or full candidate contact data.

Audit events include actor, workspace, action, resource, outcome, reason, timestamp, correlation ID, and minimized request metadata for:

- Authentication/workspace/member changes.
- Candidate exports/deletions and file access.
- Pipeline/outcome decisions and bulk actions.
- Scorecard reopen/edit events.
- Calendar connection and scheduling changes.
- Offer versions, approvals, rejection, and delivery.
- Integration/settings/retention changes.

## 11. Retention and privacy

- Workspace default for rejected/withdrawn candidates is 24 months.
- Admins can shorten or extend within company policy.
- Purge deletes PII, resume/offer files, message content, and PII-bearing AI evidence.
- Minimal non-identifying aggregate facts may remain for reports.
- Security audit retention is separately controlled and minimized.
- Admin-only export/delete operations are asynchronous, idempotent, and audited.

## 12. Security verification

- Unit tests for permission policy and state-machine guards.
- Testcontainers tests for RLS with absent, correct, and malicious tenant context.
- Playwright role matrix and cross-workspace resource tests.
- File corpus tests for malformed, oversized, spoofed, encrypted, and compressed inputs.
- Prompt-injection/adversarial assessment fixtures.
- SAST, dependency, secret, container, SBOM, Terraform, and DAST-oriented checks.
- Backup restore, token revocation, DLQ replay, and incident runbooks exercised before release.

## 13. Residual risks

- Google-federated MFA strength depends on the upstream Google account policy.
- External AI processing introduces privacy/vendor risk despite minimization and contracts.
- A single NAT reduces initial cost but does not provide full AZ-failure resilience; Supabase availability and recovery depend on the purchased plan.
- Supabase Free can pause and lacks production backup guarantees, so production use is explicitly blocked until the project is upgraded and a restore is tested.
- Hosting candidate data outside the AWS account adds vendor, regional placement, data-residency, and egress risks that require organizational approval.
- Manual offer acceptance is susceptible to operator error and requires audit/reconciliation.
- Human hiring decisions can remain biased even with explainable automation; organizational process controls remain necessary.
