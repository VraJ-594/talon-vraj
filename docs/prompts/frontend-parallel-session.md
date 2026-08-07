# Prompt: Priority Talon ATS Frontend Session

Copy everything below the divider into the existing frontend session. This replaces the earlier broad frontend prompt.

---

Continue the Talon ATS frontend from its current green baseline. Repository context, all nine rendered PDF pages, architecture, existing frontend code/tests, and Git state have already been reviewed. Do not repeat broad product discovery.

The governing design is approved and lives at:

`docs/superpowers/specs/2026-08-07-priority-import-export-search-design.md`

Read that specification, root `AGENTS.md`, and the current frontend handoff completely. Treat the approved spec as the answer to previous shell/router scope questions. Ask the user only if the repository contradicts the approved design in a way that changes behavior.

## Branch and ownership

- Work only in the frontend worktree/branch `codex/frontend-web`.
- Own `apps/web/**` and `docs/implementation/frontend-web-handoff.md`.
- Do not edit `apps/api/**`, Flyway migrations, `compose.yaml`, Terraform, or backend-owned API contracts.
- Maintain the frontend handoff after every checkpoint: what, why, how, files, verification, blockers, mock/real integration status, and next step.

## Why the scope changed

The first demonstrable product is now intentionally centered on two complete workflows:

1. Candidate job-application CSV import/export with required Drive PDF resumes.
2. Candidate/application search using deterministic Cmd+K plus Grok-translated natural language.

Calendar, interviews, offers, reports, notifications, advanced authentication, AI scoring, and other screens would consume time without proving these priority workflows. Preserve clean module boundaries, but do not build deferred screens.

## Implement only these feature modules

```text
apps/web/src/
  app/                    providers, router, protected layout, error boundary
  components/             genuinely shared accessible primitives
  features/
    auth/                 sign-in/session/logout only
    jobs/                 import-target job selector
    imports/              CSV application import wizard
    candidates/           list and basic candidate/application profile
    exports/              filtered CSV export jobs/download
    search/               Cmd+K and natural-language candidate search
  lib/                    typed HTTP/error/date/currency helpers
  test/                   shared fixtures and builders
```

## Checkpoint 1: Sign-in and protected shell

Match the supplied authentication screen and ATS shell design direction.

Implement:

- Email and password fields with accessible labels.
- Password visibility control.
- Submit and loading states.
- Generic invalid-credentials message that does not reveal whether the email exists.
- Rate-limited/locked, API-unavailable, and session-expired states.
- Session restoration, logout, and protected-route redirect.
- One fixture Workspace Admin account through the auth gateway; never hardcode a real password in production code.
- PDF-directed sidebar/header with Candidates, Import, and Search as primary destinations.

Do not implement sign-up, OAuth, TOTP, 2FA, password reset, invitations, or member administration. Do not display fake links for those flows.

Reason: one real Admin login is sufficient to demonstrate authorization around import, compensation search, and export.

## Checkpoint 2: Job selection and import wizard

Implement the complete route sequence:

```text
Select job
  -> Upload/template
  -> Map columns
  -> Validate/preview
  -> Confirm
  -> Progress
  -> Results
```

Required behavior:

- Select one target job before upload.
- Download the canonical application CSV template.
- Upload a maximum 10 MB CSV with up to 2,000 rows.
- Map arbitrary Google Form headers to canonical fields.
- Require first name, last name, email, and public Drive resume URL mappings.
- Explain current/expected CTC, `LPA`, currency, experience, notice period, and date normalization.
- Prevent duplicate source/canonical mappings.
- Preview valid, invalid, and duplicate rows with safe errors.
- Confirm once using an idempotency key.
- Preserve progress across refresh.
- Display resume states: fetching, quarantined, scan pending, extracting, clean, failed.
- Display row retry where the backend permits it.
- Download the error CSV.

Use typed fixture gateways until backend endpoints land. Keep fixtures outside presentation components.

## Checkpoint 3: Candidate/application screens

Implement:

- Candidate/application list with job, stage, location, experience, current company/title, skills, expected/current CTC, notice period, application date, and resume status.
- Basic candidate profile and selected application details.
- Additional Google Form answers.
- File status and authorized-download action only for clean files.
- Admin/Recruiter compensation visibility; forbidden behavior must be testable even though the demo identity is Admin.
- Loading, empty, error, forbidden, and retry states.

Do not implement activity feeds, interviews, scorecards, offers, or calendar panels.

## Checkpoint 4: Candidate CSV export

Implement:

- Create export from the current validated filters/sort.
- Progress, completed, failed, expired, and retry states.
- Authorized download action.
- Clear statement that resumes and file URLs are excluded.
- Seven-day artifact expiry display.
- Admin/Recruiter-only compensation/export behavior.

## Checkpoint 5: Dual search

### Cmd+K

- Candidate and job keyword search.
- Navigation commands.
- Keyboard opening, focus, arrows, enter, escape, and empty/error states.
- Never call Grok for Cmd+K.

### Natural-language candidate search

- Search sentence input with examples such as `backend candidates in Pune with expected CTC below 40 LPA`.
- Show interpreted keywords, warnings, sort, and editable/removable filter chips.
- Explicit filters and sorting must work without Grok.
- Support name, location, company/title, skills/resume text, experience, job/stage, source/date, CTC, notice period, and availability filters.
- Show invalid interpretation, Grok disabled/unavailable/timeout, forbidden compensation, loading, empty, and retry states.
- Offer the original sentence as standard keyword search after interpretation failure; never silently execute a changed query.
- Store deterministic filters/sort in URL parameters; do not put the raw natural-language sentence in the URL.

## API boundary

- Use typed feature gateways for every remote operation.
- Record exact endpoint/field/enum/error requests under `## Backend contract requests` in the frontend handoff.
- Do not invent SQL, tenant IDs, security behavior, S3 keys, or provider responses.
- Backend authorization is authoritative.
- Access JWT stays in memory; refresh behavior is handled by the centralized auth/HTTP layer.
- Never store candidate PII or tokens in local storage, logs, or fixtures committed to Git.

## Visual direction

- Preserve and refactor the existing PDF-directed shell rather than replacing it with a generic template.
- Reuse the supplied typography, spacing, off-white canvas, white navigation surfaces, indigo actions, restrained borders, and information density.
- Use real semantic controls, visible focus, reduced motion, responsive overflow, and keyboard alternatives.
- Render and compare each completed priority route. Record visual-review status in the handoff.

## Test-first order

For each checkpoint:

1. Write a focused failing Vitest/Testing Library test.
2. Confirm it fails for the intended missing behavior.
3. Implement the smallest cohesive feature.
4. Run focused tests, then formatting, lint, all frontend tests, build, and audit.
5. Update `docs/implementation/frontend-web-handoff.md`.
6. Commit the green checkpoint with a narrow message.

Required commands:

```powershell
npm run format:check:web
npm run lint:web
npm run test:web
npm run build:web
npm audit --omit=dev
```

Add Playwright only after stable routes exist. The first journey is:

```text
sign in
  -> select job
  -> upload/map/confirm CSV
  -> inspect completed candidate/application
  -> run Cmd+K
  -> run natural-language CTC search
  -> export filtered CSV
```

## Explicitly deferred

- Sign-up, OAuth, TOTP, 2FA, invitations, and password reset.
- Kanban editing and review inbox scoring.
- Calendar, interviews, and scorecards.
- Offers and approvals.
- Reports and notifications.
- AI resume scoring.
- Authenticated Google Drive UI.

Begin with the sign-in/protected-shell failing tests. Keep the current 2/2 baseline tests green and update the frontend handoff before the first commit.

---
