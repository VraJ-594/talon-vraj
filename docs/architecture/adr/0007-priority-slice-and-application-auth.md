# ADR 0007: Priority Slice and Application-Owned Authentication

- Status: Accepted
- Date: 2026-08-07

## Context

The approximately ten-hour delivery window cannot responsibly implement every ATS workflow. The
demonstration specifically benefits from complete bulk data ingestion and expressive candidate
search. Cognito, OAuth, TOTP, invitations, and public sign-up would consume time without proving
those workflows.

## Decision

Prioritize one vertical slice: an existing Admin account signs in, selects a job, imports Google
Form application CSV data with resumes, searches candidates, and exports results. Use
application-owned email/password authentication with BCrypt, short-lived JWT access tokens, and
hashed rotating refresh sessions stored in PostgreSQL. Restrict import/export/search and sensitive
compensation fields to Admin and Recruiter roles. Seed the demo account from runtime secrets.

Calendar, interviews, scorecards, offers, reports, notifications, editable Kanban, AI resume
scoring, OAuth, 2FA, public sign-up, invitations, and member administration are deferred.

## Consequences

- The demonstrable path is cohesive and can be tested end to end.
- Password reset, MFA, federation, and self-service onboarding are explicit production gaps.
- Authentication can later move behind a provider adapter without changing workspace permissions
  or business APIs.
