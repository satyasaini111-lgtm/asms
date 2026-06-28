terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state — create the S3 bucket + DynamoDB table once manually before init
  backend "s3" {
    bucket         = "asms-terraform-state-prod"
    key            = "asms/prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "asms-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.env
      ManagedBy   = "Terraform"
    }
  }
}

locals {
  name_prefix = "${var.project}-${var.env}"
}
