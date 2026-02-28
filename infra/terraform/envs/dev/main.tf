terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "secure-support-hub-tf-state-545063353041"
    key            = "dev/terraform.tfstate"
    region         = "ap-southeast-2"
    dynamodb_table = "secure-support-hub-tf-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

# ---------------------------------------------------------------------------------------------------------------------
# MODULE CALLS WILL GO HERE IN PHASE 5
# ---------------------------------------------------------------------------------------------------------------------

module "network" {
  source      = "../../modules/network"
  environment = var.environment
}

module "ecr" {
  source      = "../../modules/ecr"
  environment = var.environment
}

module "rds" {
  source                = "../../modules/rds"
  environment           = var.environment
  vpc_id                = module.network.vpc_id
  private_subnet_ids    = module.network.private_subnet_ids
  rds_security_group_id = module.network.rds_security_group_id
}

data "aws_caller_identity" "current" {}

module "alb" {
  source                = "../../modules/alb"
  environment           = var.environment
  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  alb_security_group_id = module.network.alb_security_group_id
}

module "ecs" {
  source                   = "../../modules/ecs"
  environment              = var.environment
  aws_region               = var.aws_region
  vpc_id                   = module.network.vpc_id
  public_subnet_ids        = module.network.public_subnet_ids
  ecs_security_group_id    = module.network.ecs_security_group_id
  alb_target_group_api_arn = module.alb.target_group_api_arn
  alb_target_group_web_arn = module.alb.target_group_web_arn
  db_secret_arn            = module.rds.db_setup_secret_arn

  db_host = split(":", module.rds.db_endpoint)[0]
  db_port = 5432
  db_name = "securehub"

  api_image_url = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/secure-support-hub-api"
  web_image_url = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/secure-support-hub-web"

  api_cors_origin = "http://${module.alb.alb_dns_name}"
}

module "oidc" {
  source                      = "../../modules/oidc"
  environment                 = var.environment
  aws_region                  = var.aws_region
  github_user                 = var.github_user
  github_repo                 = var.github_repo
  ecs_cluster_arn             = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster/securehub-${var.environment}-cluster"
  ecs_service_api_arn         = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/securehub-${var.environment}-cluster/securehub-${var.environment}-api-service"
  ecs_service_web_arn         = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/securehub-${var.environment}-cluster/securehub-${var.environment}-web-service"
  ecr_api_arn                 = "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/secure-support-hub-api"
  ecr_web_arn                 = "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/secure-support-hub-web"
  ecs_task_execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/securehub-${var.environment}-ecs-execution-role"
  ecs_task_role_arn           = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/securehub-${var.environment}-ecs-task-role"
}
