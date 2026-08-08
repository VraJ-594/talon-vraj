output "candidate_files_bucket_name" {
  description = "Private bucket supplied to TALON_FILES_S3_BUCKET."
  value       = aws_s3_bucket.candidate_files.bucket
}

output "candidate_file_runtime_policy_arn" {
  description = "Policy attached to the combined API and in-process worker ECS task role."
  value       = aws_iam_policy.candidate_file_runtime.arn
}

output "api_repository_url" {
  description = "Private ECR repository URL for immutable Talon API images."
  value       = aws_ecr_repository.api.repository_url
}

output "web_repository_url" {
  description = "Private ECR repository URL for immutable Talon web images."
  value       = aws_ecr_repository.web.repository_url
}

output "runtime_secret_arn" {
  description = "Secrets Manager shell populated outside Terraform before ECS deployment."
  value       = aws_secretsmanager_secret.runtime.arn
}
