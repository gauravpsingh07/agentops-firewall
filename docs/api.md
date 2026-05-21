# API reference

All endpoints live under `/api`. The backend listens on `http://localhost:8080`
in the default local profile.

---

## Authentication

Two identity models coexist:

| Caller | Mechanism | Where the credential goes |
|---|---|---|
| Human user | JWT bearer token, issued by `POST /api/auth/login` | `Authorization: Bearer <token>` |
| AI agent  | Hashed API key, issued by `POST /api/agents` (returned **once**) | `X-Agent-Key: <raw key>` |

JWTs expire after `expiresInSeconds` (see `LoginResponse`); the frontend
re-authenticates the user by calling `GET /api/auth/me` on startup and
clearing the token on any 401 outside the `/login` request.

The agent ingestion endpoint (`POST /api/agent-actions`) is the **only**
endpoint that uses the `X-Agent-Key` header. All other endpoints require a JWT.

### Roles

| Role | Can | Can't |
|---|---|---|
| `ADMIN`    | All read + all write operations | — |
| `REVIEWER` | All reads, approve/reject approvals | Agent/policy CRUD |
| `VIEWER`   | All reads, run the policy simulator | Approvals, agent/policy CRUD |

---

## Error model

Every non-2xx JSON response uses the same `ApiError` shape:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields.",
  "path": "/api/auth/login",
  "timestamp": "2026-05-21T16:00:00Z",
  "fieldErrors": [
    { "field": "username", "message": "username is required" }
  ]
}
```

`fieldErrors` is only present on 400-validation failures.

Common status codes used:

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 400 | Validation failed |
| 401 | Missing or invalid credentials |
| 403 | Authenticated but not authorised for this operation |
| 404 | Resource not found |
| 409 | Conflict (e.g. an approval that has already been decided) |
| 500 | Unexpected server error |

---

## Auth

### `POST /api/auth/login`

Exchange username + password for a JWT.

```bash
curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

Response `200 OK`:

```json
{
  "token": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": {
    "id": "1e1c...",
    "username": "admin",
    "role": "ADMIN"
  }
}
```

### `GET /api/auth/me`

Returns the principal resolved from the current bearer token.

```bash
curl -sS http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## Agents

| Endpoint | Method | Roles |
|---|---|---|
| `/api/agents` | `GET`   | ADMIN, REVIEWER, VIEWER |
| `/api/agents` | `POST`  | ADMIN |
| `/api/agents/{id}` | `GET`   | ADMIN, REVIEWER, VIEWER |
| `/api/agents/{id}` | `PATCH` | ADMIN |
| `/api/agents/{id}/rotate-key` | `POST` | ADMIN |

### `POST /api/agents`

```bash
curl -sS -X POST http://localhost:8080/api/agents \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"agent-email-bot-01","description":"sends emails"}'
```

Response `201 Created`:

```json
{
  "agent": {
    "id": "a8c2...",
    "name": "agent-email-bot-01",
    "status": "ACTIVE",
    "ownerUserId": "1e1c..."
  },
  "apiKey": "agk_<raw key shown exactly once>",
  "warning": "Store this key now; it is not retrievable later."
}
```

The raw key is **never** returned again. Use `POST /api/agents/{id}/rotate-key`
to mint a new one.

---

## Agent actions

### `POST /api/agent-actions`

The agent ingestion endpoint. Authenticated by `X-Agent-Key`, not JWT.
The decision is synchronous; downstream Kafka events fan the same decision
out for observability.

```bash
curl -sS -X POST http://localhost:8080/api/agent-actions \
  -H "X-Agent-Key: $AGENT_KEY" \
  -H 'Content-Type: application/json' \
  -d '{
    "agentId": "agent-email-bot-01",
    "actionType": "SEND_EMAIL",
    "resource": "external_email",
    "riskLevel": "MEDIUM",
    "metadata": {
      "recipientDomain": "external.com",
      "containsAttachment": true
    }
  }'
