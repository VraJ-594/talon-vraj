# Minimal ECS Fargate Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy the Talon frontend and API as one versioned two-container ECS Fargate task behind a generated ALB URL with one running task.

**Architecture:** The existing development Terraform root becomes the foundation owner for private ECR repositories and an empty runtime secret. A separate remote-state root owns the VPC, ALB, ECS roles/task/service, and consumes immutable API/web image URIs. Nginx serves the SPA and proxies same-origin API/health traffic to Spring Boot through localhost inside the task.

**Tech Stack:** Terraform 1.15.8, AWS provider 6.58.0, ECS Fargate, ALB, ECR, Secrets Manager, CloudWatch Logs, Docker, Nginx, Spring Boot 3.5, React/Vite, GitHub Actions OIDC.

## Global Constraints

- Every explicitly nameable AWS resource ends in `-vraj`.
- Run exactly one Fargate task at 0.5 vCPU/1 GiB; create no EC2 instances, NAT Gateway, autoscaling, Route53, ACM, or WAF.
- The generated ALB endpoint is HTTP/demo-only. Use only synthetic credentials and candidate data and set `TALON_SECURITY_COOKIE_SECURE=false`.
- Never put runtime secret values in Terraform inputs/state, GitHub variables, source, logs, or outputs.
- Keep both S3 buckets private and preserve their existing state/resources without destructive changes.
- GitHub AWS access remains OIDC-only and restricted to `VraJ-594/talon-vraj` on `main`.
- Use immutable Git commit SHA image tags and deploy the exact image URIs planned by Terraform.

---

### Task 1: Same-task web/API proxy contract

**Files:**
- Create: `scripts/verify-nginx-ecs-contract.ps1`
- Modify: `apps/web/nginx.conf`
- Verify: `apps/api/Dockerfile`, `apps/web/Dockerfile`

**Interfaces:**
- Consumes: API paths `/api/**` and `/actuator/health/readiness` on `127.0.0.1:8080`.
- Produces: web container port 80 serving SPA assets and proxying both backend path families unchanged.

- [ ] **Step 1: Add a failing proxy-contract check**

Create a PowerShell check that reads `apps/web/nginx.conf`, requires two locations (`/api/` and
`/actuator/`), and requires `proxy_pass http://127.0.0.1:8080` in each block. It must exit nonzero
when either contract is absent.

- [ ] **Step 2: Prove the current configuration fails**

Run `pwsh -File scripts/verify-nginx-ecs-contract.ps1` and observe a missing proxy-location failure.

- [ ] **Step 3: Add minimal Nginx proxying**

Add exact `/api/` and `/actuator/` locations with `proxy_http_version 1.1`, forwarded host/protocol,
bounded connect/read timeouts, and `proxy_pass http://127.0.0.1:8080` without a URI suffix so paths
are preserved. Keep SPA and immutable asset behavior unchanged.

- [ ] **Step 4: Verify the proxy contract and both image builds**

Run the contract script, then build `apps/api/Dockerfile` and `apps/web/Dockerfile` from repository
root. Expected: check and both builds exit zero.

### Task 2: Deployment foundation resources

**Files:**
- Create: `infra/terraform/environments/dev/ecr.tf`
- Create: `infra/terraform/environments/dev/secrets.tf`
- Modify: `infra/terraform/environments/dev/outputs.tf`
- Modify: `infra/terraform/bootstrap/main.tf`
- Modify: `infra/terraform/bootstrap/outputs.tf`
- Modify: `infra/terraform/environments/dev/README.md`
- Create: `infra/terraform/environments/dev/runtime-secret.json.example`

**Interfaces:**
- Produces: `api_repository_url`, `web_repository_url`, `runtime_secret_arn`, existing candidate bucket/policy ARN, and expanded branch-restricted CI permissions.

- [ ] **Step 1: Add private immutable ECR repositories**

Create `talon-dev-api-vraj` and `talon-dev-web-vraj` with scan-on-push, immutable tags, AES256
encryption, and lifecycle retention of the latest 20 tagged images plus seven-day expiry for
untagged images.

- [ ] **Step 2: Add an empty runtime secret shell**

Create `talon-dev-runtime-vraj` with seven-day recovery and no `aws_secretsmanager_secret_version`.
Add an ignored-value JSON example containing only placeholder property names for database,
security/demo-admin, and Groq settings.

- [ ] **Step 3: Extend GitHub OIDC permissions**

Add the runtime state key/lockfile and only the ECR push, ECS/ELB/EC2 networking, CloudWatch Logs,
Secrets Manager metadata, and exact IAM role/policy actions required by foundation/runtime
Terraform. Retain the exact `main` trust subject and existing storage permissions.

- [ ] **Step 4: Validate, plan, and apply foundation changes**

Run Terraform format/validate and a saved plan. Confirm no S3/state replacement or destruction,
then apply the exact plan and verify both repositories and the secret shell exist.

### Task 3: Minimal ECS runtime root

