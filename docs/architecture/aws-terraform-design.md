# AWS and Terraform Design

## 1. Objectives

- Provision a repeatable, production-oriented AWS environment with low initial cost.
- Keep application containers portable and Terraform modules environment-ready.
- Separate one-time state bootstrap, platform infrastructure, and application releases.
- Use no long-lived AWS credentials in the eventual GitHub deployment workflow.
- Primary region: `ap-south-1`.

## 2. Terraform structure

```text
infra/terraform/
  bootstrap/                 remote-state bucket and access policy
  modules/
    network/
    security/
    supabase/
    storage/
    messaging/
    identity/
    compute/
    edge/
    observability/
    github-oidc/
  environments/demo/
    backend.hcl
    main.tf
    variables.tf
    outputs.tf
    terraform.tfvars.example
```

Modules expose narrow inputs/outputs and do not configure providers internally. Resources use consistent tags: `Project`, `Environment`, `ManagedBy`, `Owner`, and `DataClassification`.

## 3. Remote state bootstrap

The bootstrap stack creates a dedicated versioned S3 bucket in `ap-south-1` with encryption, public access block, TLS-only policy, lifecycle protection for old versions, and restricted IAM access. Terraform's S3 locking mechanism prevents concurrent writes. State and plan artifacts are never committed.

Bootstrap is deliberately separate because Terraform cannot use a backend that does not yet exist. The operator applies bootstrap once using local state, migrates platform state to S3, verifies recovery, and stores the bootstrap state in an approved secure location.

## 4. Network topology

- One VPC spanning two Availability Zones.
- Public subnets: ALB and one NAT Gateway for initial cost control.
- Private application subnets: ECS API and worker tasks.
- Route tables separated by tier.
- Security groups reference other security groups rather than broad CIDRs.
- VPC endpoints for S3 and selected AWS control/data services when they reduce NAT traffic enough to justify endpoint cost.

The single NAT Gateway is an explicit initial cost/resilience trade-off. Production hardening can enable one NAT per AZ without changing application code.

## 5. Edge and frontend

- Private S3 bucket stores immutable hashed React build assets.
- CloudFront accesses the bucket through Origin Access Control.
- CloudFront routes `/api/*` to the ALB and all other routes to the SPA origin, applying an SPA fallback only for non-asset paths.
- Security headers include CSP, HSTS after HTTPS is confirmed, frame restrictions, referrer policy, and MIME sniffing protection.
- Initially use the CloudFront domain. Route53 and a custom ACM certificate are optional inputs when the company domain is supplied.
- Any CloudFront custom certificate is provisioned through an aliased `us-east-1` provider.

## 6. Identity

- Cognito user pool in `ap-south-1`.
- Verified email, secure password policy, recovery flow, and TOTP support.
- App client uses authorization code flow with PKCE and no client secret in the SPA.
- Google identity provider configuration receives its client secret through a sensitive protected Terraform input. Cognito's declarative provider configuration can place that value in remote state, so state encryption and access restrictions are treated as secret controls.
- Callback/logout URLs include local development and the emitted CloudFront URL; production removes obsolete callbacks.
- Cognito groups are not the authorization source of truth; database memberships and roles are.

## 7. Compute and image registry

- One ECR repository for Spring Boot images with immutable tags and scan-on-push.
- ECS cluster using Fargate.
- API service behind ALB; worker service has no public load balancer.
- Initial API and worker tasks: 0.5 vCPU and 1 GB each, adjustable through variables after measurement.
- Desired count one for each profile initially; API health checks gate rolling deployment.
- Queue-based autoscaling can scale worker count; request/CPU-based autoscaling can scale API count.
- Separate execution and task roles. Task roles grant only required bucket prefixes, queues, SES calls, and secret ARNs.
- One-off ECS tasks run Flyway/database migration commands before service rollout.

## 8. Database

