package com.agentops.firewall.messaging.events;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted to {@code agent.actions.decided} after the policy evaluator
 * has produced a decision and the action has been transitioned to its
 * post-evaluation status (ALLOWED / DENIED / PENDING_APPROVAL). Carries
 * the same safe-only field set as ActionReceivedEvent plus the decision
 * outcome and matched-policy reference.
 */
public record ActionDecidedEvent(
        String eventId,
        String eventType,
        UUID actionRequestId,
        UUID agentId,
        ActionType actionType,
        RiskLevel riskLevel,
        PolicyOutcome decision,
        ActionRequestStatus status,
        UUID matchedPolicyId,
        String matchedPolicyName,
        String reason,
        Instant timestamp
) {
    public static final String TYPE = "ACTION_DECIDED";
}