# ADR 0004: ECS Fargate for Initial AWS Compute

- Status: Accepted
- Date: 2026-08-07

## Context

The backend needs a standard container runtime on AWS. Managing EC2 hosts or Kubernetes would consume time unrelated to ATS features. Initial traffic is small but API and worker capacity must remain independently scalable.

## Decision

Deploy API and worker profiles as ECS Fargate services behind an ALB where applicable. Begin with small tasks and one desired task per profile. Keep standard Docker images so ECS-on-EC2 remains a future cost optimization.

## Consequences

- AWS manages host provisioning and patching.
- Baseline Fargate/ALB/NAT costs exceed a single unmanaged VM.
- Task roles, networking, health checks, and rolling deployments are explicit Terraform resources.
- EKS and direct EC2 administration are excluded initially.
