variable "aws_region" {
  description = "AWS region that owns the Talon state and development resources."
  type        = string

  validation {
    condition     = can(regex("^[a-z]{2}(?:-gov)?-[a-z]+-\\d$", var.aws_region))
    error_message = "aws_region must be a valid AWS region identifier."
  }
}

variable "environment" {
  description = "Short deployment environment name."
  type        = string
  default     = "dev"

  validation {
    condition     = can(regex("^[a-z0-9-]{2,12}$", var.environment))
    error_message = "environment must contain 2-12 lowercase letters, numbers, or hyphens."
  }
}

variable "github_owner" {
  description = "GitHub repository owner allowed to assume the Terraform role."
  type        = string
  default     = "VraJ-594"
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the Terraform role."
  type        = string
  default     = "talon-vraj"
}

variable "github_branch" {
  description = "Only this GitHub branch may assume the Terraform role."
  type        = string
  default     = "main"
}

variable "existing_github_oidc_provider_arn" {
  description = "Existing account-wide GitHub OIDC provider ARN; null creates it in this stack."
  type        = string
  default     = null

  validation {
    condition = (
      var.existing_github_oidc_provider_arn == null
      || can(regex("^arn:[^:]+:iam::[0-9]{12}:oidc-provider/token\\.actions\\.githubusercontent\\.com$", var.existing_github_oidc_provider_arn))
    )
    error_message = "existing_github_oidc_provider_arn must be the GitHub Actions provider ARN."
  }
}
