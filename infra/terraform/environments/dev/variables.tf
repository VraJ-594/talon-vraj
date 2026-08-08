variable "aws_region" {
  description = "AWS region for the Talon development environment."
  type        = string
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

variable "allowed_web_origins" {
  description = "HTTPS web origins allowed to fetch exact presigned resume URLs. Empty disables bucket CORS."
  type        = list(string)
  default     = []

  validation {
    condition = alltrue([
      for origin in var.allowed_web_origins : startswith(origin, "https://")
    ])
    error_message = "Every production web origin must use HTTPS."
  }
}
