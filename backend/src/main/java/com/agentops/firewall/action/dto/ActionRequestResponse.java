package com.agentops.firewall.action.dto;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/** Read projection for GET /api/agent-actions and /{id}. */
public record ActionRequestResponse(
        UUID id,
        UUID agentId,
        ActionType actionType,
        String resource,
        RiskLevel riskLevel,
        String metadataJson,
        ActionRequestStatus status,
        String decisionReason,
        UUID matchedPolicyId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ActionRequestResponse fromEntity(ActionRequest a) {
        return new ActionRequestResponse(
                a.getId(), a.getAgentId(), a.getActionType(), a.getResource(),
                a.getRiskLevel(), a.getMetadataJson(), a.getStatus(), a.getDecisionReason(),
                a.getMatchedPolicyId(), a.getCreatedAt(), a.getUpdatedAt());
    }
}