# Generate a random password for the master user
resource "random_password" "master_password" {
  length           = 24
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "securehub-${var.environment}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name        = "securehub-${var.environment}-db-subnet-group"
    Environment = var.environment
  }
}

# RDS Instance
resource "aws_db_instance" "main" {
  identifier        = "securehub-${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage

  db_name  = var.db_name
  username = var.db_username
  password = random_password.master_password.result
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_security_group_id]

  publicly_accessible = false
  multi_az            = false # Single AZ for MVP to save costs

  backup_retention_period = 7 # 7 days automated backups
  backup_window           = "14:00-15:00"

  storage_encrypted = true

  # WARNING: Set to true for MVP/Dev to allow effortless terraform destroy, change to false for prod!
  skip_final_snapshot = true

  tags = {
    Name        = "securehub-${var.environment}-db"
    Environment = var.environment
  }
}

# Store the complete credential payload securely in AWS Secrets Manager
resource "aws_secretsmanager_secret" "db_credentials" {
  name = "securehub-${var.environment}-db-credentials-v2"

  tags = {
    Name        = "securehub-${var.environment}-db-credentials"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.master_password.result
    dbname   = var.db_name
    host     = aws_db_instance.main.address
    port     = aws_db_instance.main.port
  })
}
