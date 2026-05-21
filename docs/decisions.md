# Engineering decisions

A short record of the choices that shaped AgentOps Firewall. Each entry
states the call, the alternatives weighed, and why the chosen option
wins for this project. Format borrows from ADRs but stays compact.

---

## Backend platform — Java 21 + Spring Boot 3.3

**Alternatives considered:** Node/Express, Go (Echo/Chi), Python/FastAPI.

**Why Spring Boot:** the project's narrative is "platform-grade backend
for governing AI agents". Spring's track record in regulated /
enterprise contexts (security, JPA, observability, validation,
declarative AOP) is the strongest portfolio signal for backend +
platform-engineering roles. Java 21 also brings virtual threads,
records, and pattern matching — modern surface without leaving the
mainstream stack.

**Why not Node/Go/Python:** all three are smaller-stack stories. None
matches the "Spring Security + JPA + Flyway + actuator + Kafka + AMQP"
batteries that ship out of the box.

---

## Frontend — Angular 18 (standalone) + Tailwind

**Alternatives considered:** React, Vue, Svelte.

**Why Angular:** ships a strong opinionated story (DI, routing,
reactive forms, signals, change-detection strategies) that matches the
backend's opinionated story. Standalone components remove the historic
NgModule complexity that put people off the framework. Tailwind covers
styling without dragging in a component library.

**Why not React:** more decisions for the reader to evaluate (router?
state lib? forms lib? styling?). Angular's "one official answer" plays
to the portfolio narrative.

---

## Persistence — PostgreSQL 16

**Alternatives considered:** MySQL, SQLite (for portability), MongoDB.

**Why PostgreSQL:** strongest JSON support of the relational engines,
trivial Docker setup, the default for the AWS / Azure managed targets
in [`cloud-architecture.md`](cloud-architecture.md), and Flyway has
first-class support. The audit log uses `JSONB` for `detailsJson`,
which would be a meaningfully worse story on MySQL.

**Why not MongoDB:** the data model has hard relational constraints
(policy → policy_condition, action_request → policy_decision, etc.).
Forcing it into a document store would lose the readability of the
schema and trade away foreign-key safety.

---

## Migrations — Flyway

**Alternatives considered:** Liquibase, Spring's `data.sql`.

**Why Flyway:** plain SQL migrations, predictable filename ordering
(`V1__init_schema.sql`, `V2__seed_local_demo_users.sql`,
`V3__seed_sample_policies.sql`), works from any environment (CLI,
Docker, Spring Boot starter), and has zero XML.

**Why not Liquibase:** changelog XML/YAML is an extra abstraction
layer. For a project where every migration is straightforward SQL,
that abstraction is pure cost.

---

## Event stream + work queue — Kafka *and* RabbitMQ

This is the single most-questioned choice; the project uses **both**
on purpose.

**Kafka** carries the **event stream of agent activity** — append-only
records of what the firewall observed and decided. Three topics:
`agent.actions.received`, `agent.actions.decided`,
`agent.actions.completed`. Many independent consumers can read this
log without coordinating, which is the right shape for downstream
analytics, alerting, SIEM integrations, and time-travel debugging.

**RabbitMQ** carries the **human-approval work queue** —
`approval.requests`. Exactly one reviewer should claim each task; the
durable queue + message-acknowledge model fits the human-in-the-loop
workflow naturally. A Kafka topic would force every consumer to
coordinate offsets to avoid double-claiming a task, which is awkward
for this use case.

**Why not pick one:** Kafka can be coerced into a work-queue (consumer
groups + careful offset management) and RabbitMQ can be coerced into a
stream (durable queues + fanout), but each is the wrong shape for the
other job. Using both is the most honest model. Both have a clean
managed AWS equivalent (MSK Serverless / Amazon MQ for RabbitMQ).

---

## Local-first + Docker Compose

**Why local-first:** the project must be evaluable for **zero spend**
in cloud. A recruiter on a laptop should be able to clone, run, and
poke through every feature in 5 minutes. Compose gives that without
any cloud account.

**Why not cloud deployment:** running real AWS / Azure infrastructure
just for a portfolio demo costs money, leaks billing/ARN details
through config, and adds operational overhead disproportionate to the
audience.

**Compromise:** [`cloud-architecture.md`](cloud-architecture.md) and
[`infra/terraform/aws/`](../infra/terraform/aws/) document the
production-shaped blueprint without running it. Recruiters see the
cloud thinking; nobody pays the bill.

---

## E2E framework — Playwright

**Alternatives considered:** Cypress, Selenium.

**Why Playwright:** native multi-browser, faster than Cypress at
parallel runs, and the API request fixture (`request.newContext`)
makes seed-via-API setup natural — used in
`frontend/e2e/approval-flow.spec.ts` to plant a pending approval
before driving the UI.

**Why not Cypress:** the iframe-isolated runtime makes cross-origin
auth flakier than Playwright's in-process driver, and Cypress's
single-browser-per-spec design is a worse fit for a parallelised CI
job.

---

## Frontend state — RxJS BehaviorSubject in services, no NgRx

**Why no NgRx / Redux:** the project's state surface is small — one
user, one set of filters per page, one paginated list at a time. NgRx
would add Action / Reducer / Effect / Selector boilerplate for what
fits in a service-owned `BehaviorSubject`. The cost-benefit goes the
wrong way at this size.

**Where this might change:** if the app grew to multiple tabs of live
data with cross-tab consistency, the trade-off would flip.

---

## Charts — hand-rolled CSS / SVG, no library

**Why no chart library:** the dashboard's two charts (risk
distribution, decision distribution) are bar charts of <=6 categories.
A library buys nothing here; a `<div>` with `width: {{ percent }}%`
is enough.

**When to revisit:** if the analytics view grows to multi-series time
charts, switch to `@swimlane/ngx-charts` (Angular-native) before
Chart.js.

---

## JWT storage — `localStorage`

**Alternatives considered:** httpOnly cookies, in-memory only.

**Why `localStorage`:** simplest path to a working SPA. The frontend
reads the token on app boot, attaches it as `Authorization: Bearer ...`
via an HTTP interceptor, and clears it on 401 or sign-out.

**Tradeoff:** `localStorage` is readable by any JavaScript running on
the page, so an XSS becomes account theft. The mitigation is the same
mitigation everyone uses: ship a strict CSP and treat XSS as the
primary threat.

**When to revisit:** if the firewall ever runs on a multi-tenant
domain or hosts user-generated content, switch to httpOnly cookies +
CSRF tokens.

---

## Audit log is append-only

**Why:** the audit log is the source of truth for status transitions.
If `ActionRequest.status` ever disagrees with the audit log, the
audit log wins. No API endpoint mutates or deletes audit rows; the
backend has no `update`/`delete` method on `AuditLogRepository`.

**Operational implication:** the table grows monotonically. In a real
deployment, partition by month and ship older partitions to S3 (or
Azure Blob) on a lifecycle policy. The portfolio-local profile does
neither because volume is trivial.

---

## Phase numbering

The original spec document numbers phases 0–6; `CLAUDE.md` was later
expanded to a finer-grained 0–8. They land at the same place, just
labelled differently. The spec doc is the canonical reference for
"what was supposed to ship in phase N". See
[`memory/project_phase_numbering.md`](../../.claude/projects/.../memory/project_phase_numbering.md)
in the development memory for the running notes.
