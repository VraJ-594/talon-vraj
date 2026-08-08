variable "aws_region" {
  description = "AWS region for the Talon demo runtime."
  type        = string
  default     = "ap-south-1"
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

variable "state_bucket_name" {
  description = "Private S3 bucket holding the Talon foundation state."
  type        = string

  validation {
    condition     = endswith(var.state_bucket_name, "-tfstate-vraj")
    error_message = "state_bucket_name must reference the Talon -tfstate-vraj bucket."
  }
}

variable "api_image_uri" {
  description = "Full immutable ECR URI for the API image, tagged with a 40-character Git SHA."
  type        = string

  validation {
    condition     = can(regex("^[0-9]+\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+:[0-9a-f]{40}$", var.api_image_uri))
    error_message = "api_image_uri must be a full ECR URI tagged with a lowercase 40-character Git SHA."
  }
}

variable "web_image_uri" {
  description = "Full immutable ECR URI for the web image, tagged with a 40-character Git SHA."
  type        = string

  validation {
    condition     = can(regex("^[0-9]+\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+:[0-9a-f]{40}$", var.web_image_uri))
    error_message = "web_image_uri must be a full ECR URI tagged with a lowercase 40-character Git SHA."
  }
}

variable "allowed_demo_cidrs" {
  description = "IPv4 CIDRs permitted to reach the HTTP-only demo ALB."
  type        = list(string)

  validation {
    condition = length(var.allowed_demo_cidrs) > 0 && alltrue([
      for cidr in var.allowed_demo_cidrs : can(cidrhost(cidr, 0)) && !strcontains(cidr, ":")
    ])
    error_message = "allowed_demo_cidrs must contain at least one valid IPv4 CIDR."
  }
}

variable "search_ai_enabled" {
  description = "Enable Groq natural-language interpretation when the runtime secret contains a key."
  type        = bool
  default     = true
}
