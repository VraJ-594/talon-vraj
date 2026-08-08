output "terraform_state_bucket_name" {
  description = "Private, versioned bucket used by Talon Terraform backends."
  value       = aws_s3_bucket.terraform_state.bucket
}

output "github_actions_role_arn" {
  description = "Branch-restricted role assumed by GitHub Actions through OIDC."
  value       = aws_iam_role.github_terraform.arn
}

output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider created or reused by this bootstrap stack."
  value       = local.github_oidc_provider_arn
}

output "development_state_key" {
  description = "S3 object key used by the development Terraform root."
  value       = local.state_key
}
