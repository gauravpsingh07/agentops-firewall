# Cloud architecture

AgentOps Firewall is **local-first** by design: the full stack runs on a
developer laptop via Docker Compose and a JDK 21 install. This document
describes how the same architecture would deploy to AWS and Azure without
requiring paid cloud resources during development or evaluation.

> **No live deployment is run from this repository.** The Terraform
> blueprint in `infra/terraform/aws/` is documentation-only; `terraform
> apply` is not expected to be invoked.

---

## Local → AWS mapping

| Local component | AWS target | Notes |
|---|---|---|
| Angular frontend (Nginx container) | S3 + CloudFront | Static build artefact; CloudFront for TLS + caching. |
| Spring Boot backend | ECS Fargate behind ALB | Stateless; horizontal scale by task count. |
| PostgreSQL 16 | RDS PostgreSQL (Single-AZ for dev, Multi-AZ for prod) | Flyway migrations run on each backend boot. |
| Kafka (KRaft, single-broker) | Amazon MSK Serverless | Topic-level access control via IAM. |
| RabbitMQ | Amazon MQ for RabbitMQ | Single-instance broker for dev; cluster for prod. |
| `.env` files | AWS Secrets Manager | One secret per environment; injected into ECS task definition. |
| Application logs | CloudWatch Logs | One log group per service; structured JSON via Logback. |
| Health checks | ALB target-group health checks | Backed by `/actuator/health`. |
| Static assets / DB backups | S3 (separate buckets) | Lifecycle rule moves backups to Glacier after 30 days. |

### Network sketch

```
                         ┌──────────────┐
        users  ────────► │  CloudFront  │
                         └──────┬───────┘
                                ▼
                          ┌────────┐
                          │   S3   │  (Angular bundle)
                          └────────┘

                         ┌──────────────┐
        agents ────────► │     ALB      │
                         └──────┬───────┘
                                ▼
                       ┌───────────────────┐
                       │   ECS Fargate     │
                       │   (Spring Boot)   │
                       └──┬─────────┬───┬──┘
                          │         │   │
                          ▼         ▼   ▼
                       ┌─────┐  ┌─────┐ ┌─────┐
                       │ RDS │  │ MSK │ │  MQ │
                       └─────┘  └─────┘ └─────┘
```

VPC layout: two private subnets (RDS, MSK, MQ) and two public subnets
(ALB, NAT). The ECS tasks live in the private subnets and reach RDS, MSK,
MQ via security-group rules scoped to the task role.

---

## Local → Azure mapping

| Local component | Azure target | Notes |
|---|---|---|
| Angular frontend | Azure Static Web Apps | Built CI artifact uploaded directly. |
| Spring Boot backend | Azure App Service for Containers **or** AKS | App Service is simpler for a portfolio narrative. |
| PostgreSQL 16 | Azure Database for PostgreSQL (Flexible Server) | Burstable B1 for dev. |
| Kafka | Azure Event Hubs Kafka-compatible endpoint | Native Kafka protocol, no client changes. |
| RabbitMQ | Containerised on AKS, or Service Bus topics if RabbitMQ is replaceable | Service Bus changes the wire protocol — keep RabbitMQ if portability matters. |
| Secrets | Azure Key Vault | App Service / AKS read via managed identity. |
| Logs / metrics | Azure Monitor + Log Analytics | OpenTelemetry exporter on the backend. |

---

## Free-local-dev guarantee

The project is intentionally evaluable without spending a cent on cloud
infrastructure:

- `docker compose up -d postgres kafka rabbitmq` brings up the data plane.
- `./mvnw spring-boot:run` runs the backend on `localhost:8080`.
- `npm start` serves the Angular app on `localhost:4200`.

Every cloud-equivalent in the tables above has a 100%-functional local
substitute. The frontend, backend, audit log, policy evaluator,
approval workflow, and Kafka/RabbitMQ event paths all behave identically
to their cloud counterparts.

---

## Cost-conscious notes

If a real deployment were attempted (not part of this portfolio):

- **MSK Serverless** is the cheapest Kafka option for sporadic traffic;
  provisioned MSK would be wasteful at dev/staging volumes.
- **Amazon MQ for RabbitMQ** charges per broker-hour; a single
  `mq.t3.micro` is enough for a demo but pin its uptime.
- **RDS Single-AZ** + automated snapshots is fine for non-production;
  promote to Multi-AZ before any production traffic.
- **CloudFront** has a generous free tier; ensure the cache TTL on the
  Angular bundle's `index.html` is short (e.g. 60 s) so deploys don't
  require a manual invalidation.
- **ECS Fargate Spot** can cut backend cost by ~70% for non-critical
  workloads; not appropriate for the approval workflow path because of
  unpredictable task termination.

---

## Terraform-ready folder

`infra/terraform/aws/` mirrors the planned production blueprint
(`main.tf`, `variables.tf`, `outputs.tf`). All resource blocks are
commented out, and the folder is documentation-only — see its README for
the safety rules.
