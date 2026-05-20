# Architecture (placeholder)

> This document will be fleshed out in Phase 6 (`docs: add initial architecture plan`).

Planned sections:

- Components and their responsibilities
- Request flow for `POST /api/agent-actions`
- Policy evaluation flow (priority resolution, default behavior)
- Approval workflow (NEEDS_APPROVAL → RabbitMQ → reviewer → status update)
- Kafka topic usage (`agent.actions.received`, `agent.actions.decided`, `agent.actions.completed`)
- RabbitMQ queue usage (`approval.requests`, optional `approval.notifications`)
- Database summary
