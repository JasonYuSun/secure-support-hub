output "terraform_state_bucket_name" {
  description = "Name of the S3 bucket created for Terraform state"
  value       = aws_s3_bucket.terraform_state.bucket
}

output "terraform_locks_table_name" {
  description = "Name of the DynamoDB table created for Terraform state locking"
  value       = aws_dynamodb_table.terraform_locks.name
}
