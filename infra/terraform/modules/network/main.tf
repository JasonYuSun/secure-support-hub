data "aws_availability_zones" "available" {
  state = "available"
}

# ---------------------------------------------------------------------------------------------------------------------
# VPC
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "securehub-${var.environment}-vpc"
    Environment = var.environment
  }
}

# ---------------------------------------------------------------------------------------------------------------------
# SUBNETS
# COST-OPTIMIZED PROFILE: Public for ECS and ALB, Private for RDS (No NAT Gateway)
# ---------------------------------------------------------------------------------------------------------------------

resource "aws_subnet" "public" {
  count                   = length(var.public_subnets)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnets[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true # Required for ECS to pull images from ECR without NAT/VPC Endpoints

  tags = {
    Name        = "securehub-${var.environment}-public-${count.index + 1}"
    Environment = var.environment
  }
}

resource "aws_subnet" "private" {
  count             = length(var.private_subnets)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnets[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "securehub-${var.environment}-private-${count.index + 1}"
    Environment = var.environment
  }
}

# ---------------------------------------------------------------------------------------------------------------------
# INTERNET GATEWAY & ROUTING
# ---------------------------------------------------------------------------------------------------------------------
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name        = "securehub-${var.environment}-igw"
    Environment = var.environment
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name        = "securehub-${var.environment}-public-rt"
    Environment = var.environment
  }
}

resource "aws_route_table_association" "public" {
  count          = length(var.public_subnets)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ---------------------------------------------------------------------------------------------------------------------
# SECURITY GROUPS
# ---------------------------------------------------------------------------------------------------------------------

# ALB Security Group: Allow HTTP/HTTPS from Internet
resource "aws_security_group" "alb" {
  name        = "securehub-${var.environment}-alb-sg"
  description = "Allow inbound traffic for ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from Internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS from Internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "securehub-${var.environment}-alb-sg"
    Environment = var.environment
  }
}

# ECS Security Group: Allow traffic from ALB only
resource "aws_security_group" "ecs" {
  name        = "securehub-${var.environment}-ecs-sg"
  description = "Allow inbound traffic from ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Traffic from ALB"
    from_port       = 0
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "securehub-${var.environment}-ecs-sg"
    Environment = var.environment
  }
}

# RDS Security Group: Allow traffic from ECS only
resource "aws_security_group" "rds" {
  name        = "securehub-${var.environment}-rds-sg"
  description = "Allow inbound traffic from ECS"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "securehub-${var.environment}-rds-sg"
    Environment = var.environment
  }
}
