variable "environment" {
  type        = string
  description = "Environment name"
}

variable "aws_region" {
  type        = string
  description = "AWS Region where the infrastructure is deployed"
}

variable "github_user" {
  type        = string
  description = "GitHub organization or username"
}

variable "github_repo" {
  type        = string
  description = "GitHub repository name"
}

variable "ecs_cluster_arn" {
  type        = string
  description = "ARN of the ECS Cluster"
}

variable "ecs_service_api_arn" {
  type        = string
  description = "ARN of the API ECS Service"
}

variable "ecs_service_web_arn" {
  type        = string
  description = "ARN of the Web ECS Service"
}

variable "ecr_api_arn" {
  type        = string
  description = "ARN of the API ECR Repository"
}

variable "ecr_web_arn" {
  type        = string
  description = "ARN of the Web ECR Repository"
}

variable "ecs_task_execution_role_arn" {
  type        = string
  description = "ARN of the ECS Task Execution Role"
}

variable "ecs_task_role_arn" {
  type        = string
  description = "ARN of the ECS Task Role"
}

variable "attachment_bucket_arn" {
  type        = string
  description = "ARN of the S3 bucket used for request/comment attachments"
}
