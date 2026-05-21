# AgentOps Firewall

> Policy, audit, and approval layer for AI agents.

AgentOps Firewall mediates risky AI agent actions. Instead of letting an
agent directly send emails, delete files, call external APIs, modify
databases, deploy code, or run terminal commands, the agent submits a
proposed action to the firewall. The firewall validates the agent's
identity, evaluates the request against a policy set, optionally routes
it through a human reviewer, and produces a complete audit trail.

---

## What's included

- **Backend** (Java 21, Spring Boot 3.3) — REST API, JWT + agent-key auth,
  policy engine, approval workflow, audit log, Kafka + RabbitMQ
  publishers, 121 Surefire + Failsafe tests on H2.
- **Frontend** (Angular 18 standalone) — login, role-aware dashboard
  shell, action feed, policy management, simulator, approval inbox,
  audit-log viewer, analytics with hand-rolled bar charts. Karma unit
  tests + Playwright smoke spec.
- **Infra** — Docker Compose for Postgres / Kafka / RabbitMQ, multi-stage
  Dockerfiles for backend + frontend, GitHub Actions CI, Terraform-ready
  AWS skeleton (docs only).

For the request-flow walkthrough, see [`docs/architecture.md`](docs/architecture.md).
For the endpoint reference, see [`docs/api.md`](docs/api.md).

---

## Tech stack

| Layer       | Technology                                                    |
|-------------|---------------------------------------------------------------|
| Frontend    | Angular 18 (standalone), TypeScript, RxJS, Tailwind CSS       |
| Backend     | Java 21, Spring Boot 3.3, Spring Security, REST               |
| Persistence | PostgreSQL 16, Flyway migrations, Spring Data JPA             |
| Streaming   | Apache Kafka (KRaft single-broker for local)                  |
| Work queue  | RabbitMQ                                                      |
| DevOps      | Docker, Docker Compose, GitHub Actions                        |
| Testing     | JUnit 5, Mockito, Spring Boot Test, Karma/Jasmine, Playwright |
| Infra (docs)| Terraform-ready AWS skeleton                                  |

---

## Repository layout

```
AgentOpsFirewall/
├── backend/        # Spring Boot 3 service (Java 21)
├── frontend/       # Angular dashboard
├── infra/          # Terraform skeleton + architecture diagrams
├── docs/           # Architecture, API, cloud, decisions
├── .github/        # CI workflows
├── docker-compose.yml
├── CLAUDE.md       # Guardrails for AI-assisted development sessions
└── README.md
```

---

## Local development

### Prerequisites

- **JDK 21** (Eclipse Temurin recommended)
- **Node 20+** and npm
- **Docker** with Docker Compose v2
- ~2 GB free disk for container images and Maven cache

### One-time setup

```bash
git clone <this-repo>
cd AgentOpsFirewall
cp .env.example .env                   # fill placeholders if needed
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

`.env` files are gitignored. The defaults are safe for local development.

### Run

```bash
# 1. Bring up the data plane.
docker compose up -d postgres kafka rabbitmq

# 2. Backend (in one terminal).
cd backend
./mvnw spring-boot:run

# 3. Frontend (in another terminal).
cd frontend
npm install
npm start
```

Open <http://localhost:4200> and sign in with one of the demo accounts
below.

### Demo credentials (local profile only)

| Username | Password    | Role     | Sees |
|----------|-------------|----------|------|
| admin    | admin123    | ADMIN    | Everything |
| reviewer | reviewer123 | REVIEWER | Everything except agent/policy writes |
| viewer   | viewer123   | VIEWER   | Read-only; no approval inbox |

These are seeded by Flyway migration `V2__seed_local_demo_users.sql`
under the `local` Spring profile. They are **not** safe for any
non-development environment.

### Smoke test the API

```bash
# Log in as admin.
TOKEN=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

# Hit a protected endpoint.
curl -sS http://localhost:8080/api/dashboard/summary \
  -H "Authorization: Bearer $TOKEN"
```

---

## Tests

```bash
# Backend (Surefire unit + Failsafe integration)
cd backend && ./mvnw verify

# Frontend unit tests (Karma + ChromeHeadless)
cd frontend && npm test

# Frontend end-to-end (Playwright)
#   Requires backend and frontend running locally first.
cd frontend
npx playwright install chromium    # one-time
npm run test:e2e
```

CI runs the first two on every push and pull request. The e2e suite is
gated behind a manual `workflow_dispatch` (`.github/workflows/e2e.yml`)
to keep PR feedback fast.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Connection refused` on port 5432 | `docker compose ps` — Postgres still starting; healthcheck takes ~10s. |
| Backend boots but Flyway fails | Stale schema from a previous run. `docker compose down -v` wipes the volume. |
| `npm start` complains about Node version | Angular 18 requires Node 18.19+ or 20.11+. `node --version`. |
| `mvn` not found | Use the wrapper: `./mvnw` (or `mvnw.cmd` on Windows). |
| Login returns 500 | JWT signing key not set. Confirm `JWT_SECRET` in `backend/.env` or use the local default. |
| Karma tests can't find Chrome | Install Chrome stable, or set `CHROME_BIN` to your Chrome path. |

---

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — components, request flow, policy evaluation, approval workflow, audit log, DB layout
- [`docs/api.md`](docs/api.md) — full REST endpoint reference with curl recipes
- [`docs/cloud-architecture.md`](docs/cloud-architecture.md) — local → AWS / Azure mapping, free-local-dev guarantee, cost notes
- [`docs/decisions.md`](docs/decisions.md) — engineering decisions (Kafka vs RabbitMQ, Flyway vs Liquibase, Playwright vs Cypress, etc.)
- [`infra/terraform/aws/README.md`](infra/terraform/aws/README.md) — Terraform blueprint (documentation only)

---

## License

TBD.
