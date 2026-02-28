variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "environment" {
  type        = string
  description = "Environment name (e.g., dev, prod)"
  default     = "dev"
}

variable "github_user" {
  type        = string
  description = "GitHub username"
}

variable "github_repo" {
  type        = string
  description = "GitHub repository name"
}
