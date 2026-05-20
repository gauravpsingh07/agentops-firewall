# Variable declarations placeholder. Defaults intentionally generic.

variable "aws_region" {
  description = "AWS region for the AgentOps Firewall deployment."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Logical project name used for resource tagging."
  type        = string
  default     = "agentops-firewall"
}
