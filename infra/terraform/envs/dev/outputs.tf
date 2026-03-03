output "alb_dns_name" {
  description = "The DNS name of the load balancer"
  value       = module.alb.alb_dns_name
}

output "ecr_repository_urls" {
  description = "URLs of the ECR repositories"
  value       = module.ecr.repository_urls
}

output "rds_endpoint" {
  description = "Connection endpoint for the RDS instance"
  value       = module.rds.db_endpoint
}

output "github_actions_role_arn" {
  description = "Deprecated compatibility output. Points to the CD deploy role ARN"
  value       = module.oidc.github_actions_role_arn
}

output "github_actions_deploy_role_arn" {
  description = "ARN of the IAM role for GitHub Actions CD deploy workflow"
  value       = module.oidc.github_actions_deploy_role_arn
}

output "github_actions_terraform_role_arn" {
  description = "ARN of the IAM role for GitHub Actions Terraform workflow"
  value       = module.oidc.github_actions_terraform_role_arn
}

output "attachments_bucket_name" {
  description = "Name of the S3 bucket for request/comment attachments"
  value       = module.s3_attachments.bucket_name
}

output "attachments_bucket_arn" {
  description = "ARN of the S3 bucket for request/comment attachments"
  value       = module.s3_attachments.bucket_arn
}

output "ecs_task_role_name" {
  description = "ECS task role name used by API runtime"
  value       = module.ecs.task_role_name
}

output "ecs_task_role_arn" {
  description = "ECS task role ARN used by API runtime"
  value       = module.ecs.task_role_arn
}

output "ai_provider" {
  description = "AI provider configured for API runtime"
  value       = module.ecs.ai_provider
}

output "ai_bedrock_model_id" {
  description = "Bedrock model id configured for API runtime"
  value       = module.ecs.ai_bedrock_model_id
}
