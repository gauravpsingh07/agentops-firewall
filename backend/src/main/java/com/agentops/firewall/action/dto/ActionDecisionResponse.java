package com.agentops.firewall.action.dto;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;

import java.util.UUID;

/**
 * Synchronous response returned to the AI agent. The decision is
 * authoritative; downstream Kafka events fan the same decision out for
 * observability but are not the contract surface for the caller.
 *
 * <p>{@code approvalId} is non-null only when the policy outcome is
 * {@code NEEDS_APPROVAL}; for {@code ALLOW} and {@code DENY} outcomes
 * it is always {@code null}.
 */
public record ActionDecisionResponse(
        UUID actionId,
        PolicyOutcome decision,
        ActionRequestStatus status,
        UUID matchedPolicyId,
        String matchedPolicyName,
        String reason,
        UUID approvalId
) {
}