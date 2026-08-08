resource "aws_cloudwatch_log_group" "app" {
  name              = "/talon/${var.environment}/ecs-vraj"
  retention_in_days = 7
}

resource "aws_ecs_cluster" "app" {
  name = "${local.resource_prefix}-cluster-vraj"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

locals {
  runtime_secret_arn = data.terraform_remote_state.foundation.outputs.runtime_secret_arn
  api_secrets = [
    for name in [
      "DATABASE_URL",
      "DATABASE_USERNAME",
      "DATABASE_PASSWORD",
      "TALON_SECURITY_ACCESS_SIGNING_KEY",
      "TALON_SECURITY_REFRESH_HASH_KEY",
      "TALON_DEMO_ADMIN_EMAIL",
      "TALON_DEMO_ADMIN_PASSWORD_HASH",
      "GROQ_API_KEY"
      ] : {
      name      = name
      valueFrom = "${local.runtime_secret_arn}:${name}::"
    }
  ]
  api_environment = [
    { name = "DATABASE_POOL_SIZE", value = "4" },
    { name = "TALON_SECURITY_ENABLED", value = "true" },
    { name = "TALON_SECURITY_ISSUER", value = "http://${aws_lb.app.dns_name}" },
    { name = "TALON_SECURITY_AUDIENCE", value = "talon-web" },
    { name = "TALON_SECURITY_ACCESS_TOKEN_LIFETIME", value = "15m" },
    { name = "TALON_SECURITY_REFRESH_TOKEN_LIFETIME", value = "7d" },
    { name = "TALON_SECURITY_COOKIE_SECURE", value = "false" },
    { name = "TALON_DEMO_ADMIN_ENABLED", value = "true" },
    { name = "TALON_DEMO_ADMIN_DISPLAY_NAME", value = "Demo Administrator" },
    { name = "TALON_DEMO_ADMIN_WORKSPACE_NAME", value = "Talon Demo" },
    { name = "TALON_DEMO_ADMIN_WORKSPACE_SLUG", value = "talon-demo" },
    { name = "TALON_DEMO_ADMIN_DEFAULT_TIMEZONE", value = "Asia/Kolkata" },
    { name = "TALON_FILES_PROVIDER", value = "s3" },
    { name = "TALON_FILES_S3_BUCKET", value = data.terraform_remote_state.foundation.outputs.candidate_files_bucket_name },
    { name = "TALON_FILES_S3_REGION", value = var.aws_region },
    { name = "TALON_SEARCH_AI_ENABLED", value = tostring(var.search_ai_enabled) },
    { name = "TALON_SEARCH_AI_MODEL", value = "openai/gpt-oss-20b" },
    { name = "TALON_SEARCH_DEMO_DATA_ENABLED", value = "false" }
  ]
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${local.resource_prefix}-app-vraj"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.application.arn

  container_definitions = jsonencode([
    {
      name              = "api"
      image             = var.api_image_uri
      essential         = true
      cpu               = 448
      memoryReservation = 768
      environment       = local.api_environment
      secrets           = local.api_secrets
      portMappings = [{
        name          = "api"
        containerPort = 8080
        hostPort      = 8080
        protocol      = "tcp"
      }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "api"
        }
      }
    },
    {
      name              = "web"
      image             = var.web_image_uri
      essential         = true
      cpu               = 64
      memoryReservation = 128
      dependsOn         = [{ containerName = "api", condition = "START" }]
      portMappings = [{
        name          = "web"
        containerPort = 80
        hostPort      = 80
        protocol      = "tcp"
      }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "web"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "app" {
  name                              = "${local.resource_prefix}-service-vraj"
  cluster                           = aws_ecs_cluster.app.id
  task_definition                   = aws_ecs_task_definition.app.arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  platform_version                  = "LATEST"
  health_check_grace_period_seconds = 90
  wait_for_steady_state             = true
  enable_execute_command            = false

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    assign_public_ip = true
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.task.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "web"
    container_port   = 80
  }

  depends_on = [
    aws_lb_listener.http,
    aws_iam_role_policy_attachment.execution_managed,
    aws_iam_role_policy.execution_secret,
    aws_iam_role_policy_attachment.candidate_files
  ]
}
