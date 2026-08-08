output "demo_url" {
  description = "HTTP-only generated ALB URL for the synthetic-data demo."
  value       = "http://${aws_lb.app.dns_name}"
}

output "ecs_cluster_name" {
  description = "ECS cluster used by the Talon demo."
  value       = aws_ecs_cluster.app.name
}

output "ecs_service_name" {
  description = "Single-task ECS service used by the Talon demo."
  value       = aws_ecs_service.app.name
}
