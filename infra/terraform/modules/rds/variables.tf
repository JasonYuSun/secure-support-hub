variable "environment" {
  type        = string
  description = "Environment name"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID where RDS will be deployed"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "List of private subnet IDs for RDS"
}

variable "rds_security_group_id" {
  type        = string
  description = "Security Group ID for RDS"
}

variable "db_name" {
  type        = string
  description = "Name of the initial database"
  default     = "securehub"
}

variable "db_username" {
  type        = string
  description = "Master username for the database"
  default     = "securehub"
}

variable "instance_class" {
  type        = string
  description = "RDS instance class"
  default     = "db.t3.micro"
}

variable "allocated_storage" {
  type        = number
  description = "Allocated storage in GB"
  default     = 20
}
