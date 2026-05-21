# Changelog

All notable changes to AgentOps Firewall are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

---

## [1.0.0] — 2026-05-21

First portfolio release. Complete local-first stack — backend, frontend,
infra, docs, CI, e2e — runnable from `docker compose up -d` plus the two
dev commands documented in the README.

### Backend

- Java 21 / Spring Boot 3.3.4 with Maven Wrapper, PostgreSQL 16, Flyway.
- JWT login + `/me`, BCrypt-hashed credentials, role-based access
  (ADMIN / REVIEWER / VIEWER) enforced via `@PreAuthorize`.
- Agent registry with hashed `X-Agent-Key` issuance / rotation; the raw
  key is returned exactly once on create.
- Action ingestion (`POST /api/agent-actions`) with synchronous policy
  decision and asynchronous Kafka fan-out
  (`agent.actions.received` / `.decided` / `.completed`).
- Policy engine: priority-sorted, coarse filters (action type, resource
  pattern, min risk) plus per-condition operators (EQUALS, NOT_EQUALS,
  IN, NOT_IN, EXISTS, NOT_EXISTS, CONTAINS, NOT_CONTAINS, MATCHES,
  GTE, LTE).
- Policy simulator (`POST /api/policies/simulate`) — full-fidelity
  evaluation without persisting or auditing.
- Approval workflow with RabbitMQ `approval.requests` queue, reviewer
  approve / reject endpoints, and 409 on race-conditioned double
  decisions.
- Append-only audit log with search, pagination, and JSON details.
- Dashboard summary APIs (counts, recent activity, risk + decision
  distributions, recent audit events).
- Uniform `ApiError` shape; hardened handlers for malformed JSON,
  missing query params, and non-UUID path variables (each now 400 with
  a useful message instead of 500).
- **124 tests** total: 45 Surefire unit + 79 Failsafe integration on
  Eclipse Temurin 21.0.11.

### Frontend

- Angular 18 standalone components, Tailwind CSS dark theme, RxJS,
  reactive forms.
- Login + JWT interceptor + APP_INITIALIZER that re-hydrates the user
  from `/api/auth/me` on refresh; 401 interceptor that clears the
  token and bounces to `/login`.
- Protected dashboard shell with role-aware navigation: VIEWER never
  sees the Approval inbox link.
- Pages: dashboard overview, action feed, policy management (list +
  editor with FormArray-driven conditions), simulator, approval inbox,
  audit log viewer with expandable detail rows.
- Hand-rolled CSS bar charts for risk and decision distributions —
  zero charting dependencies.
- Global `NotificationService` toasts wired in via a polite
  aria-live region.
- Karma unit tests for `AuthService` and `TokenStorageService`
  (6 specs).

### E2E

- Playwright config + two specs in `frontend/e2e/`:
  `smoke.spec.ts` (login → dashboard → sign out) and
  `approval-flow.spec.ts` (seed pending approval via API, then a
  reviewer approves it through the dashboard).
- Configurable `E2E_BASE_URL`; default `http://localhost:4200`.
- Not part of default CI; run locally with `npm run test:e2e` or via
  the manual `e2e.yml` GitHub Actions workflow.

### Infrastructure

- Multi-stage Dockerfiles for backend (`maven:3.9-eclipse-temurin-21`
  build / `eclipse-temurin:21-jre-alpine` runtime) and frontend
  (`node:20-alpine` build / `nginx:1.27-alpine` runtime).
- Full-stack `docker-compose.yml`: Postgres 16, Kafka 3.7 (KRaft),
  RabbitMQ 3 with management UI, backend, frontend — each with the
  appropriate healthcheck and `depends_on` wiring.
- Terraform-ready AWS blueprint in `infra/terraform/aws/` covering
  VPC, RDS Postgres, MSK Serverless, Amazon MQ for RabbitMQ, ECS
  Fargate + ALB, S3 + CloudFront, CloudWatch, and IAM. All resource
  blocks remain commented out per the documentation-only safety rule.

### CI

- Default `ci.yml` runs `mvn verify` (Surefire + Failsafe) on the
  backend and `npm run build` + `npm test` (Karma + headless Chrome)
  on the frontend for every push and pull request.
- Backend job uploads Surefire/Failsafe reports on failure.
- Concurrency group cancels the in-flight run when a new push lands.
- Separate `e2e.yml` workflow gated on `workflow_dispatch` brings up
  docker-compose and runs Playwright on demand.

### Documentation

- `README.md` — getting started + smoke test + troubleshooting.
- `docs/architecture.md` — request flow, policy evaluation, approval
  workflow, audit invariants, layering.
- `docs/api.md` — full endpoint reference with curl recipes.
- `docs/cloud-architecture.md` — AWS / Azure mapping + cost notes.
- `docs/decisions.md` — ADR-style record of the technology choices.
- `docs/screenshots/` — capture guidance for the dashboard tour.

### Security & operability

- No `.env`, secret, or credential file ever staged; `.gitignore` is
  strict and the audit log of every commit reflects this.
- Demo credentials live only in the `local` profile (Flyway V2 seed)
  and are documented as such everywhere they appear.
- Application logs route at INFO; the catch-all exception handler
  logs at DEBUG with the full stack trace and emits a generic 500
  message on the wire — caller-side errors never reach this path.

[1.0.0]: https://example.com/agentops-firewall/releases/tag/v1.0.0
