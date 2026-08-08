# AWS and Terraform Design

## 1. Objectives

Provision the same Talon priority behavior in any approved AWS account/region without source-code
changes. Terraform owns cloud runtime configuration; Supabase-hosted PostgreSQL remains external
and portable. Start with manual deployment, explicit cost review, and least privilege.

## 2. Structure

```text
infra/terraform/
  bootstrap/                  remote state bucket/locking
  modules/
    network edge web compute storage messaging secrets observability
  environments/dev/
    providers.tf variables.tf main.tf outputs.tf terraform.tfvars.example
```

Inputs include account/region/environment/domain, networking, image digests, task sizes/counts,
Supabase secret reference/value injection procedure, bucket naming prefix, queue limits, xAI/scanner
configuration, and alarms. No account ID, ARN, region, bucket name, credential, or Terraform state
is committed/hardcoded in application behavior.

### Resource naming and ownership

Terraform applies the owner suffix `-vraj` to every explicitly nameable AWS resource. Globally
unique and region/account-scoped identifiers place their uniqueness components before the final
suffix; for example, `talon-resumes-<account>-<region>-vraj`. ECS services, ECR repositories,
queues, log groups, IAM roles, security groups, load balancers, and any future EC2 instance `Name`
tag follow the same final-suffix rule within each service's naming constraints.

All supported resources also receive the default tags `Owner = "Vraj"` and
`Project = "TalonATS"`, plus environment and managed-by tags. AWS-generated ephemeral identifiers
such as ECS task IDs cannot be renamed; their parent service/task definition and propagated tags
carry the ownership convention instead. The naming suffix and tags affect Terraform configuration
only and never application behavior.

## 3. Topology

- CloudFront serves the SPA from a private S3 origin through Origin Access Control.
- ALB terminates API TLS and routes to ECS Fargate tasks in private subnets.
- API and worker use the same immutable image with separate profiles/commands.
- SQS + DLQ transports work references; PostgreSQL jobs remain authoritative.
- Private quarantine, clean-resume, and export storage is separated by bucket or strongly scoped
  prefixes/roles. Lifecycle removes quarantine failures promptly and exports after seven days.
- Secrets Manager supplies database/JWT/xAI/scanner secrets to task roles.
- CloudWatch logs, metrics, dashboards, and alarms cover service health, queue age/DLQ, import
  failures, Grok failures, and access anomalies.

NAT/egress cost is explicitly reviewed because workers need Google Drive/xAI/Supabase access.
Where supported and cost-effective, VPC endpoints reduce S3/SQS/ECR/logs egress dependencies.

## 4. PostgreSQL

Supabase Free can support local/first demo only. Spring uses TLS through the supported pooler/session
connection with JPA/JDBC/Flyway—not Supabase Auth, Storage, Data API, or Edge Functions. The
connection is a secret. Production requires an approved non-pausing tier and verified automated
backup/PITR or another approved managed PostgreSQL service; RDS is intentionally not required.

Migrations run as a one-off ECS task before application rollout. Database network allowlisting and
pool sizing are configured per environment.

## 5. Private storage controls

Terraform asserts:

- all four S3 Block Public Access flags are true;
- Object Ownership is bucket-owner-enforced, so ACLs are disabled;
- default encryption and HTTPS-only bucket policy;
- no public website configuration, wildcard public principal, or public ACL;
- versioning/lifecycle where justified and server access/audit visibility;
- API role can authorize exact clean/export GET signing, worker roles can access only required
  quarantine/clean/export prefixes, and frontend has no candidate-bucket permission;
- object keys and outputs reveal no candidate PII.

Presigned GET URLs are produced by the authorized API for one exact key and five minutes. They are
temporary bearer access, not a public-bucket mechanism.

## 6. Messaging and behavior parity

The local dispatcher and SQS implement one `WorkDispatcher` contract. Messages contain opaque work
IDs/version, not resume content, Drive URLs, or authoritative tenant claims. Workers load the
durable job, claim a lease, and execute the same idempotent handler. Visibility timeout exceeds
normal handling with heartbeat/extension if needed; bounded receives reach the DLQ and alarms.

## 7. Validation, deployment, and recovery

Run `terraform fmt -check`, `validate`, lint/security/policy tests, and a reviewed plan. Estimate
recurring ALB, NAT, ECS, CloudWatch, and data-transfer costs before apply. Use narrowly scoped
operator and future GitHub OIDC roles. Do not expose secret values in outputs.

Deployment outputs include CloudFront/API URLs, ECS/ECR identifiers, private bucket names, queue/
DLQ references, log groups, and secret ARNs. Recovery covers redeploying immutable images,
replaying idempotent DLQ work, restoring verified PostgreSQL backup/export according to tier, and
reconciling object metadata. Multi-AZ tasks, autoscaling, WAF, and richer CI are enabled when load
and risk justify them, without changing application contracts.
