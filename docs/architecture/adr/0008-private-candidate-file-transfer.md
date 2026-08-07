# ADR 0008: Private Candidate File Transfer

- Status: Accepted
- Date: 2026-08-07

## Context

Google Form submissions provide candidate data and public Drive resume links. Candidate PII must
not remain dependent on public links or be served from a public S3 bucket. ZIP intake was proposed
but adds archive traversal and resource-exhaustion risks unrelated to the chosen demo workflow.

## Decision

Accept CSV only, up to 2,000 rows and 10 MB. Every row requires a public, anonymously downloadable
Google Drive PDF link. A Drive source adapter validates HTTPS hosts, redirects, resolved addresses,
content type, PDF signature, and a 10 MB limit. It uses a configurable leaky bucket (five download
starts/second, capacity five) and at most five in-flight downloads.

Download into quarantine, require a clean malware scan, extract text with PDFBox, and then promote
to private object storage. S3 uses all four Block Public Access controls, bucket-owner-enforced
object ownership/disabled ACLs, encryption, least-privilege IAM, opaque workspace-prefixed keys,
and no wildcard public principal. Authorized users receive five-minute GET presigned URLs for an
exact object. CSV exports omit resume URLs, remain private, and expire after seven days.

## Consequences

- Presigned URLs provide time-bounded delegated access without making the bucket public.
- Anonymous Drive access is an explicit demo prerequisite and is unsuitable as the final candidate
  upload model; authenticated Drive and direct upload remain extension adapters.
- The system does not support ZIP files or private Drive documents in this slice.
