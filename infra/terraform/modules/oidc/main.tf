# GitHub OIDC Provider
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["1b511abead59c6ce207077c0bf0e0043b1382612"] # Standard GitHub thumbprint, though AWS now usually trusts the CA root
}

# IAM Role for GitHub Actions
data "aws_iam_policy_document" "github_actions_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Restrict to this specific repository (allow PRs and main branch to assume role)
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_user}/${var.github_repo}:ref:refs/heads/main",
        "repo:${var.github_user}/${var.github_repo}:pull_request",
        "repo:${var.github_user}/${var.github_repo}:environment:${var.environment}"
      ]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "securehub-${var.environment}-github-actions-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

# Policy for letting GitHub Actions deploy to ECS and ECR
resource "aws_iam_policy" "github_actions_deploy" {
  name        = "securehub-${var.environment}-github-deploy-policy"
  description = "Permissions for GitHub Actions to push to ECR and deploy to ECS"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # Get ECR auth token (cannot be resource-restricted)
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        # Push to ECR repositories
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage"
        ]
        Resource = [
          var.ecr_api_arn,
          var.ecr_web_arn
        ]
      },
      {
        # Register new task definitions
        Effect = "Allow"
        Action = [
          "ecs:RegisterTaskDefinition",
          "ecs:DescribeTaskDefinition"
        ]
        Resource = "*" # ECS Task definitions are versioned, so we allow * here
      },
      {
        # Update ECS Services
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices"
        ]
        Resource = [
          var.ecs_service_api_arn,
          var.ecs_service_web_arn
        ]
      },
      {
        # Pass Execution and Task Roles to ECS
        Effect = "Allow"
        Action = [
          "iam:PassRole"
        ]
        Resource = [
          var.ecs_task_execution_role_arn,
          var.ecs_task_role_arn
        ]
        Condition = {
          StringEquals = {
            "iam:PassedToService" : "ecs-tasks.amazonaws.com"
          }
        }
      },
      {
        # Allow Terraform State Management (S3 & DynamoDB)
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:DeleteItem"
        ]
        Resource = [
          "arn:aws:s3:::secure-support-hub-tf-state-*",
          "arn:aws:s3:::secure-support-hub-tf-state-*/*",
          "arn:aws:dynamodb:${var.aws_region}:*:table/secure-support-hub-tf-locks"
        ]
      },
      {
        # Allow Terraform pipeline to manage the attachments bucket configuration.
        Effect = "Allow"
        Action = [
          "s3:CreateBucket",
          "s3:DeleteBucket",
          "s3:GetBucketLocation",
          "s3:GetBucketAcl",
          "s3:PutBucketAcl",
          "s3:ListBucket",
          "s3:GetBucketWebsite",
          "s3:GetAccelerateConfiguration",
          "s3:GetBucketRequestPayment",
          "s3:GetBucketLogging",
          "s3:GetBucketTagging",
          "s3:PutBucketTagging",
          "s3:GetBucketPolicy",
          "s3:PutBucketPolicy",
          "s3:DeleteBucketPolicy",
          "s3:GetBucketPublicAccessBlock",
          "s3:PutBucketPublicAccessBlock",
          "s3:DeleteBucketPublicAccessBlock",
          "s3:GetBucketOwnershipControls",
          "s3:PutBucketOwnershipControls",
          "s3:DeleteBucketOwnershipControls",
          "s3:GetEncryptionConfiguration",
          "s3:PutEncryptionConfiguration",
          "s3:GetBucketVersioning",
          "s3:PutBucketVersioning",
          "s3:GetLifecycleConfiguration",
          "s3:PutLifecycleConfiguration",
          "s3:DeleteBucketLifecycle",
          "s3:GetBucketCORS",
          "s3:PutBucketCORS",
          "s3:DeleteBucketCORS"
        ]
        Resource = [var.attachment_bucket_arn]
      },
      {
        # Object-level permissions required when Terraform or automation needs object operations.
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = ["${var.attachment_bucket_arn}/*"]
      },
      {
        # MVP ONLY: Broad permissions to allow the single CI role to manage 
        # all infrastructure components defined in Terraform rather than dealing 
        # with bounded IAM boundaries (skipped dedicated TF role for MVP).
        Effect = "Allow"
        Action = [
          "ec2:*",
          "ecs:*",
          "ecr:*",
          "elasticloadbalancing:*",
          "rds:*",
          "logs:*",
          "iam:*",
          "secretsmanager:*",
          "ssm:*",
          "kms:*"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "github_actions_deploy" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.github_actions_deploy.arn
}