**Files:**
- Create: `infra/terraform/environments/dev-runtime/versions.tf`
- Create: `infra/terraform/environments/dev-runtime/providers.tf`
- Create: `infra/terraform/environments/dev-runtime/variables.tf`
- Create: `infra/terraform/environments/dev-runtime/foundation.tf`
- Create: `infra/terraform/environments/dev-runtime/network.tf`
- Create: `infra/terraform/environments/dev-runtime/iam.tf`
- Create: `infra/terraform/environments/dev-runtime/alb.tf`
- Create: `infra/terraform/environments/dev-runtime/ecs.tf`
- Create: `infra/terraform/environments/dev-runtime/outputs.tf`
- Create: `infra/terraform/environments/dev-runtime/backend.hcl.example`
- Create: `infra/terraform/environments/dev-runtime/terraform.tfvars.example`
- Create: `infra/terraform/environments/dev-runtime/README.md`

**Interfaces:**
- Consumes: foundation remote-state outputs, `api_image_uri`, `web_image_uri`, and explicit
`allowed_demo_cidrs`.
- Produces: `demo_url`, cluster/service names, and one healthy ECS task.

- [ ] **Step 1: Define provider/backend and validated inputs**

Use state key `talon/dev/runtime/terraform.tfstate`, require two full ECR image URIs, require at
least one CIDR, and default region/environment/capacity to `ap-south-1`, `dev`, `512`, and `1024`.

- [ ] **Step 2: Read foundation state and create the public VPC**

Read foundation outputs from the private state bucket. Create `10.42.0.0/16`, two `/24` public
subnets in the first two available AZs, Internet Gateway, and one public route table. Add default
ownership tags and `-vraj` Name tags.

- [ ] **Step 3: Create least-privilege ECS roles and logs**

Create exact execution/application roles. Execution may pull the two images, write the one log
group, and read the one runtime secret. Application attaches the existing candidate-file runtime
policy. Create `/talon/dev/ecs-vraj` with seven-day retention.

- [ ] **Step 4: Create ALB and security boundaries**

Allow port 80 into the ALB only from `allowed_demo_cidrs`; allow port 80 into tasks only from the ALB
security group. Create HTTP listener and an IP target group whose readiness path is
`/actuator/health/readiness`.

- [ ] **Step 5: Create the task definition and one-task service**

Define essential `web` and `api` containers in one task. Inject non-secrets as API environment and
runtime JSON properties through ECS `secrets`. Enable Fargate, public IP outbound, deployment
circuit breaker/rollback, `desired_count=1`, and ALB registration for web port 80.

- [ ] **Step 6: Validate and plan without applying before images/secrets exist**

Run format/init/validate and plan using real immutable image URIs and an approved demo CIDR. Expected:
only runtime resources are created; existing foundation resources are read, not managed.

### Task 4: OIDC build-and-deploy workflow

**Files:**
- Modify: `.github/workflows/terraform-apply.yml`

**Interfaces:**
- Consumes: repository variables `AWS_ROLE_ARN`, `AWS_REGION`, `TF_STATE_BUCKET`, and
`DEMO_INGRESS_CIDRS`.
- Produces: SHA-tagged images and an exact runtime Terraform apply on `main`.

- [ ] **Step 1: Expand trusted paths and language verification**

Trigger for API/web/Docker/Terraform/workflow changes. Run backend verification plus frontend install,
lint, tests, and build before authenticating to AWS.

- [ ] **Step 2: Apply foundation before image build**

Initialize/apply the existing development root, read repository URLs using Terraform outputs, and
fail if the runtime secret has no current version without printing secret content.

- [ ] **Step 3: Build and push immutable images**

Authenticate Docker to ECR with the short-lived OIDC session. Build both existing Dockerfiles from
repository root and push only `${GITHUB_SHA}` tags.

- [ ] **Step 4: Apply the exact runtime plan**

Initialize the runtime state, plan with full SHA image URIs and decoded demo CIDRs, apply the saved
plan, then poll ECS/ALB readiness and print only the generated demo URL.

### Task 5: End-to-end deployment gate and handoff

**Files:**
- Modify: `docs/architecture/aws-terraform-design.md`
- Modify: `docs/implementation/aws-terraform-bootstrap.md`
- Create: `docs/implementation/ecs-fargate-demo.md`

**Interfaces:**
- Produces: current evidence, manual setup commands, blockers, and exact next step without secrets or candidate PII.

- [ ] **Step 1: Populate the runtime secret outside Terraform**

Create an ignored local JSON file from the example, fill it from existing ignored environments, and
use `aws secretsmanager put-secret-value`. Confirm only that a current version exists.

- [ ] **Step 2: Build/push and apply runtime from the operator session**

Push commit-SHA images, review/apply runtime Terraform, and wait for one healthy task.

- [ ] **Step 3: Run deployed smoke checks**

Verify the ALB URL serves the SPA and readiness. Sign in with synthetic credentials, refresh once,
and import one synthetic CSV row with a public Drive PDF. Verify an opaque private S3 object exists
without outputting its key or candidate data.

- [ ] **Step 4: Run regression and safety gates**

Run Maven verification, frontend lint/tests/build, Terraform no-drift plans, workflow formatting,
`git diff --check`, and staged secret/state/PII scans.

- [ ] **Step 5: Update handoff, commit, and push**

Record observed results and remaining HTTP/PII limitation, commit on `codex/backend-api`, and push.
Do not merge to `main` until GitHub repository variables are configured.
