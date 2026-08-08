resource "aws_secretsmanager_secret" "runtime" {
  name                    = "talon-${var.environment}-runtime-vraj"
  description             = "Runtime configuration for the Talon ECS demo; values are managed outside Terraform."
  recovery_window_in_days = 7
}
