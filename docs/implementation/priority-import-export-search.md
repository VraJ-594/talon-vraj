# Priority Import, Export, and Search Implementation Handoff

## Scope and current status

Status: in progress.

The approved active slice is minimal application-owned authentication followed by candidate CSV
import/private export and dual-mode candidate search. The design and three executable plans are
approved. Backend implementation begins with the identity refactor.

## What changed

- Replaced the broad active roadmap with gated foundation, import/export, search, AWS, and E2E
  phases.
- Rewrote the HLD, LLD, API, database, security, deployment/testing, and Terraform documents around
  the approved priority behavior and updated the browser index/editable diagrams.
- Added ADRs for priority scope/authentication, private file transfer, and validated dual-mode
  search.
- Updated the provider-port ADR for Drive, object storage, scanner, queue, and Grok boundaries.
- Prepared a reduced parallel-frontend prompt covering only the approved workflow.

## Why this approach

Finishing a secure vertical workflow provides stronger evidence than partially implementing every
ATS area. Ports preserve provider independence; durable PostgreSQL jobs preserve restart safety;
private object storage protects candidate data; and a validated DSL prevents LLM-produced queries
from becoming database instructions.

## Important paths

- Authentication: existing account → BCrypt verification → access JWT + rotating hashed refresh
  session → workspace/role principal.
- Import: select job → upload/map/preview CSV → confirm durable job → rate-limited Drive fetch →
  quarantine/scan/PDF extraction → private store → candidate/application result.
- Export: validated candidate criteria → durable export job → private CSV → authorized five-minute
  download URL; artifact lifecycle is seven days.
- Search: Cmd+K/explicit filters → typed criteria → PostgreSQL. Natural language → Grok restricted
  DSL → backend validation → the same typed criteria and repository.

## Files and modules affected

- `docs/plans/talon-ats-implementation-plan.md`
- `docs/architecture/adr/0005-external-provider-ports.md`
- `docs/architecture/{hld,lld,api-design,database-design,security-threat-model}.md`
- `docs/architecture/{aws-terraform-design,deployment-and-testing}.md`
- `docs/architecture/architecture.html` and priority Eraser sources
- `docs/architecture/adr/0007-priority-slice-and-application-auth.md`
- `docs/architecture/adr/0008-private-candidate-file-transfer.md`
- `docs/architecture/adr/0009-validated-dual-mode-search.md`
- `docs/prompts/frontend-parallel-session.md`
- Planned backend modules: `identity`, `jobs`, `candidates`, `imports`, `files`, `search`, and shared
  authorization/error infrastructure.

## Verification commands and observed results

- `git -c safe.directory=E:/Project/LiveBuildTask diff --check` — passed with no whitespace errors
  in the architecture changes (Git emitted only the existing `initial_requirements.txt` line-ending
  warning).
- `rg` scans for active Cognito, CSV/ZIP, Gemini fallback, and Google Calendar decisions — no active
  references remain; Cognito appears only in ADR 0007 context explaining the superseded choice.
- Backend focused and full Maven verification are pending the first test-first code checkpoint.
- Docker-backed checks are blocked because Docker is not currently discoverable from this shell.

## Blockers, prerequisites, and exact next step

- External prerequisites: runnable Docker engine/CLI, PostgreSQL/Supabase connection, AWS account,
  private S3/SQS resources, malware scanner choice, and an xAI key with usable billing.
- Exact next step: align the remaining HLD/LLD/API/database/security/deployment documents, then add
  a failing identity test that removes the Cognito-subject assumption.
