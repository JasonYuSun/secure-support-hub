# GitHub OIDC Provider
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["1b511abead59c6ce207077c0bf0e0043b1382612"] # Standard GitHub thumbprint, though AWS now usually trusts the CA root
}

# IAM Role trust policy for GitHub Actions OIDC
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

# GitHub Actions Deploy Role (CD pipeline, least privilege)
resource "aws_iam_role" "github_actions_deploy" {
  name               = "securehub-${var.environment}-github-deploy-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

# Policy for CD deploy workflow (ECR + ECS only)
resource "aws_iam_policy" "github_actions_deploy" {
  name        = "securehub-${var.environment}-github-deploy-policy"
  description = "Least-privilege permissions for GitHub Actions CD deploy workflow"

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
    ]
  })
}

resource "aws_iam_role_policy_attachment" "github_actions_deploy_attachment" {
  role       = aws_iam_role.github_actions_deploy.name
  policy_arn = aws_iam_policy.github_actions_deploy.arn
}

# GitHub Actions Terraform Role (IaC pipeline, intentionally broader for MVP)
resource "aws_iam_role" "github_actions_terraform" {
  name               = "securehub-${var.environment}-github-terraform-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

resource "aws_iam_policy" "github_actions_terraform" {
  name        = "securehub-${var.environment}-github-terraform-policy"
  description = "Broad permissions for GitHub Actions Terraform workflow (MVP)"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # Terraform backend state and locks
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
        # Attachment bucket management including replication and other bucket reads
        Effect = "Allow"
        Action = [
          "s3:*"
        ]
        Resource = [
          var.attachment_bucket_arn,
          "${var.attachment_bucket_arn}/*"
        ]
      },
      {
        # Wider IaC permissions for MVP
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
          "kms:*",
          "acm:*",
          "cloudwatch:*"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "github_actions_terraform_attachment" {
  role       = aws_iam_role.github_actions_terraform.name
  policy_arn = aws_iam_policy.github_actions_terraform.arn
}
