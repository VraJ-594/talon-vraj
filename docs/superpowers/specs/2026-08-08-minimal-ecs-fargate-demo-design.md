# Minimal ECS Fargate Demo Deployment Design

## Goal

Deploy the current Talon React frontend and Spring Boot API as one versioned application release in
AWS, using the existing Terraform state and private candidate-storage foundation. The demo must use
the generated ALB address, one Fargate task, and no NAT Gateway, custom domain, ACM certificate, or
autoscaling.

## Scope

This checkpoint adds two private ECR repositories, a small public VPC, an ECS cluster/service/task,
one public ALB, CloudWatch logs, scoped IAM roles, and a Secrets Manager secret shell. It updates the
web container to proxy `/api/**` to the API container in the same ECS task and updates GitHub Actions
to build immutable commit-SHA images and deploy their exact tags.

It does not add Route53, TLS, WAF, NAT, Redis, SQS, separate workers, autoscaling, multi-service
deployment, RDS, or Supabase-specific infrastructure.

## Runtime architecture

One ECS service runs one Fargate task (`desired_count = 1`) across two public subnets. The task uses
`awsvpc` networking and a public IP only for outbound Google Drive, Groq, Supabase, ECR, CloudWatch,
and AWS API access. Its security group accepts API traffic only from the ALB security group.

The task contains two essential containers:

- `web`: Nginx serves the Vite production build on port 80, handles SPA fallback, and proxies
  `/api/**` plus `/actuator/**` to `127.0.0.1:8080`.
- `api`: Spring Boot listens on port 8080 and runs the current in-process import worker.

The public ALB listens on HTTP port 80 and targets only the web container. Its health check reaches
the proxied Spring readiness endpoint. Terraform outputs the generated `http://<alb-dns>` demo URL.

Fargate task capacity starts at 0.5 vCPU and 1 GiB shared by both containers. The service uses the
deployment circuit breaker with rollback and keeps one running task. No EC2 instances are created.

## Networking and demo security

Terraform creates one VPC, an Internet Gateway, one public route table, and two public subnets in
separate Availability Zones because ALB requires two zones. No NAT Gateway is created. ALB ingress
is restricted by an explicit `allowed_demo_cidrs` input; operators may add reviewer networks.

The generated ALB address cannot receive an ACM public certificate without an owned domain, so this
checkpoint is HTTP-only. It sets `TALON_SECURITY_COOKIE_SECURE=false` for the demo. Only synthetic
demo credentials and candidate data may be used. Production or real PII requires HTTPS before use.

## Images and deployment order

The existing development root first creates two private, scan-on-push ECR repositories with
immutable tags. GitHub Actions then builds the existing API and web Dockerfiles, pushes both images
with the immutable Git commit SHA, and passes those exact image URIs to a separate runtime Terraform
root. Separating foundation and runtime state avoids the first-deployment dependency where an ECS
service references images before their repositories/images exist.

The runtime state key is `talon/dev/runtime/terraform.tfstate`. Runtime Terraform consumes explicit
foundation outputs through S3 remote state and never discovers resources by mutable tags.

## Secrets and permissions

Terraform creates a Secrets Manager secret without a secret version. Secret values are inserted by
an operator through an ignored local JSON file or an interactive AWS command, never through
Terraform variables or state. The JSON holds the Supabase JDBC values, JWT signing/hash keys,
demo-admin bootstrap values, and optional Groq API key.

The task execution role may pull only the two ECR repositories, write the application log group,
and read the exact runtime secret. The application task role receives the existing candidate-file
runtime policy. The GitHub OIDC role remains restricted to `VraJ-594/talon-vraj` on `main` and gains
only the ECR/ECS/ELB/EC2/log/secret/IAM permissions required by these two Terraform roots and image
pushes.

## Failure handling and observability

- ALB readiness failures prevent a deployment from becoming healthy.
- ECS circuit-breaker rollback restores the prior task definition after a failed deployment.
- Both containers log to one CloudWatch log group with separate stream prefixes and seven-day demo
  retention.
- Spring startup fails closed when required database/security/storage configuration is absent.
- Secrets and candidate identifiers are not emitted by workflow or Terraform outputs.

## Verification and acceptance

1. Backend tests/package and frontend lint/tests/build pass.
2. Both Docker images build locally and run together with Nginx proxying API health.
3. Terraform format, initialization, validation, and reviewed plans pass with no destructive changes
   to existing S3/state resources.
4. ECR contains both commit-SHA images and image scanning is enabled.
5. ECS reaches one healthy running task and the generated ALB URL serves the SPA and API readiness.
6. Auth works using synthetic demo credentials over the restricted HTTP demo endpoint.
7. A public Drive PDF imports to the existing private S3 bucket before ECS is declared ready for the
   demonstration.

## Deferred production hardening

An owned domain plus ACM/HTTPS is the first production prerequisite. Private application subnets,
NAT or VPC endpoints, WAF, autoscaling, multiple tasks, separate workers, longer log retention,
alarms, and backup/DR automation remain deferred until usage and risk justify them.
