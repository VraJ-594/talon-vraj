output "candidate_files_bucket_name" {
  description = "Private bucket supplied to TALON_FILES_S3_BUCKET."
  value       = aws_s3_bucket.candidate_files.bucket
}

output "candidate_file_runtime_policy_arn" {
  description = "Policy attached to the combined API and in-process worker ECS task role."
  value       = aws_iam_policy.candidate_file_runtime.arn
}
