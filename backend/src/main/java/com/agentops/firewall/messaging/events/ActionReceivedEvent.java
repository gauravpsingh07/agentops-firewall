package com.agentops.firewall.messaging.events;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted to {@code agent.actions.received} immediately after the
 * firewall persists an inbound action. Carries only non-sensitive fields:
 * no API keys, no JWTs, no raw metadata, no PII. The {@code resource}
 * string is the same low-risk identifier already stored on
 * ActionRequest.
 */
public record ActionReceivedEvent(
        String eventId,
        String eventType,
        UUID actionRequestId,
        UUID agentId,
        String agentName,
        ActionType actionType,
        String resource,
        RiskLevel riskLevel,
        ActionRequestStatus status,
        Instant timestamp
) {
    public static final String TYPE = "ACTION_RECEIVED";
}