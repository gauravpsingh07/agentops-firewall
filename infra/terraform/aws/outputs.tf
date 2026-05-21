# ---------------------------------------------------------------------------
# AgentOps Firewall — Terraform outputs.
#
# Outputs are also commented out because every block references a
# resource that itself is commented out in main.tf. Uncommenting both
# main.tf and these outputs together is the intended path to a real
# deployment.
# ---------------------------------------------------------------------------

# output "alb_dns_name" {
#   description = "DNS name of the ALB fronting the Spring Boot backend."
#   value       = aws_lb.backend.dns_name
# }
#
# output "cloudfront_domain" {
#   description = "Public CloudFront domain serving the Angular bundle."
#   value       = aws_cloudfront_distribution.frontend.domain_name
# }
#
# output "rds_endpoint" {
#   description = "PostgreSQL connection endpoint. Sensitive."
#   value       = aws_db_instance.postgres.address
#   sensitive   = true
# }
#
# output "msk_bootstrap_brokers" {
#   description = "MSK bootstrap broker string (IAM auth)."
#   value       = aws_msk_serverless_cluster.events.bootstrap_brokers_sasl_iam
#   sensitive   = true
# }
#
# output "mq_broker_endpoint" {
#   description = "Amazon MQ broker AMQP endpoint."
#   value       = aws_mq_broker.approvals.instances[0].endpoints[0]
#   sensitive   = true
# }
