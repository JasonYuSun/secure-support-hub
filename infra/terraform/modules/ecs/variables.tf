variable "environment" {
  type        = string
  description = "Environment name"
}

variable "aws_region" {
  type        = string
  description = "AWS region for CloudWatch Logs"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID where ECS will be deployed"
}

variable "public_subnet_ids" {
  type        = list(string)
  description = "List of public subnet IDs for ECS tasks (assumes cost-optimized dev profile)"
}

variable "ecs_security_group_id" {
  type        = string
  description = "Security Group ID for ECS tasks"
}

variable "alb_target_group_api_arn" {
  type        = string
  description = "ARN of the API Target Group"
}

variable "alb_target_group_web_arn" {
  type        = string
  description = "ARN of the Web Target Group"
}

variable "db_secret_arn" {
  type        = string
  description = "ARN of the RDS database secret in Secrets Manager"
}

variable "db_host" {
  type        = string
  description = "RDS Endpoint Host"
}

variable "db_port" {
  type        = number
  description = "RDS Endpoint Port"
}

variable "db_name" {
  type        = string
  description = "RDS Database Name"
}

variable "api_image_url" {
  type        = string
  description = "ECR Image URL for the API"
}

variable "web_image_url" {
  type        = string
  description = "ECR Image URL for the Web"
}

variable "api_cors_origin" {
  type        = string
  description = "Allowed CORS origin for the API"
  default     = "*" # Can be restricted when ALB DNS is known
}
