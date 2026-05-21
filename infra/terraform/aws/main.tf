# ---------------------------------------------------------------------------
# AgentOps Firewall — AWS blueprint (documentation-only).
#
# All `resource` blocks in this file are intentionally commented out.
# `terraform init` will succeed; `terraform apply` is not expected to run
# from this repository. The goal is to show the production-shaped
# blueprint a real deployment would follow, not to provision anything.
#
# To exercise this locally a developer would:
#   1. Uncomment the resource blocks.
#   2. Configure provider credentials (`AWS_PROFILE`, `AWS_REGION`).
#   3. Replace every `CHANGE_ME` placeholder.
#   4. Run `terraform plan` first, before any apply.
# ---------------------------------------------------------------------------

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# provider "aws" {
#   region = var.aws_region
# }

# ----- Networking ----------------------------------------------------------
#
# resource "aws_vpc" "this" {
#   cidr_block           = "10.42.0.0/16"
#   enable_dns_support   = true
#   enable_dns_hostnames = true
#
#   tags = local.common_tags
# }
#
# resource "aws_subnet" "public" {
#   count                   = 2
#   vpc_id                  = aws_vpc.this.id
#   cidr_block              = cidrsubnet(aws_vpc.this.cidr_block, 8, count.index)
#   availability_zone       = element(var.availability_zones, count.index)
#   map_public_ip_on_launch = true
#   tags = merge(local.common_tags, { Tier = "public" })
# }
#
# resource "aws_subnet" "private" {
#   count             = 2
#   vpc_id            = aws_vpc.this.id
#   cidr_block        = cidrsubnet(aws_vpc.this.cidr_block, 8, count.index + 10)
#   availability_zone = element(var.availability_zones, count.index)
#   tags = merge(local.common_tags, { Tier = "private" })
# }

# ----- Database (RDS PostgreSQL) ------------------------------------------
#
# resource "aws_db_subnet_group" "postgres" {
#   name       = "${var.name_prefix}-pg"
#   subnet_ids = aws_subnet.private[*].id
#   tags       = local.common_tags
# }
#
# resource "aws_db_instance" "postgres" {
#   identifier              = "${var.name_prefix}-pg"
#   engine                  = "postgres"
#   engine_version          = "16.3"
#   instance_class          = "db.t4g.micro"
#   allocated_storage       = 20
#   storage_type            = "gp3"
#   db_name                 = "agentops"
#   username                = "agentops"
#   manage_master_user_password = true
#   db_subnet_group_name    = aws_db_subnet_group.postgres.name
#   vpc_security_group_ids  = [] # CHANGE_ME — add backend SG
#   skip_final_snapshot     = true
#   deletion_protection     = false
#   publicly_accessible     = false
#   tags                    = local.common_tags
# }

# ----- Kafka (MSK Serverless) ---------------------------------------------
#
# resource "aws_msk_serverless_cluster" "events" {
#   cluster_name = "${var.name_prefix}-events"
#   vpc_config {
#     subnet_ids         = aws_subnet.private[*].id
#     security_group_ids = [] # CHANGE_ME
#   }
#   client_authentication {
#     sasl { iam { enabled = true } }
#   }
#   tags = local.common_tags
# }

# ----- RabbitMQ (Amazon MQ) -----------------------------------------------
#
# resource "aws_mq_broker" "approvals" {
#   broker_name        = "${var.name_prefix}-approvals"
#   engine_type        = "RabbitMQ"
#   engine_version     = "3.13"
#   host_instance_type = "mq.t3.micro"
#   publicly_accessible = false
#   subnet_ids         = [aws_subnet.private[0].id]
#   security_groups    = [] # CHANGE_ME
#   user {
#     username = "agentops"
#     password = "CHANGE_ME" # store in Secrets Manager and read via data source
#   }
#   tags = local.common_tags
# }

# ----- Backend (ECS Fargate behind ALB) -----------------------------------
#
# resource "aws_ecs_cluster" "this" {
#   name = "${var.name_prefix}-cluster"
#   tags = local.common_tags
# }
#
# resource "aws_ecs_task_definition" "backend" {
#   family                   = "${var.name_prefix}-backend"
#   network_mode             = "awsvpc"
#   requires_compatibilities = ["FARGATE"]
#   cpu                      = "512"
#   memory                   = "1024"
#   execution_role_arn       = aws_iam_role.ecs_exec.arn
#   task_role_arn            = aws_iam_role.ecs_task.arn
#   container_definitions    = jsonencode([{
#     name      = "backend"
#     image     = "CHANGE_ME.dkr.ecr.${var.aws_region}.amazonaws.com/agentops/backend:latest"
#     essential = true
#     portMappings = [{ containerPort = 8080, protocol = "tcp" }]
#     environment = [
#       { name = "SPRING_PROFILES_ACTIVE", value = "prod" }
#     ]
#     secrets = [
#       { name = "POSTGRES_PASSWORD", valueFrom = "CHANGE_ME" }, # SSM/Secrets Manager ARN
#       { name = "JWT_SECRET",        valueFrom = "CHANGE_ME" }
#     ]
#     logConfiguration = {
#       logDriver = "awslogs"
#       options = {
#         awslogs-group         = aws_cloudwatch_log_group.backend.name
#         awslogs-region        = var.aws_region
#         awslogs-stream-prefix = "backend"
#       }
#     }
#   }])
#   tags = local.common_tags
# }
#
# resource "aws_lb" "backend" {
#   name               = "${var.name_prefix}-alb"
#   load_balancer_type = "application"
#   internal           = false
#   subnets            = aws_subnet.public[*].id
#   security_groups    = [] # CHANGE_ME — allow 443 from 0.0.0.0/0
#   tags               = local.common_tags
# }

# ----- Frontend (S3 + CloudFront) -----------------------------------------
#
# resource "aws_s3_bucket" "frontend" {
#   bucket = "${var.name_prefix}-frontend-CHANGE_ME"
#   tags   = local.common_tags
# }
#
# resource "aws_cloudfront_distribution" "frontend" {
#   enabled             = true
#   default_root_object = "index.html"
#   origin {
#     domain_name = aws_s3_bucket.frontend.bucket_regional_domain_name
#     origin_id   = "frontend-s3"
#     # OAC config omitted for brevity
#   }
#   default_cache_behavior {
#     target_origin_id       = "frontend-s3"
#     viewer_protocol_policy = "redirect-to-https"
#     allowed_methods        = ["GET", "HEAD"]
#     cached_methods         = ["GET", "HEAD"]
#     forwarded_values {
#       query_string = false
#       cookies { forward = "none" }
#     }
#   }
#   restrictions { geo_restriction { restriction_type = "none" } }
#   viewer_certificate { cloudfront_default_certificate = true }
#   tags = local.common_tags
# }

# ----- Observability ------------------------------------------------------
#
# resource "aws_cloudwatch_log_group" "backend" {
#   name              = "/agentops/${var.name_prefix}/backend"
#   retention_in_days = 30
#   tags              = local.common_tags
# }

# ----- IAM (sketched) ------------------------------------------------------
#
# resource "aws_iam_role" "ecs_exec" {
#   name               = "${var.name_prefix}-ecs-exec"
#   assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
#   tags               = local.common_tags
# }
#
# resource "aws_iam_role" "ecs_task" {
#   name               = "${var.name_prefix}-ecs-task"
#   assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
#   tags               = local.common_tags
# }

# ----- Local values --------------------------------------------------------
#
locals {
  common_tags = {
    Project     = "agentops-firewall"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
