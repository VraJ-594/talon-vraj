# Talon development storage foundation

This checkpoint creates only the private candidate-file storage and IAM policies required by the
priority import path. It does not create public buckets, users, access keys, ECS, or a deployment
pipeline.

Prerequisites:

- Terraform 1.8 or newer;
- an authenticated AWS operator role/profile for the target account;
- a reviewed backend-state configuration before collaborative use.

First apply `../../bootstrap`, create an ignored `backend.hcl` from `backend.hcl.example`, and set
its bucket to the bootstrap output. Copy `terraform.tfvars.example` to an ignored `terraform.tfvars`,
then run:

```powershell
terraform init -backend-config=backend.hcl
terraform fmt -check -recursive
terraform validate
terraform plan -out talon-dev.tfplan
```

Review cost and resource names before `terraform apply`. Configure the API/worker with:

```text
TALON_FILES_PROVIDER=s3
TALON_FILES_S3_BUCKET=<candidate_files_bucket_name output>
TALON_FILES_S3_REGION=<aws_region>
```

ECS will use the `candidate_file_runtime_policy_arn` output on the combined API/in-process-worker
task role through the AWS SDK default credential chain. The application enforces clean-only
presigning; IAM cannot split API and worker permissions while both execute in one process. A later
SQS/worker deployment may split that role without changing application ports.

Never add AWS access keys to application environment files or GitHub secrets. Terraform state is
stored in the bootstrap-managed private encrypted S3 bucket with native lockfiles. Clean resume
current versions are retained while active; superseded/deleted versions expire after 30 days. A
future privacy-erasure workflow must delete every version for immediate candidate deletion rather
than waiting for lifecycle expiry.

This root also creates the two private immutable ECR repositories and the empty runtime secret used
by the ECS demo. Terraform intentionally creates no secret version. Copy
`runtime-secret.json.example` to an ignored `runtime-secret.json`, replace every placeholder, and
populate it without printing its content:

```powershell
aws secretsmanager put-secret-value `
  --secret-id talon-dev-runtime-vraj `
  --secret-string file://runtime-secret.json
```

Delete the ignored local JSON after confirming a current secret version exists. Never pass these
values through Terraform variables because Terraform state would retain them.
