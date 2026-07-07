# Architecture

AgentOps Firewall sits between AI agents and the resources they act on.
Agents propose actions; the firewall evaluates them against policy,
optionally routes them through a human reviewer, and writes an append-only
audit trail of every decision.

---

## Components

| Component | Role |
|---|---|
| Angular dashboard | Operator UI: actions feed, policies, simulator, approvals, audit, analytics. |
| Spring Boot backend | REST API, policy evaluator, approval workflow, audit writer, JWT + agent-key auth. |
| PostgreSQL 16 | System of record: users, agents, policies, actions, decisions, approvals, audit log. |
| Kafka (KRaft) | Append-only event stream of agent activity. Three topics: `agent.actions.received`, `agent.actions.decided`, `agent.actions.completed`. |
| RabbitMQ | Durable work queue for human-in-the-loop approval tasks. Queue: `approval.requests`. |

Kafka and RabbitMQ have **distinct roles** — see [`decisions.md`](decisions.md).

---

## Request flow — `POST /api/agent-actions`

```
agent ──► POST /api/agent-actions                       (X-Agent-Key auth)
            │
            ├── AgentAuthenticationService              (BCrypt-verifies the key, loads agent)
            │
            ├── ActionIngestionService                  (persists the ActionRequest as RECEIVED)
            │
            ├── KafkaAgentActionEventPublisher          ──► Kafka: agent.actions.received
            │
            ├── PolicyEvaluator                         (priority-sorted; first match wins)
            │      └── ConditionEvaluator               (per-condition: EQUALS, IN, MATCHES, ...)
            │
            ├── ActionRequest status update              (ALLOWED | DENIED | PENDING_APPROVAL)
            │
            ├── PolicyDecision row                      (which policy fired, why)
            │
            ├── KafkaAgentActionEventPublisher          ──► Kafka: agent.actions.decided
            │
            ├── if NEEDS_APPROVAL:
            │     ├── ApprovalRequest row (PENDING)
            │     └── RabbitApprovalTaskPublisher       ──► RabbitMQ: approval.requests
            │
            ├── AuditService.recordDecision             (append-only)
            │
            ▼
       201 Created  { decision, status, matchedPolicy, reason, approvalId? }
```

The synchronous response is the decision authority. Kafka events fan the
same decision out to any downstream consumers without changing the API
contract.

---

## Policy evaluation

Policies are sorted by descending `priority`; the first one whose coarse
filters and conditions match the action wins. Coarse filters are:

- `actionType` (optional) — exact match
- `resourcePattern` (optional) — glob-like prefix match
- `minRiskLevel` (optional) — action's risk must be ≥ this

After the coarse filters, every condition in the policy must evaluate
to `true` for the policy to match. Conditions are evaluated against a
flat dotted key path through the action's `metadata` map and the
top-level fields (`actionType`, `resource`, `riskLevel`, `agentName`).

If no policy matches, the firewall's **default behaviour** is to mark
the action as `RECEIVED` and let it through — this is intentional for
the demo so a fresh install isn't dead-on-arrival, but a production
deployment should add a catch-all `DENY` at priority 0.

---

## Approval workflow

```
backend ──► NEEDS_APPROVAL
              ├── ApprovalRequest (PENDING)               PostgreSQL
              └── ApprovalRequestedTask                    RabbitMQ: approval.requests
                       │
                       ▼
              reviewer dashboard (Angular)
                       │
              POST /api/approvals/{id}/approve  (REVIEWER/ADMIN)
                       │
                       ▼
              ApprovalService
                ├── status check (409 if already decided)
                ├── ApprovalRequest update (APPROVED|REJECTED)
                ├── ActionRequest update (APPROVED|REJECTED)
                ├── AuditService.recordDecision
                └── ActionCompletedNotification ──► (after commit) Kafka + RabbitMQ
```

Unattended approvals do not linger: a `@Scheduled` `ApprovalExpiryService`
moves any `PENDING` request past its `expiresAt` to `EXPIRED`, fails the
action closed (`DENIED`), and writes an `APPROVAL_EXPIRED` audit event. Once
an action is `ALLOWED` or `APPROVED`, the agent reports execution via
`POST /api/agent-actions/{id}/complete`, moving it to `COMPLETED`/`FAILED`.

---

## Messaging, consumers & the live stream

Publishing is **transaction-safe**: services emit internal domain events and
a `TransactionalMessagingForwarder` relays them to Kafka/RabbitMQ under
`@TransactionalEventListener(AFTER_COMMIT)`, so a rollback never emits a
phantom broker event.

The broker side is no longer write-only. A `@KafkaListener` (action stream)
and a `@RabbitListener` (approval queue) project events into an in-memory
`LiveMetricsService`. `GET /api/stream` exposes that projection as
Server-Sent Events — an initial `snapshot` then per-event `activity` frames —
which the Angular approval inbox consumes to update live.

```
Kafka topics ──► AgentActionEventConsumer ─┐
                                           ├─► LiveMetricsService ──► GET /api/stream (SSE) ──► dashboard
RabbitMQ queue ► ApprovalTaskConsumer ─────┘
```

Consumers are gated by `agentops.messaging.consumers.enabled` (off in tests).
Login and agent ingestion are throttled by a fixed-window `RateLimitFilter`
ahead of the security chain (`429` + `Retry-After`).

---

## Audit log

Every meaningful state transition writes an `AuditLog` row:

- Action received / decided / completed
- Approval requested / approved / rejected / expired
- Policy created / updated / disabled
- Agent created / key rotated / disabled
- Login success / failure

The table is **append-only**: no API endpoint mutates or deletes rows.
The audit log is the source of truth for status transitions; if the
status column on `ActionRequest` ever disagrees with the audit log,
trust the audit log.

---

## Database

PostgreSQL 16 with Flyway-managed migrations. Eight entities:

```
User                 1 ─── *  Agent
Agent                1 ─── *  ActionRequest
ActionRequest        1 ─── 1  PolicyDecision
ActionRequest        0 ─── 1  ApprovalRequest
Policy               1 ─── *  PolicyCondition
Policy               1 ─── *  PolicyDecision
AuditLog                       (no FKs; references by actorId/subjectId UUIDs)
```

Schema lives under `backend/src/main/resources/db/migration/`.

---

## Layering

| Layer | Package suffix | Talks to |
|---|---|---|
| Controllers | `*.controller`, `*.Controller` | Services |
| Services | `*Service` | Repositories, publishers |
| Repositories | `*Repository` | JPA / Spring Data |
| Publishers | `messaging.*Publisher` | Kafka / RabbitMQ |
| DTOs | `*.dto.*` | Wire format only |
| Domain | `*.domain.*`, entity classes | JPA-managed |

Controllers never touch repositories or publishers directly; services
own that orchestration.
