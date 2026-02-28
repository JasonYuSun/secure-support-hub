variable "aws_region" {
  description = "AWS region to deploy the bootstrap resources"
  type        = string
  default     = "ap-southeast-2"
}

variable "state_bucket_name" {
  description = "Name of the S3 bucket intended to store Terraform state"
  type        = string
  default     = "secure-support-hub-tf-state-545063353041"
}

variable "dynamodb_table_name" {
  description = "Name of the DynamoDB table intended for state locking"
  type        = string
  default     = "secure-support-hub-tf-locks"
}
