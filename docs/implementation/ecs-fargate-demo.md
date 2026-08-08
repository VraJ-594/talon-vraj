# ECS Fargate demo deployment handoff

## Scope and current status

Status: the first runtime is deployed in AWS from commit `addbbd7`. One ECS task is running behind a
healthy ALB target, and the SPA and readiness path return HTTP 200. A production-only import-page
failure was then reproduced on the HTTP endpoint because `crypto.randomUUID()` requires a secure
context; its compatibility correction is the current patch awaiting image deployment.

This phase deploys the current priority ATS slice as one ECS Fargate task with two containers:
Nginx/React web and Spring Boot API. It intentionally excludes a custom domain, TLS, NAT gateways,
autoscaling, WAF, and production high availability.

## What changed

- Added immutable, scan-on-push private ECR repositories for the API and web images.
- Added an AWS Secrets Manager container secret named `talon-dev-runtime-vraj`; Terraform owns the
  secret metadata but never its value.
- Added a separate remote-state Terraform root for a minimal public-subnet ECS/Fargate runtime,
  Application Load Balancer, security groups, IAM roles, and CloudWatch logs.
- Extended the existing main-only GitHub OIDC role with scoped foundation, networking, and compute
  deployment policies.
- Changed the web Nginx configuration to proxy `/api/` and `/actuator/` to the API container over the
  task-local loopback interface.
- Extended the GitHub Actions workflow to verify the monorepo, apply the foundation, build and push
  commit-SHA images, and apply the runtime from an exact saved Terraform plan.

## Why this approach

- A two-container task preserves independent web/API images while avoiding service discovery,
  cross-service networking, and a second Fargate service for the demo.
- One ALB provides a generated HTTP address and same-origin API/cookie behavior without needing a
  domain or frontend runtime secrets.
- Public task subnets avoid NAT gateway cost. The task security group accepts traffic only from the
  ALB, and the ALB ingress is supplied as an explicit demo CIDR variable.
- Secrets Manager keeps Supabase, Groq, and token-signing values out of Terraform state, source,
  image layers, GitHub variables, and frontend JavaScript.
- Commit-SHA image tags plus immutable ECR repositories tie a deployment to reviewed source and
  prevent mutable-tag drift.

## How the important paths work

1. The foundation root owns the private candidate bucket, both ECR repositories, and the runtime
   secret container.
2. An operator writes the ignored local runtime values to Secrets Manager out of band. Terraform
   does not create a secret version.
3. CI verifies backend, frontend, and the Nginx routing contract, then builds both images using the
   merge commit SHA as their immutable tag.
4. The runtime root reads foundation outputs through remote state, creates the VPC/ALB/ECS resources,
   and injects individual secret JSON keys into the API container.
5. The ALB sends all HTTP traffic to Nginx on port 80. Nginx serves the SPA and proxies backend paths
   to `127.0.0.1:8080` inside the shared Fargate task network namespace.
6. The API uses external Supabase PostgreSQL, Groq when enabled, and the private S3 bucket via its
   task role. Candidate objects are never exposed through a public bucket.

## Files and modules affected

- `apps/api/Dockerfile`
- `apps/web/Dockerfile`
- `apps/web/nginx.conf`
- `scripts/verify-nginx-ecs-contract.ps1`
- `infra/terraform/bootstrap/`
- `infra/terraform/environments/dev/`
- `infra/terraform/environments/dev-runtime/`
- `.github/workflows/terraform-apply.yml`
- `.gitignore`

## Verification and observed results

- Backend Maven gate passed: 123 tests, executable JAR produced.
- Frontend lint passed; Vitest passed 88 tests across 15 files; production build passed.
- Nginx ECS routing contract passed for `/api/`, `/actuator/`, loopback upstream, upload size, and SPA
  fallback.
- Terraform bootstrap, foundation, and runtime roots initialized and validated successfully.
- The runtime placeholder plan reported 21 additions, 0 changes, and 0 destroys; it was not applied.
- Foundation apply created two ECR repositories and the secret metadata with 5 additions and no
  changes or destroys.
- Bootstrap permission extension applied 6 additions, 1 in-place change, and no destroys; the OIDC
  trust remains restricted to `repo:VraJ-594/talon-vraj:ref:refs/heads/main`.
- The runtime secret has an `AWSCURRENT` version containing eight expected keys; no value was printed
  or written to Terraform.
- Docker Desktop 29.6.2 is healthy with overlayfs. Local images observed:
  `talon-api-vraj:local` (`964dfd1e3e7b`, 444 MB) and
  `talon-web-vraj:local` (`1ddc0dc6e615`, 79.8 MB).
- Commit `addbbd71724fc5900cb30462fa04a1c726586695` was built and pushed to both private ECR
  repositories. Runtime apply created 21 resources with no updates or deletes. ECS reported one
  running task, the exact Terraform target group reported `healthy`, and both the readiness and SPA
  paths returned HTTP 200.
- HTTP import regression: the focused test failed with `crypto.randomUUID is not a function` when
  `randomUUID` was unavailable, matching the ALB production error. The fallback now uses
  `crypto.getRandomValues()` to create an RFC 4122 version-4 identifier; the focused suite passed
  7/7 after the correction.

## Known blockers, prerequisites, and exact next step

- The HTTP UUID correction must be committed, pushed as a new immutable web image, and deployed
  before the import page is considered production-smoke green.
- After apply, wait for ECS service stability and smoke the generated ALB URL, readiness endpoint,
  SPA fallback, login/refresh/logout, candidate list/search, and import workflow.
- The real Drive-to-S3 row-level smoke remains a separate acceptance gate. Do not claim reliable S3
  import delivery until an imported public PDF is observed as a private S3 object.
- GitHub repository variables still need to be configured before the first `main` workflow run:
  `AWS_ROLE_ARN`, `AWS_REGION`, `TF_STATE_BUCKET`, and JSON-array `DEMO_INGRESS_CIDRS`.
- GitHub currently reports `codex/backend-api` as the default branch even though `main` exists. Push
  the UUID correction to both branches to exercise the `main` workflow, then change the repository
  default branch to `main` so workflow discovery and pull requests use the intended base.
- The first `main` workflow reached OIDC but AWS rejected its token because the job declared the
  `development` GitHub Environment, changing the token subject away from the role's exact main-ref
  trust. Repository variables are already used, so the environment declaration was removed. The
  next run will retain the strict `refs/heads/main` trust without storing AWS credentials.
- CloudTrail then exposed the repository's customized ID-bound subject as
  `repo:VraJ-594@149707336/talon-vraj@1327513749:ref:refs/heads/main`. Bootstrap Terraform now models
  the immutable owner and repository IDs explicitly and retains the exact main-ref restriction.
