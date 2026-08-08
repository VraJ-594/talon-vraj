locals {
  candidate_files_bucket_name = "talon-${var.environment}-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}-vraj"
  resource_prefix             = "talon-${var.environment}"
  default_tags = {
    Owner       = "Vraj"
    Project     = "TalonATS"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_s3_bucket" "candidate_files" {
  bucket        = local.candidate_files_bucket_name
  force_destroy = false
}

resource "aws_s3_bucket_public_access_block" "candidate_files" {
  bucket = aws_s3_bucket.candidate_files.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "candidate_files" {
  bucket = aws_s3_bucket.candidate_files.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_versioning" "candidate_files" {
  bucket = aws_s3_bucket.candidate_files.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "candidate_files" {
  bucket = aws_s3_bucket.candidate_files.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "candidate_files" {
  bucket = aws_s3_bucket.candidate_files.id

  depends_on = [aws_s3_bucket_versioning.candidate_files]

  rule {
    id     = "expire-quarantine"
    status = "Enabled"

    filter {
      prefix = "quarantine/"
    }

    expiration {
      days = 2
    }

    noncurrent_version_expiration {
      noncurrent_days = 2
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  rule {
    id     = "expire-exports"
    status = "Enabled"

    filter {
      prefix = "exports/"
    }

    expiration {
      days = 7
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }

  rule {
    id     = "expire-noncurrent-clean-resumes"
    status = "Enabled"

    filter {
      prefix = "clean/"
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "candidate_files" {
  count  = length(var.allowed_web_origins) == 0 ? 0 : 1
  bucket = aws_s3_bucket.candidate_files.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "HEAD"]
    allowed_origins = var.allowed_web_origins
    expose_headers  = ["ETag", "Content-Length", "Content-Type"]
    max_age_seconds = 300
  }
}

data "aws_iam_policy_document" "candidate_files_transport" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.candidate_files.arn,
      "${aws_s3_bucket.candidate_files.arn}/*"
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "candidate_files" {
  bucket = aws_s3_bucket.candidate_files.id
  policy = data.aws_iam_policy_document.candidate_files_transport.json

  depends_on = [aws_s3_bucket_public_access_block.candidate_files]
}

data "aws_iam_policy_document" "candidate_file_runtime" {
  statement {
    sid = "ListPrivatePrefixes"
    actions = [
      "s3:ListBucket"
    ]
    resources = [aws_s3_bucket.candidate_files.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values = [
        "imports/*",
        "quarantine/*",
        "clean/*",
        "exports/*"
      ]
    }
  }

  statement {
    sid = "ProcessPrivateCandidateFiles"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = [
      "${aws_s3_bucket.candidate_files.arn}/imports/*",
      "${aws_s3_bucket.candidate_files.arn}/quarantine/*",
      "${aws_s3_bucket.candidate_files.arn}/clean/*",
      "${aws_s3_bucket.candidate_files.arn}/exports/*"
    ]
  }
}

resource "aws_iam_policy" "candidate_file_runtime" {
  name        = "${local.resource_prefix}-candidate-file-runtime-vraj"
  description = "Allows the combined Talon API and in-process worker to handle private candidate files."
  policy      = data.aws_iam_policy_document.candidate_file_runtime.json
}
