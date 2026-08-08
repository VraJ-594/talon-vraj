# Talon Terraform bootstrap

This root creates the private Terraform-state bucket and the GitHub Actions OIDC identity used by
the development infrastructure workflow. Run it once from an authenticated operator session. It
does not create application compute, networking, databases, users, or access keys.

## Preconditions

- Terraform 1.10 or newer and AWS CLI v2 are installed.
- `aws sts get-caller-identity` resolves account `762079300828` in the intended operator session.
- The operator may create S3 and IAM/OIDC resources. AWS `PowerUserAccess` commonly excludes IAM
  administration; use an approved administrator/bootstrap role if the plan fails on IAM.
- The repository and trusted branch are `VraJ-594/talon-vraj` and `main`.

Never place AWS credentials in Terraform variables, backend files, GitHub secrets, or this
repository. Both local AWS login/SSO and GitHub OIDC use short-lived credentials.

## First apply

The state bucket cannot contain its own state until it exists, so the first apply uses local state.
The bootstrap root intentionally has no committed backend block during this first phase:

```powershell
Copy-Item terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check
terraform validate
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Review the plan before applying it. The local state is sensitive operational data and is ignored by
Git; keep it only until the migration below succeeds.

If GitHub's OIDC provider already exists in the account, obtain its ARN with:

```powershell
aws iam list-open-id-connect-providers
```

Then set `existing_github_oidc_provider_arn` in the ignored `terraform.tfvars` before planning.

## Move bootstrap state into S3

After the initial apply succeeds, add an S3 backend block to `versions.tf`, copy
`backend.hcl.example` to the ignored `backend.hcl`, replace the account placeholder with the
`terraform_state_bucket_name` output, and migrate:

```hcl
terraform {
  backend "s3" {}
}
```

```powershell
terraform init -migrate-state -backend-config=backend.hcl
terraform state list
```

The S3 backend uses native lockfiles; no DynamoDB table is required. After verifying the migrated
state, remove the obsolete local state files from the workstation.

## Configure the development workflow

Create these non-secret GitHub repository variables from the bootstrap outputs:

- `AWS_ROLE_ARN`: `github_actions_role_arn`
- `AWS_REGION`: `ap-south-1`
- `TF_STATE_BUCKET`: `terraform_state_bucket_name`

The role trust policy accepts only the repository's `main` branch. Pull requests do not receive AWS
credentials or apply infrastructure.
