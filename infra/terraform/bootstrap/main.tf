locals {
  state_bucket_name          = "talon-${var.environment}-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}-tfstate-vraj"
  candidate_files_bucket     = "talon-${var.environment}-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}-vraj"
  candidate_runtime_policy   = "talon-${var.environment}-candidate-file-runtime-vraj"
  github_actions_role_name   = "talon-${var.environment}-github-terraform-vraj"
  github_actions_policy_name = "talon-${var.environment}-terraform-permissions-vraj"
  state_key                  = "talon/${var.environment}/terraform.tfstate"
  github_subject             = "repo:${var.github_owner}/${var.github_repository}:ref:refs/heads/${var.github_branch}"
  github_oidc_provider_arn = coalesce(
    var.existing_github_oidc_provider_arn,
    one(aws_iam_openid_connect_provider.github[*].arn)
  )
  default_tags = {
    Owner       = "Vraj"
    Project     = "TalonATS"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_s3_bucket" "terraform_state" {
  bucket        = local.state_bucket_name
  force_destroy = false
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "terraform_state_transport" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*"
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  policy = data.aws_iam_policy_document.terraform_state_transport.json

  depends_on = [aws_s3_bucket_public_access_block.terraform_state]
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.existing_github_oidc_provider_arn == null ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "github_actions_trust" {
  statement {
    sid     = "GitHubMainBranchOnly"
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.github_subject]
    }
  }
}

resource "aws_iam_role" "github_terraform" {
  name                 = local.github_actions_role_name
  description          = "Applies the Talon development Terraform stack from the protected main branch."
  assume_role_policy   = data.aws_iam_policy_document.github_actions_trust.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "github_terraform" {
  statement {
    sid       = "ListStatePath"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.terraform_state.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values = [
        local.state_key,
        "${local.state_key}.tflock"
      ]
    }
  }

  statement {
    sid = "ReadWriteStateAndLock"
    actions = [
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = [
      "${aws_s3_bucket.terraform_state.arn}/${local.state_key}",
      "${aws_s3_bucket.terraform_state.arn}/${local.state_key}.tflock"
    ]
  }

  statement {
    sid       = "DeleteLockOnly"
    actions   = ["s3:DeleteObject"]
    resources = ["${aws_s3_bucket.terraform_state.arn}/${local.state_key}.tflock"]
  }

  statement {
    sid     = "ManageCandidateFilesBucket"
    actions = ["s3:*"]
    resources = [
      "arn:${data.aws_partition.current.partition}:s3:::${local.candidate_files_bucket}",
      "arn:${data.aws_partition.current.partition}:s3:::${local.candidate_files_bucket}/*"
    ]
  }

  statement {
    sid = "ManageCandidateRuntimePolicy"
    actions = [
      "iam:CreatePolicy",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicy",
      "iam:DeletePolicyVersion",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:SetDefaultPolicyVersion",
      "iam:TagPolicy",
      "iam:UntagPolicy"
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:policy/${local.candidate_runtime_policy}"
    ]
  }
}

resource "aws_iam_role_policy" "github_terraform" {
  name   = local.github_actions_policy_name
  role   = aws_iam_role.github_terraform.id
  policy = data.aws_iam_policy_document.github_terraform.json
}