```

Response `201 Created`:

```json
{
  "actionId": "f30c...",
  "decision": "NEEDS_APPROVAL",
  "status": "PENDING_APPROVAL",
  "matchedPolicyId": "...",
  "matchedPolicyName": "Require approval for external email with attachment",
  "reason": "external recipient + attachment",
  "approvalId": "..."
}
```

`approvalId` is non-null only when `decision == NEEDS_APPROVAL`.

### `GET /api/agent-actions`

Paginated list. Filters: `agentId`, `actionType`, `status`, `riskLevel`,
`page`, `size`. Default sort: `createdAt DESC`.

### `GET /api/agent-actions/{id}`

Single action by id.

---

## Policies

| Endpoint | Method | Roles |
|---|---|---|
| `/api/policies` | `GET` | ADMIN, REVIEWER, VIEWER |
| `/api/policies` | `POST` | ADMIN |
| `/api/policies/{id}` | `GET` | ADMIN, REVIEWER, VIEWER |
| `/api/policies/{id}` | `PATCH` | ADMIN |
| `/api/policies/{id}` | `DELETE` | ADMIN (soft-disable) |
| `/api/policies/simulate` | `POST` | ADMIN, REVIEWER, VIEWER |

### `POST /api/policies`

```bash
curl -sS -X POST http://localhost:8080/api/policies \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Block external deletes",
    "description": "Reject DELETE_FILE on external storage",
    "effect": "DENY",
    "priority": 90,
    "actionType": "DELETE_FILE",
    "resourcePattern": "s3://prod-*",
    "minRiskLevel": "MEDIUM",
    "conditions": [
      { "field": "metadata.region", "operator": "NOT_IN", "value": "us-east-1,us-west-2" }
    ]
  }'
```

Condition operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `EXISTS`,
`NOT_EXISTS`, `CONTAINS`, `NOT_CONTAINS`, `MATCHES` (regex), `GTE`, `LTE`.

### `POST /api/policies/simulate`

Runs a hypothetical action against the live policy set without
persisting an `ActionRequest`, writing audit, or publishing Kafka events.

```bash
curl -sS -X POST http://localhost:8080/api/policies/simulate \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "actionType": "DELETE_FILE",
    "resource": "s3://prod-customer/data",
    "riskLevel": "HIGH",
    "metadata": {"region": "eu-west-1"}
  }'
```

Response:

```json
{
  "decision": "DENY",
  "matchedPolicyId": "...",
  "matchedPolicyName": "Block external deletes",
  "reason": "metadata.region NOT_IN [us-east-1, us-west-2]",
  "simulated": true
}
```

---

## Approvals

| Endpoint | Method | Roles |
|---|---|---|
| `/api/approvals` | `GET` | ADMIN, REVIEWER, VIEWER |
| `/api/approvals/{id}` | `GET` | ADMIN, REVIEWER, VIEWER |
| `/api/approvals/{id}/approve` | `POST` | ADMIN, REVIEWER |
| `/api/approvals/{id}/reject` | `POST` | ADMIN, REVIEWER |

Filters on `GET /api/approvals`: `status` (`PENDING`/`APPROVED`/`REJECTED`/`EXPIRED`),
`agentId`, `actionType`, `riskLevel`, `from`, `to`, `page`, `size`.

### `POST /api/approvals/{id}/approve`

Body is optional; only the reviewer note is accepted.

```bash
curl -sS -X POST "http://localhost:8080/api/approvals/$APPROVAL_ID/approve" \
  -H "Authorization: Bearer $REVIEWER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"note":"Customer-approved one-off, see ticket #1234."}'
```

Returns `409 Conflict` if the approval has already been decided.

---

## Audit log

### `GET /api/audit-logs`

Append-only. Filters: `eventType`, `actorType`, `actorId`, `subjectType`,
`subjectId`, `from` (ISO-8601), `to` (ISO-8601), `page`, `size`, `sort`, `direction`.

```bash
curl -sS "http://localhost:8080/api/audit-logs?eventType=APPROVAL_DECIDED&from=2026-05-01T00:00:00Z" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Dashboard

All five endpoints accept any authenticated role.

| Endpoint | Returns |
|---|---|
| `GET /api/dashboard/summary` | aggregated counts (`totalActions`, `pendingApprovals`, `allowedActions`, `deniedActions`, …) |
| `GET /api/dashboard/recent-actions` | most recent `ActionRequest` rows |
| `GET /api/dashboard/risk-distribution` | one row per `RiskLevel` with a count |
| `GET /api/dashboard/decision-distribution` | one row per `ActionRequestStatus` with a count |
| `GET /api/dashboard/recent-audit-events` | most recent audit-log entries |

---

## Conventions

- All timestamps are ISO-8601 UTC.
- All IDs are version-4 UUIDs.
- Pagination follows Spring's `Page` envelope (`content`, `totalElements`,
  `totalPages`, `size`, `number`, `first`, `last`).
- Validation errors return 400 with `fieldErrors`; auth failures return
  401; role denials return 403.
