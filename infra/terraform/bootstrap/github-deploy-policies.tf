data "aws_iam_policy_document" "github_foundation_deploy" {
  statement {
    sid = "AuthenticateAndCreateFoundation"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:CreateRepository",
      "secretsmanager:CreateSecret"
    ]
    resources = ["*"]
  }

  statement {
    sid = "ManageExactEcrRepositories"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:DeleteLifecyclePolicy",
      "ecr:DeleteRepository",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:GetLifecyclePolicy",
      "ecr:InitiateLayerUpload",
      "ecr:ListImages",
      "ecr:ListTagsForResource",
      "ecr:PutImage",
      "ecr:PutImageScanningConfiguration",
      "ecr:PutImageTagMutability",
      "ecr:PutLifecyclePolicy",
      "ecr:TagResource",
      "ecr:UntagResource",
      "ecr:UploadLayerPart"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/${local.api_repository_name}",
      "arn:${data.aws_partition.current.partition}:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/${local.web_repository_name}"
    ]
  }

  statement {
    sid = "ManageExactRuntimeSecretMetadata"
    actions = [
      "secretsmanager:DeleteSecret",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "secretsmanager:ListSecretVersionIds",
      "secretsmanager:TagResource",
      "secretsmanager:UntagResource",
      "secretsmanager:UpdateSecret"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:${local.runtime_secret_name}-*"
    ]
  }
}

resource "aws_iam_policy" "github_foundation_deploy" {
  name        = "talon-${var.environment}-github-foundation-vraj"
  description = "Allows the trusted Talon main-branch workflow to manage and push exact foundation artifacts."
  policy      = data.aws_iam_policy_document.github_foundation_deploy.json
}

resource "aws_iam_role_policy_attachment" "github_foundation_deploy" {
  role       = aws_iam_role.github_terraform.name
  policy_arn = aws_iam_policy.github_foundation_deploy.arn
}

data "aws_iam_policy_document" "github_network_deploy" {
  statement {
    sid = "ManageDemoNetworking"
    actions = [
      "ec2:*InternetGateway*",
      "ec2:*Route*",
      "ec2:*SecurityGroup*",
      "ec2:*Subnet*",
      "ec2:*Vpc*",
      "ec2:CreateTags",
      "ec2:DeleteTags",
      "ec2:DescribeAccountAttributes",
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeNetworkInterfaces",
      "elasticloadbalancing:*"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "github_network_deploy" {
  name        = "talon-${var.environment}-github-network-vraj"
  description = "Allows the trusted Talon main-branch workflow to manage the minimal demo VPC and ALB."
  policy      = data.aws_iam_policy_document.github_network_deploy.json
}

resource "aws_iam_role_policy_attachment" "github_network_deploy" {
  role       = aws_iam_role.github_terraform.name
  policy_arn = aws_iam_policy.github_network_deploy.arn
}

data "aws_iam_policy_document" "github_compute_deploy" {
  statement {
    sid = "ManageDemoEcsAndLogs"
    actions = [
      "ecs:*",
      "logs:CreateLogGroup",
      "logs:DeleteLogGroup",
      "logs:DescribeLogGroups",
      "logs:ListTagsForResource",
      "logs:PutRetentionPolicy",
      "logs:TagResource",
      "logs:UntagResource"
    ]
    resources = ["*"]
  }

  statement {
    sid = "ManageExactEcsRoles"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:PassRole",
      "iam:PutRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.ecs_execution_role_name}",
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${local.ecs_application_role_name}"
    ]
  }
}

resource "aws_iam_policy" "github_compute_deploy" {
  name        = "talon-${var.environment}-github-compute-vraj"
  description = "Allows the trusted Talon main-branch workflow to manage the minimal ECS demo runtime."
  policy      = data.aws_iam_policy_document.github_compute_deploy.json
}

resource "aws_iam_role_policy_attachment" "github_compute_deploy" {
  role       = aws_iam_role.github_terraform.name
  policy_arn = aws_iam_policy.github_compute_deploy.arn
}
