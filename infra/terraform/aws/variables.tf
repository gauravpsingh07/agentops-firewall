# ---------------------------------------------------------------------------
# AgentOps Firewall — Terraform input variables.
#
# Defaults are placeholder-friendly. A real deployment would pass values
# via `-var-file` or environment variables, never by editing this file.
# ---------------------------------------------------------------------------

variable "aws_region" {
  description = "AWS region in which to deploy."
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Short identifier prepended to resource names. Use 3–10 chars, lowercase."
  type        = string
  default     = "agentops"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,9}$", var.name_prefix))
    error_message = "name_prefix must be 3–10 chars, lowercase, alphanumeric or hyphens."
  }
}

variable "environment" {
  description = "Deployment environment tag (dev / staging / prod)."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "availability_zones" {
  description = "AZs to spread subnets across. Length must be >= 2."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]

  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "Provide at least two availability zones for HA."
  }
}

variable "backend_image" {
  description = "Fully-qualified ECR image URI for the backend container."
  type        = string
  default     = "CHANGE_ME.dkr.ecr.us-east-1.amazonaws.com/agentops/backend:latest"
}

variable "frontend_domain" {
  description = "Optional custom domain for CloudFront. Empty string disables custom-domain config."
  type        = string
  default     = ""
}
