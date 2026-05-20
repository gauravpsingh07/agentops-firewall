# Terraform — AWS (placeholder)

> **This folder is documentation-only for the portfolio.** No real
> infrastructure is provisioned and no real secrets live here.

The eventual goal is to provide a Terraform-ready blueprint that mirrors the
local Docker Compose stack on AWS:

| Local service       | AWS target                                  |
|---------------------|---------------------------------------------|
| Angular frontend    | S3 + CloudFront                             |
| Spring Boot backend | ECS Fargate behind ALB                      |
| PostgreSQL          | RDS PostgreSQL (single-AZ for dev)          |
| Kafka               | Amazon MSK (Serverless option)              |
| RabbitMQ            | Amazon MQ for RabbitMQ                      |
| Secrets             | AWS Secrets Manager                         |
| Logs / metrics      | CloudWatch                                  |

## Safety rules

- Never commit `*.tfstate`, `*.tfvars`, or any `.terraform/` directory.
- Use placeholders (`CHANGE_ME`, `your_account_id_here`) — never real IDs, ARNs, or secrets.
- `terraform apply` is **not** expected to run from this repository.

The actual `main.tf`, `variables.tf`, and `outputs.tf` skeletons land in
Phase 8 (`chore: add Terraform-ready AWS folder`).
