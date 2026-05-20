# CLAUDE.md — AgentOps Firewall

Guardrails for any AI-assisted development session in this repository.
Read this file at the start of every session.

---

## Project identity

AgentOps Firewall is a local-first full-stack platform that mediates AI agent
actions. Agents submit proposed actions; the firewall validates identity,
evaluates policy, optionally requires human approval, and writes an audit
trail. Target audience for the portfolio: backend / platform / security /
AI-infra recruiters and hiring managers.

---

## Stack (locked-in decisions)

- **Backend**: Java 21, Spring Boot 3.3.x (or latest stable 3.x), Maven Wrapper, Spring Security, JWT, Spring Data JPA, Flyway, Spring for Apache Kafka, Spring AMQP (RabbitMQ).
- **Database**: PostgreSQL 16.
- **Frontend**: Angular (standalone components), TypeScript, RxJS, Tailwind CSS, default Angular test setup (Karma/Jasmine).
- **E2E**: Playwright.
- **DevOps**: Docker, Docker Compose, GitHub Actions CI.
- **Messaging tests**: mocks for Kafka/RabbitMQ; Testcontainers only if it stays cheap.
- **Demo users (local only)**: admin/admin123, reviewer/reviewer123, viewer/viewer123.

---

## Workflow rules

1. Keep everything inside this folder. Do not touch unrelated directories.
2. Never push to GitHub unless the user explicitly asks.
3. Local commits only after a phase is complete and verified.
4. Before each commit:
   - run `git status`
   - confirm no `.env`, secret, credential, key, dump, or token-like file is staged
   - if any secret-like file is detected, stop and tell the user immediately
5. Use professional commit messages (Conventional Commits style).
   No "update", "fix", "changes", "final", "final-final".
6. Target ~45–50 meaningful commits total across the whole project.
7. After each phase, run relevant build/tests where possible.
8. Inspect before coding. Produce a plan. Wait for approval.

---

## Secret & git safety

- Never commit `.env`, `.env.local`, `.env.development`, `.env.production`, or any variant.
- Never commit private keys, API keys, tokens, credentials, real passwords, database dumps, or generated secret files.
- Only commit `.env.example` files with placeholders (`CHANGE_ME`, `local_dev_only`, `example_token_only`, `your_api_key_here`).
- Do not print secrets in terminal output, logs, docs, tests, README, or example code.
- `.gitignore` is the source of truth — extend it before adding any new file type that could leak secrets.

---

## Architectural rules

- **Kafka** = append-only event stream of agent activity (topics: `agent.actions.received`, `agent.actions.decided`, `agent.actions.completed`).
- **RabbitMQ** = durable work queue for human approval tasks (queue: `approval.requests`).
- The distinction must be honored in code and documented in `docs/decisions.md`.
- `POST /api/agent-actions` returns the decision synchronously; events are emitted asynchronously for downstream consumers.
- Audit log is append-only and is the source of truth for status transitions.
- Agent identity uses a hashed API key (`X-Agent-Key`); user identity uses JWT.

---

## Phase roadmap

- **Phase 0** — Scaffolding (this commit).
- **Phase 1** — Auth & JWT & RBAC.
- **Phase 2** — Persistence & base entities.
- **Phase 3** — Agent registry & action ingestion & Kafka.
- **Phase 4** — Policy engine & simulator.
- **Phase 5** — Approval workflow & RabbitMQ.
- **Phase 6** — Audit logging & dashboard APIs.
- **Phase 7** — Angular dashboard.
- **Phase 8** — Tests, CI, docs, Terraform skeleton, polish.

Do not skip phases. Wait for explicit approval before starting the next one.
