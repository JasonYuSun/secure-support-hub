variable "environment" {
  type        = string
  description = "Environment name"
}

variable "repo_names" {
  type        = list(string)
  description = "List of ECR repository names to create"
  default     = ["secure-support-hub-api", "secure-support-hub-web"]
}
