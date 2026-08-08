locals {
  resource_prefix = "talon-${var.environment}"
  default_tags = {
    Owner       = "Vraj"
    Project     = "TalonATS"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

data "terraform_remote_state" "foundation" {
  backend = "s3"

  config = {
    bucket       = var.state_bucket_name
    key          = "talon/${var.environment}/terraform.tfstate"
    region       = var.aws_region
    encrypt      = true
    use_lockfile = true
  }
}
