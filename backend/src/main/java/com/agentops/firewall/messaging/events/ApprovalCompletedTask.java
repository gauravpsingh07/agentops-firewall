package com.agentops.firewall.messaging.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured RabbitMQ message published to the
 * {@code approval.notifications} queue after a reviewer approves or
 * rejects a request. Carries safe-only fields.
 */
public record ApprovalCompletedTask(
        String taskId,
        UUID approvalRequestId,
        UUID actionRequestId,
        UUID agentId,
        String actionType,
        String finalStatus,
        UUID reviewedByUserId,
        Instant completedAt
) {
    public static final String TYPE = "APPROVAL_COMPLETED";
}
