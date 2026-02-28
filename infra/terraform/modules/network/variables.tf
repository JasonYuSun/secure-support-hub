variable "environment" {
  type        = string
  description = "Environment name"
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the VPC"
  default     = "10.0.0.0/16"
}

variable "public_subnets" {
  type        = list(string)
  description = "CIDR blocks for the public subnets (ALB & ECS)"
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnets" {
  type        = list(string)
  description = "CIDR blocks for the private subnets (RDS)"
  default     = ["10.0.10.0/24", "10.0.20.0/24"]
}

variable "availability_zones" {
  type        = list(string)
  description = "List of availability zones"
  default     = ["ap-southeast-2a", "ap-southeast-2b"]
}
