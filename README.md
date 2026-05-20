# AgentOps Firewall

> Policy, Audit, and Approval Layer for AI Agents.

AgentOps Firewall is a local-first full-stack platform that simulates how
organizations govern AI agents before they perform risky actions. Instead of
letting an AI agent directly send emails, delete files, call external APIs,
modify databases, deploy code, or run terminal commands, the agent submits a
proposed action to the firewall. The firewall evaluates the request against
policy, optionally requires human approval, and produces a complete audit
trail.

---

## Status

🚧 **Phase 0 — project scaffolding only.** No business logic implemented yet.
This commit establishes the monorepo structure, Docker Compose skeleton,
documentation placeholders, and CI scaffold. Subsequent phases will add auth,
persistence, policy engine, Kafka events, RabbitMQ approval workflow, and the
Angular dashboard.

---

## Tech stack

| Layer       | Technology                                            |
|-------------|-------------------------------------------------------|
| Frontend    | Angular (standalone components), TypeScript, RxJS, Tailwind CSS |
| Backend     | Java 21, Spring Boot 3, Spring Security, REST         |
| Database    | PostgreSQL 16 + Flyway migrations                     |
| Streaming   | Apache Kafka (agent action events)                    |
| Work queue  | RabbitMQ (human approval tasks)                       |
| DevOps      | Docker, Docker Compose, GitHub Actions                |
| Testing     | JUnit 5, Mockito, Spring Boot Test, Playwright        |
| Infra (docs)| Terraform-ready AWS skeleton                          |

---

## Repository layout

```
AgentOpsFirewall/
├── backend/        # Spring Boot 3 service (Java 21)
├── frontend/       # Angular dashboard
├── infra/          # Terraform skeleton + architecture diagrams
├── docs/           # Architecture, API, cloud, and decisions docs
├── .github/        # CI workflows
├── docker-compose.yml
├── CLAUDE.md       # Guardrails for AI-assisted development sessions
└── README.md
```

---

## Local development (planned)

Full instructions will land in `docs/` during Phase 8. The intended flow is:

```bash
cp .env.example .env             # fill placeholders locally
docker compose up -d postgres kafka rabbitmq
# backend
cd backend && ./mvnw spring-boot:run
# frontend
cd frontend && npm install && npm start
```

Demo credentials (local only):

| Username | Password    | Role     |
|----------|-------------|----------|
| admin    | admin123    | ADMIN    |
| reviewer | reviewer123 | REVIEWER |
| viewer   | viewer123   | VIEWER   |

These are seeded only in the local profile and are not safe for any
non-development environment.

---

## Documentation

- `docs/architecture.md` — system architecture and request flow
- `docs/api.md` — REST API reference
- `docs/cloud-architecture.md` — how local services map to AWS / Azure
- `docs/decisions.md` — engineering decisions (Kafka vs RabbitMQ, etc.)
- `infra/terraform/aws/README.md` — planned cloud infrastructure (docs only)

---

## License

TBD.
