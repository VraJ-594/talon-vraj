# Terraform State and GitHub OIDC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap recoverable Terraform state and least-privilege GitHub Actions access for the Talon development AWS account, then apply the existing private candidate-file storage stack on merges to `main`.

**Architecture:** A one-time bootstrap root creates a private versioned S3 state bucket and a GitHub OIDC role restricted to `VraJ-594/talon-vraj` on `refs/heads/main`. The development root uses a partial S3 backend with native lockfiles; a push to `main` assumes the OIDC role, validates, plans, and applies only the development storage stack. ECS is outside this checkpoint and its permissions are not granted yet.

**Tech Stack:** Terraform 1.15.x, HashiCorp AWS provider 6.x, Amazon S3, AWS IAM OIDC, GitHub Actions.

## Global Constraints

- Every explicitly nameable AWS resource ends in `-vraj`.
- Candidate and Terraform-state buckets must block all public access, enforce bucket-owner ownership, require TLS, enable encryption, and enable versioning.
- Terraform state and plans, candidate data, credentials, Supabase values, JWT/HMAC keys, and AWS temporary credentials must never be committed.
- GitHub receives no permanent AWS access key; Actions uses `id-token: write` and short-lived OIDC credentials.
- The OIDC trust subject is exactly `repo:VraJ-594/talon-vraj:ref:refs/heads/main`.
- Use S3 native locking with `use_lockfile=true`; do not add the deprecated DynamoDB locking path.
- Do not add ECS, networking, database, email, calendar, or queue resources in this checkpoint.

---

### Task 1: Bootstrap state and CI identity

**Files:**
- Create: `infra/terraform/bootstrap/versions.tf`
- Create: `infra/terraform/bootstrap/providers.tf`
- Create: `infra/terraform/bootstrap/variables.tf`
- Create: `infra/terraform/bootstrap/main.tf`
- Create: `infra/terraform/bootstrap/outputs.tf`
- Create: `infra/terraform/bootstrap/terraform.tfvars.example`
- Create: `infra/terraform/bootstrap/README.md`

**Interfaces:**
- Consumes: authenticated local AWS provider credentials, `aws_region`, `environment`, GitHub owner/repository/branch.
- Produces: `terraform_state_bucket_name`, `github_actions_role_arn`, and a role allowed to manage the exact development state path and current candidate-storage resources.

- [x] **Step 1: Declare the bootstrap providers**

Use the default local backend for the state bucket's first apply, require Terraform `>= 1.10.0, < 2.0.0`, and pin AWS provider compatibility to `~> 6.0`. Add the S3 backend block only after the bucket exists, then migrate the bootstrap state.

- [x] **Step 2: Declare validated inputs and deterministic names**

Default GitHub inputs to owner `VraJ-594`, repository `talon-vraj`, branch `main`, and environment `dev`. Derive names with account and region uniqueness before the required `-vraj` suffix.

- [x] **Step 3: Create the private state bucket**

Add bucket ownership enforcement, all four public-access blocks, AES256 default encryption, versioning, and an HTTPS-only deny policy. Keep `force_destroy=false`.

- [x] **Step 4: Create or consume GitHub's OIDC provider**

Support both a new provider and a caller-supplied existing provider ARN so accounts with organization-wide GitHub federation do not collide.

- [x] **Step 5: Create the branch-restricted Actions role**

Require audience `sts.amazonaws.com` and subject `repo:VraJ-594/talon-vraj:ref:refs/heads/main`. Grant state-object/lockfile access and management of only the deterministic development candidate bucket and candidate-file runtime IAM policy.

- [x] **Step 6: Document initial apply and state migration**

Run the first bootstrap with `terraform init -backend=false`, apply locally, then migrate that bootstrap state into the new bucket with a generated, ignored `backend.hcl`. Document import configuration for an existing GitHub OIDC provider.

- [x] **Step 7: Format and validate**

Run:

```powershell
terraform -chdir=infra/terraform/bootstrap fmt -check
terraform -chdir=infra/terraform/bootstrap init -backend=false
terraform -chdir=infra/terraform/bootstrap validate
terraform -chdir=infra/terraform/bootstrap plan -var="aws_region=ap-south-1"
```