- Supabase-hosted PostgreSQL replaces AWS RDS; no RDS resources, subnets, or security groups are provisioned.
- The official `supabase/supabase` Terraform provider creates or imports the project and manages only supported project/database settings. Supabase organization ID, project region, and plan are explicit inputs.
- Supabase Free is the development/demo default. Production deployment is gated on a paid, non-pausing tier with automated backups/PITR and a successful restore exercise.
- Select the closest organization-approved Supabase region available at deployment time; do not assume it matches AWS `ap-south-1`. Record the resulting data-residency and latency trade-off.
- ECS connects over TLS to the shared Supavisor session pooler on port 5432. Session mode fits persistent Spring Boot services and supplies IPv4 connectivity when the direct database endpoint is IPv6-only.
- Runtime credentials are stored in AWS Secrets Manager. If Terraform creates the project or sets a database password, that sensitive value may also exist in encrypted remote state, so state access is treated as database-administrator access.
- HikariCP maximum pools are computed from the selected Supabase plan's connection allowance and maximum API/worker task counts. Flyway is the only schema-change mechanism.
- Application code uses JDBC/JPA and PostgreSQL capabilities only; Supabase Auth, Storage, Edge Functions, and Data APIs are excluded.
- The Supabase provider's pre-GA status is an explicit IaC risk. If it cannot safely own project creation, bootstrap the project once, import it into Terraform, and retain declarative settings thereafter.

## 9. Files and messaging

- Private versioned S3 data bucket with workspace-prefixed keys, lifecycle rules, access logging where appropriate, and blocked public access.
- Standard SQS queues with redrive policies and named DLQs for imports, resume processing/scoring, calendar, messages, and maintenance.
- Queue visibility timeouts exceed maximum bounded handler duration; consumers extend visibility only for known safe work.
- SES verified sender identity, configuration set, and event destination for delivery/bounce/complaint processing.
- Initial SES sandbox restrictions are documented; production access is an external prerequisite for arbitrary recipients.
- EventBridge Scheduler triggers reconciliation, retention, webhook renewal, and housekeeping messages.

## 10. Secrets and encryption

- KMS keys for application data services where a customer-managed key is justified.
- Database and application provider secrets use Secrets Manager; their values are seeded through protected operator or CI inputs rather than committed files.
- ECS secret injection uses task-definition secret references.
- Key policies grant administrative and runtime use separately.
- Secret rotation procedures are documented. Terraform manages secret containers and references; sensitive values required by declarative resources may remain in encrypted remote state and therefore inherit the state bucket's strict access policy.

## 11. Observability

- CloudWatch log groups per service/profile with explicit retention.
- Container Insights or equivalent ECS metrics if cost permits.
- Dashboards for API, ECS, Supabase connection-pool/application metrics, SQS/DLQ, imports, and provider calls.
- Alarms for unhealthy targets, sustained 5xx, latency, database connectivity/pool pressure, queue age, DLQ messages, backup failures exposed by the selected Supabase plan, and authentication anomalies.
- CloudTrail enabled for management events and relevant data events according to cost/security policy.
- Alarm destinations remain configurable because the company notification channel is not yet specified.

## 12. Deployment permissions

Initial deployment is manual with short-lived approved AWS credentials. After stability, Terraform creates GitHub OIDC roles:

- Pull-request role: read-only plan and metadata permissions.
- Infrastructure apply role: protected environment approval and scoped infrastructure permissions.
- Application deploy role: ECR push, ECS task registration/service update, frontend bucket sync/invalidation, and migration-task execution.

Trust policies restrict repository, branch/tag, workflow/environment, and audience claims. GitHub stores no long-lived AWS access keys.

## 13. Validation and cost controls

Every change runs:

```text
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
tflint --recursive
checkov -d infra/terraform
terraform plan -var-file=environments/demo/terraform.tfvars
```

The reviewed plan identifies recurring-cost resources: ALB, NAT Gateway, ECS tasks, public IPv4 addresses, logs, endpoints, and the required production Supabase plan. AWS Budgets covers AWS spend; Supabase spending and quota alerts are configured in its own organization controls where supported.

## 14. Outputs

Platform outputs include CloudFront URL, ALB/API health URL, Cognito pool/client/domain identifiers, ECR repository URL, ECS cluster/service names, Supabase project reference and database secret ARN, data bucket name, queue/DLQ URLs, SES identity, log-group names, and AWS/Supabase regions. Sensitive values are marked sensitive and not echoed by deployment scripts.

## 15. Recovery and evolution

- Versioned state enables recovery from accidental state changes.
- Paid-tier Supabase restore procedures use a separate project/database before cutover; Free-tier logical exports are demo recovery exercises, not production backup guarantees.
- S3 versioning protects accidental object deletion until lifecycle expiry.
- DLQ replay uses a controlled tool and idempotent consumers.
- Per-AZ NAT, multiple API tasks, WAF, a custom domain, and higher Supabase compute/backup tiers are variable-driven hardening steps, not separate application architectures.
- Standard Docker images allow later ECS-on-EC2 placement if steady-state cost justifies managing capacity.
