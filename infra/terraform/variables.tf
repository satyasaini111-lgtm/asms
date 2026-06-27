variable "aws_region" {
  default = "us-east-1"
}

variable "project" {
  default = "asms"
}

variable "env" {
  default = "prod"
}

variable "vpc_cidr" {
  default = "10.0.0.0/16"
}

variable "public_subnets" {
  default = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnets" {
  default = ["10.0.11.0/24", "10.0.12.0/24"]
}

variable "availability_zones" {
  default = ["us-east-1a", "us-east-1b"]
}

variable "eks_version" {
  default = "1.36"
}

variable "node_instance_type" {
  default = "t3.medium"
}

variable "node_desired" {
  default = 2
}

variable "node_min" {
  default = 2
}

variable "node_max" {
  default = 4
}

variable "services" {
  default = [
    "config-server", "api-gateway", "user-service", "amenity-service",
    "support-service", "visitor-service", "payment-service",
    "billing-service", "workflow-service", "notification-service", "helpbot-service"
  ]
}
