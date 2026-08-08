# Talon development ECS runtime

This root deploys one two-container Fargate task behind an HTTP ALB. It consumes the existing
development foundation state and never owns the candidate bucket, ECR repositories, or runtime
secret.

Prerequisites:

1. Apply `../dev` and populate `talon-dev-runtime-vraj` outside Terraform.
2. Push API and web images tagged with the same full Git SHA.
3. Copy both example files to ignored local files and replace every placeholder.

```powershell
terraform init -backend-config=backend.hcl
terraform fmt -check -recursive
terraform validate
terraform plan -out=runtime.tfplan
terraform apply runtime.tfplan
terraform output -raw demo_url
```

The generated URL is HTTP-only. Restrict `allowed_demo_cidrs`, use synthetic data/credentials only,
and destroy or scale down the demo when it is not needed. Do not use this endpoint for candidate PII.
