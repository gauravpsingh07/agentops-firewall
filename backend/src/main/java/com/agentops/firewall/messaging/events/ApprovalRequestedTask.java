package com.agentops.firewall.messaging.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured RabbitMQ message published to the {@code approval.requests}
 * queue when a policy evaluator returns NEEDS_APPROVAL. Carries safe-only
 * fields — no API keys, JWTs, passwords, or raw metadata.
 */
public record ApprovalRequestedTask(
        String taskId,
        UUID approvalRequestId,
        UUID actionRequestId,
        UUID agentId,
        String agentName,
        String actionType,
        String riskLevel,
        String resource,
        String decisionReason,
        Instant requestedAt,
        Instant expiresAt
) {
    public static final String TYPE = "APPROVAL_REQUESTED";
}
