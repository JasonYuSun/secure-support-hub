variable "environment" {
  type        = string
  description = "Environment name"
}

variable "cors_allowed_origins" {
  type        = list(string)
  description = "Allowed CORS origins for direct browser uploads/downloads"

  validation {
    condition     = length(var.cors_allowed_origins) > 0
    error_message = "At least one CORS allowed origin is required."
  }
}

variable "abort_multipart_upload_days" {
  type        = number
  description = "Days after which incomplete multipart uploads are aborted"
  default     = 7
}

variable "noncurrent_version_expiration_days" {
  type        = number
  description = "Days to retain noncurrent object versions"
  default     = 30
}
