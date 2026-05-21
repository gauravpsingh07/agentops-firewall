package com.agentops.firewall.messaging.events;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted to {@code agent.actions.completed} after a reviewer approves
 * or rejects an action that was pending approval. Carries safe-only
 * fields — no API keys, JWTs, passwords, or raw metadata.
 */
public record ActionCompletedEvent(
        String eventId,
        String eventType,
        UUID actionRequestId,
        UUID approvalRequestId,
        UUID agentId,
        ActionType actionType,
        RiskLevel riskLevel,
        ActionRequestStatus finalStatus,
        UUID reviewedByUserId,
        Instant timestamp
) {
    public static final String TYPE = "ACTION_COMPLETED";
}
