# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "securehub-${var.environment}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    base              = 1
    weight            = 100
    capacity_provider = "FARGATE"
  }
}

locals {
  api_container_environment = concat([
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "CORS_ALLOWED_ORIGINS", value = var.api_cors_origin },
    { name = "JWT_EXPIRATION_MS", value = "86400000" },
    { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
    { name = "AWS_REGION", value = var.aws_region },
    { name = "AWS_S3_ATTACHMENT_BUCKET_NAME", value = var.attachment_bucket_name }
    ],
    var.aws_s3_endpoint != "" ? [{ name = "AWS_S3_ENDPOINT", value = var.aws_s3_endpoint }] : []
  )
}

# CloudWatch Log Groups
resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/securehub-${var.environment}-api"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "web" {
  name              = "/ecs/securehub-${var.environment}-web"
  retention_in_days = 14
}

# IAM Roles
data "aws_iam_policy_document" "ecs_tasks_trust_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution_role" {
  name               = "securehub-${var.environment}-ecs-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust_policy.json
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_role_policy" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Allow Execution Role to read secrets from Secrets Manager (for DB) and SSM (for JWT)
resource "aws_iam_policy" "ecs_secrets_policy" {
  name        = "securehub-${var.environment}-ecs-secrets-policy"
  description = "Policy to allow ECS to read secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [var.db_secret_arn]
      },
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameters"
        ]
        Resource = [aws_ssm_parameter.jwt_secret.arn]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_secrets_attachment" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = aws_iam_policy.ecs_secrets_policy.arn
}

resource "aws_iam_role" "ecs_task_role" {
  name               = "securehub-${var.environment}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust_policy.json
}

resource "aws_iam_policy" "ecs_attachment_s3_policy" {
  name        = "securehub-${var.environment}-ecs-attachments-s3-policy"
  description = "Policy for ECS task role to access the attachments S3 bucket"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowListAttachmentBucket"
        Effect = "Allow"
        Action = [
          "s3:ListBucket"
        ]
        Resource = [var.attachment_bucket_arn]
      },
      {
        Sid    = "AllowAttachmentObjectCrud"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = ["${var.attachment_bucket_arn}/*"]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_attachment_s3_policy" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_attachment_s3_policy.arn
}

# JWT Secret stored in SSM
resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/securehub/${var.environment}/jwt-secret"
  description = "JWT Secret for signing tokens"
  type        = "SecureString"
  value       = random_password.jwt_secret.result

  lifecycle {
    ignore_changes = [value]
  }
}

# API Task Definition
resource "aws_ecs_task_definition" "api" {
  family                   = "securehub-${var.environment}-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${var.api_image_url}:latest"
    essential = true

    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]

    environment = local.api_container_environment

    secrets = [
      { name = "DB_USERNAME", valueFrom = "${var.db_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.db_secret_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  # Ignore container_definitions changes because GitHub Actions CD pipeline 
  # dynamically updates the image tag (github.sha) during deployment.
  lifecycle {
    ignore_changes = [container_definitions]
  }
}

# Web Task Definition
resource "aws_ecs_task_definition" "web" {
  family                   = "securehub-${var.environment}-web"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "web"
    image     = "${var.web_image_url}:latest"
    essential = true

    portMappings = [{
      containerPort = 80
      hostPort      = 80
      protocol      = "tcp"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.web.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  # Ignore container_definitions changes because GitHub Actions CD pipeline 
  # dynamically updates the image tag (github.sha) during deployment.
  lifecycle {
    ignore_changes = [container_definitions]
  }
}

# ECS Service - API
resource "aws_ecs_service" "api" {
  name            = "securehub-${var.environment}-api-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [var.ecs_security_group_id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = var.alb_target_group_api_arn
    container_name   = "api"
    container_port   = 8080
  }

  # Ignore task_definition changes so Terraform doesn't revert 
  # deployments triggered by the CI/CD pipeline.
  lifecycle {
    ignore_changes = [task_definition]
  }
}

# ECS Service - Web
resource "aws_ecs_service" "web" {
  name            = "securehub-${var.environment}-web-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.web.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [var.ecs_security_group_id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = var.alb_target_group_web_arn
    container_name   = "web"
    container_port   = 80
  }

  # Ignore task_definition changes so Terraform doesn't revert 
  # deployments triggered by the CI/CD pipeline.
  lifecycle {
    ignore_changes = [task_definition]
  }
}
