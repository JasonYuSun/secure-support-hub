output "github_actions_role_arn" {
  description = "Deprecated compatibility output. Points to the deploy role ARN."
  value       = aws_iam_role.github_actions_deploy.arn
}

output "github_actions_deploy_role_arn" {
  description = "ARN of the IAM role for GitHub Actions CD deploy workflow"
  value       = aws_iam_role.github_actions_deploy.arn
}

output "github_actions_terraform_role_arn" {
  description = "ARN of the IAM role for GitHub Actions Terraform workflow"
  value       = aws_iam_role.github_actions_terraform.arn
}
