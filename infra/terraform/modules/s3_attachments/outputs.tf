output "bucket_name" {
  description = "S3 bucket name for attachments"
  value       = aws_s3_bucket.attachments.bucket
}

output "bucket_arn" {
  description = "S3 bucket ARN for attachments"
  value       = aws_s3_bucket.attachments.arn
}

output "bucket_regional_domain_name" {
  description = "Regional domain name of the attachment bucket"
  value       = aws_s3_bucket.attachments.bucket_regional_domain_name
}
