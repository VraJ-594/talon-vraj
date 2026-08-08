# AWS Terraform bootstrap handoff

## Scope and status

Status: AWS bootstrap and development foundation stacks are applied and verified in account
`762079300828`, region `ap-south-1`. The foundation now includes candidate storage, private ECR
repositories, and runtime-secret metadata. GitHub workflow source and the separate ECS runtime root
are implemented. Repository variables, the first GitHub-hosted workflow run, runtime apply, and the
real application Drive-to-S3 smoke remain open gates.

## What changed

- Added a Terraform bootstrap root for a private remote-state bucket and GitHub Actions OIDC role.
- Reused the account's existing GitHub OIDC provider rather than creating a duplicate.
- Added partial S3 backends with native lockfiles for bootstrap and development state.
- Applied the development root that owns the private candidate-files bucket and scoped runtime IAM
  policy.
- Added a pinned GitHub Actions workflow that validates, plans, and applies the development root on
  relevant merges to `main` or a manual dispatch from `main`.

## Why this approach

- S3 versioning and native lockfiles make Terraform state recoverable and serialized without the
  deprecated DynamoDB locking path.
- GitHub OIDC avoids permanent AWS access keys. The trust subject is restricted to the exact
  repository and `main` branch.
- Bootstrap and development state are separate so the candidate-storage stack can evolve without
  owning its own state infrastructure.
- ECS is isolated in a third state root so foundation changes do not accidentally replace the
  runtime. The current demo runtime may be applied after immutable images are pushed; the real
  Drive-to-S3 transfer remains an acceptance gate rather than a prerequisite for declaring the HCL.

## Important paths

1. An authenticated operator applies `infra/terraform/bootstrap` once with local state.
2. Bootstrap creates the state bucket and branch-restricted Actions role, then its state migrates to
   `talon/bootstrap/terraform.tfstate`.
3. `infra/terraform/environments/dev` stores state at `talon/dev/terraform.tfstate` and owns the
   candidate-storage, ECR, and secret-metadata foundation.
4. `infra/terraform/environments/dev-runtime` stores state at
   `talon/dev/runtime/terraform.tfstate` and consumes foundation outputs through remote state.
5. GitHub Actions assumes `talon-dev-github-terraform-vraj` through OIDC, saves plans, builds
   commit-SHA images, and applies each exact plan. No AWS key or runtime secret is stored in GitHub.

## Files and modules affected

- `infra/terraform/bootstrap/`
- `infra/terraform/environments/dev/`
- `infra/terraform/environments/dev-runtime/`
- `.github/workflows/terraform-apply.yml`
- `.gitignore`
- `docs/architecture/aws-terraform-design.md`
- `docs/superpowers/plans/2026-08-08-terraform-state-github-oidc.md`

## Verification and observed results

- AWS identity: account `762079300828`, assumed SSO role
  `AWSReservedSSO_PowerUserAccess_370eb89693c45b3c/vraj`, region `ap-south-1`.
- Terraform `1.15.8`; AWS provider lockfiles select `hashicorp/aws 6.58.0`.
- Both Terraform roots initialized and validated successfully.
- Bootstrap plan/apply: `8 added, 0 changed, 0 destroyed`.
- Development storage plan/apply: `8 added, 0 changed, 0 destroyed`.
- State bucket: `talon-dev-762079300828-ap-south-1-tfstate-vraj`.
- Candidate bucket: `talon-dev-762079300828-ap-south-1-vraj`.
- Both buckets have all four public-access blocks enabled, versioning enabled, AES256 default
  encryption, and `BucketOwnerEnforced` ownership.
- Remote objects observed: `talon/bootstrap/terraform.tfstate` and
  `talon/dev/terraform.tfstate`. Lockfiles are transient and were absent after commands completed.
- OIDC trust observed with audience `sts.amazonaws.com` and subject exactly
  `repo:VraJ-594/talon-vraj:ref:refs/heads/main`.
- Local backend configs, `.terraform/`, state, plans, environment files, credentials, and candidate
  data are ignored and must not be committed.

## Open prerequisites and exact next steps

1. In GitHub repository settings, add non-secret Actions variables:
   - `AWS_ROLE_ARN=arn:aws:iam::762079300828:role/talon-dev-github-terraform-vraj`
   - `AWS_REGION=ap-south-1`
   - `TF_STATE_BUCKET=talon-dev-762079300828-ap-south-1-tfstate-vraj`
2. Review and merge the feature branch to `main`, then observe the first GitHub Actions apply. The
   local machine has no GitHub CLI, so repository variables cannot be configured from this session.
3. Tag and push the locally verified API and web images using the source commit SHA, apply the
   runtime root with a reviewed `/32` ingress CIDR, and smoke the generated ALB URL.
4. Run an import containing a real public Drive PDF and verify an opaque object exists in private S3
   without logging its URL, candidate identity, or object key.
