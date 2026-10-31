variable "project" {
  type    = string
  default = "ticketing"
}

variable "environment" {
  description = "dev | prod: names, sizes and protections change per environment"
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "region" {
  type    = string
  default = "us-east-1"
}

variable "owner" {
  type = string
}

variable "cost_center" {
  type = string
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Two AZs: the service survives the loss of one"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "single_nat_gateway" {
  description = "Cost trade-off: one NAT in dev, one per AZ in prod for high availability"
  type        = bool
}

variable "container_image" {
  description = "Immutable image tag pushed by the CI pipeline to the ECR repository"
  type        = string
}

variable "task_cpu" {
  type = number
}

variable "task_memory" {
  type = number
}

variable "min_tasks" {
  type = number
}

variable "max_tasks" {
  type = number
}

variable "reservation_ttl" {
  type    = string
  default = "10m"
}

variable "acm_certificate_arn" {
  description = "TLS certificate for the public HTTPS listener"
  type        = string
}

variable "alarm_email" {
  description = "Receives the operational alarms"
  type        = string
}

variable "log_retention_days" {
  type = number
}