Expected: format and validation pass; plan contains only bootstrap S3/IAM resources and no secrets.

### Task 2: Move the development root to remote state

**Files:**
- Modify: `infra/terraform/environments/dev/versions.tf`
- Create: `infra/terraform/environments/dev/backend.hcl.example`
- Modify: `infra/terraform/environments/dev/README.md`

**Interfaces:**
- Consumes: bootstrap output `terraform_state_bucket_name` and the selected AWS region.
- Produces: development state at `talon/dev/terraform.tfstate` plus `talon/dev/terraform.tfstate.tflock`.

- [x] **Step 1: Add a partial S3 backend**

Add `backend "s3" {}` without credentials or account-specific values in source.

- [x] **Step 2: Add the non-secret example configuration**

Document `bucket`, `key`, `region`, `encrypt=true`, and `use_lockfile=true`. The actual `backend.hcl` remains ignored/local.

- [x] **Step 3: Initialize and plan**

Run:

```powershell
terraform -chdir=infra/terraform/environments/dev init -backend-config=backend.hcl
terraform -chdir=infra/terraform/environments/dev validate
terraform -chdir=infra/terraform/environments/dev plan -out=dev.tfplan -var="aws_region=ap-south-1"
```

Expected: the candidate-file bucket and runtime policy are planned against the remote backend; the plan file remains ignored.

### Task 3: Apply Terraform on merges to main with OIDC

**Files:**
- Create: `.github/workflows/terraform-apply.yml`

**Interfaces:**
- Consumes repository variables `AWS_ROLE_ARN`, `AWS_REGION`, and `TF_STATE_BUCKET`.
- Produces a serialized, non-interactive development Terraform apply after changes reach `main`.

- [x] **Step 1: Define the trusted trigger and permissions**

Trigger on pushes to `main` affecting `.github/workflows/terraform-apply.yml` or `infra/terraform/**`, plus manual dispatch. Set only `contents: read` and `id-token: write`; add one development concurrency group.

- [x] **Step 2: Configure short-lived AWS credentials**

Use GitHub checkout, HashiCorp Terraform setup, and AWS credential configuration actions. Assume `${{ vars.AWS_ROLE_ARN }}` in `${{ vars.AWS_REGION }}` with account-ID masking enabled.

- [x] **Step 3: Validate and apply**

Run recursive format checking, remote-backend initialization from repository variables, validation, a saved plan, and `terraform apply -auto-approve` of that exact plan. Do not upload the plan as an artifact.

- [ ] **Step 4: Validate workflow configuration**

Confirm YAML parsing, action references, permissions, paths, and variable names. After bootstrap apply, add the three non-secret repository variables and manually dispatch once before relying on merge automation.

### Task 4: Verification, handoff, and source control

**Files:**
- Modify: `docs/architecture/aws-terraform-design.md`
- Modify: `docs/implementation/priority-import-export-search.md`

**Interfaces:**
- Consumes: Terraform format/validate/plan evidence, AWS caller identity, S3 public-access/encryption/versioning checks, and GitHub workflow result.
- Produces: an evidence-backed deployment handoff and a stable pushed checkpoint.

- [x] **Step 1: Verify AWS resources after apply**

Use AWS CLI read-only checks to prove both buckets are private and versioned, the state object/lockfile are present after development init/apply, and the OIDC trust subject is branch-restricted. Do not print policies containing unrelated account data.

- [ ] **Step 2: Run application regression checks**

Run the full Maven verification and frontend lint/test/build gates. Do not claim green if concurrent candidate-roster work remains incomplete.

- [ ] **Step 3: Update the living handoff**

Record exact commands/results, AWS prerequisites, created resource names, workflow status, known Drive failures, and the ECS/S3 reliability next gate without recording candidate data or credentials.

- [ ] **Step 4: Commit and push the verified checkpoint**

Review `git diff --check`, staged paths, and ignored secret/state files. Commit on `codex/backend-api` and push to `origin`; do not merge to `main` until the workflow variables exist and the branch is reviewed.
