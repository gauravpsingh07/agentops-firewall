# Changelog

All notable changes to AgentOps Firewall are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

Hardening pass plus four capability additions surfaced by a deep code
review: the messaging architecture gains a consumer side and a live feed,
approvals finally expire, agents can report execution outcomes, and the
login/ingestion paths are rate-limited.

### Added

- **Action completion callback.** `POST /api/agent-actions/{id}/complete`
  lets an agent report the outcome of an `ALLOWED`/`APPROVED` action; the
  action moves to the new `COMPLETED`/`FAILED` terminal state, is audited,
  and emits an `ACTION_COMPLETED` Kafka event. Authenticated by
  `X-Agent-Key`, scoped to the action's own agent.
- **Approval expiry sweeper.** A `@Scheduled` job expires `PENDING`
  approvals past their deadline — approval → `EXPIRED`, action failed closed
  to `DENIED`, and an `APPROVAL_EXPIRED` audit event written. Wires up the
  previously-dead `EXPIRED` status / audit event / dashboard filter.
  Configurable via `agentops.approvals.expiry.*`.
- **Broker consumers.** Kafka (`@KafkaListener`) and RabbitMQ
  (`@RabbitListener`) consumers project the action stream and approval queue
  into an in-memory `LiveMetricsService`, giving the topics/queues a reader.
  Guarded by `agentops.messaging.consumers.enabled`.
- **Live activity stream.** `GET /api/stream` (SSE) broadcasts consumed
  events; the Angular approval inbox live-reloads and shows a Live/Offline
  badge. JWT passed as an `access_token` query param since `EventSource`
  cannot set headers.
- **Rate limiting.** A dependency-free fixed-window limiter throttles
  `POST /api/auth/login` (per IP) and agent ingestion (per key) ahead of the
  security chain, returning `429` with `Retry-After`. Configurable via
  `agentops.security.rate-limit.*`.
- **Policy-update audit diff.** `POLICY_UPDATED` entries now include a
  field-level before/after diff of what changed.

### Changed

- **Broker publishing moved to after-commit.** Kafka/RabbitMQ events are now
  emitted via `@TransactionalEventListener(AFTER_COMMIT)` instead of inside
  the transaction, removing the dual-write hazard where a rollback could
  still emit a phantom event.
- **Action list query pushed to the database.** `GET /api/agent-actions` now
  filters, sorts, and paginates via a JPA `Specification` instead of loading
  the whole table into memory.

### Performance

- Policy evaluation caches compiled regex patterns and batch-loads
  conditions (`findByPolicyIdIn`) instead of an N+1 query per policy.
- `Agent.lastUsedAt` is stamped with a targeted `@Modifying` update, keeping
  the hottest write off the optimistic-locking path.

### Security

- Documented the stateless-JWT / `localStorage` tradeoff in `docs/api.md`.

---

## [1.0.1] — 2026-05-22

Patch release focused on local-development experience: hardens the
Docker Compose stack against Docker Desktop image-extraction
regressions, fixes a CORS gap that prevented the dashboard from
reading a successful login response in a real browser, aligns the
host-run backend's RabbitMQ defaults with the broker container, and
adds a dedicated troubleshooting guide plus the long-promised
dashboard screenshots.

### Fixed

- **Kafka container image.** Switched from `bitnami/kafka:3.7` (no
  longer published) to `confluentinc/cp-kafka:7.7.1`.
  `apache/kafka:3.9.0` was tried first and rejected because Docker
  Desktop's containerd image store extracts its layers as 0-byte
  files on some 4.6x builds (243/243 files empty, including the
  Kafka JARs themselves). cp-kafka uses the same `KAFKA_*` env-var
  convention so the broker config required no rename.
- **Kafka networking for host-run clients.** Added a
  `PLAINTEXT_HOST` listener on port 9092 advertising
  `localhost:9092` alongside the existing internal listener on
  `kafka:29092`. A backend launched on the host
  (`./mvnw spring-boot:run`) can now reach the broker; the
  compose-managed backend is pinned to the internal listener so
  container-to-container traffic stays inside the Docker network.
- **RabbitMQ auth from a host-run backend.** `application.yml`
  defaults changed from Spring's stock `guest:guest` to the
  `agentops:local_dev_only` pair the compose `rabbitmq` service
  already provisions. RabbitMQ's built-in `guest` user only accepts
  loopback connections, so the previous defaults made
  `/actuator/health` report DOWN until a per-shell env override
  was set.
- **CORS for the dev frontend.** `SecurityConfig` had
  `.cors(cors -> {})` wired into the filter chain but no
  `CorsConfigurationSource` bean to back it, so successful login
  responses came back without an `Access-Control-Allow-Origin`
  header and browsers refused to expose the JWT to the Angular
  app. A configurable bean
  (`agentops.security.cors.allowed-origins`, defaulting to
  `http://localhost:4200,http://localhost`) now emits the header
  and unblocks the dashboard.

### Added

- **`docs/troubleshooting.md`** — companion to the README's
  troubleshooting table. Eight entries covering issues caught
  during bring-up: containerd image-store regression, Kafka
  `appuser` lookup, host/container Kafka networking, opaque
  `/actuator/health` DOWN, CORS, disk recovery, PowerShell `curl`
  alias, and `wsl --unregister` cross-project blast radius.
- **README Docker Desktop callout.** Prerequisites now names the
  containerd image-store toggle so a first-time reader doesn't
  have to discover the workaround the hard way, plus a pointer to
  `docs/troubleshooting.md` for deeper diagnostics.
- **`docs/screenshots/*.png`.** Committed the eight dashboard PNGs
  the README's Demo section has been pointing at, captured via
  the existing `npm run screenshots` Playwright script against
  the full local stack (Postgres + Kafka + RabbitMQ + backend +
  Angular dev server).

[1.0.1]: https://example.com/agentops-firewall/releases/tag/v1.0.1

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
