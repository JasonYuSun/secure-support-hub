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
  description = "ARN of the IAM role for GitHub Actions to assume"
  value       = module.oidc.github_actions_role_arn
}
