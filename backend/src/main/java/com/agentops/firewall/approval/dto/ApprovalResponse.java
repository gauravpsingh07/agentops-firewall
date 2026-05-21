package com.agentops.firewall.approval.dto;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection for approval list and detail endpoints. Includes the
 * related action, agent, and policy decision context so reviewers can
 * make informed decisions without additional API calls.
 */
public record ApprovalResponse(
        UUID id,
        ApprovalStatus status,
        UUID actionRequestId,
        ActionType actionType,
        String resource,
        RiskLevel riskLevel,
        ActionRequestStatus actionStatus,
        UUID agentId,
        String agentName,
        UUID matchedPolicyId,
        String decisionReason,
        UUID reviewerUserId,
        String reviewerUsername,
        String reviewerNote,
        Instant createdAt,
        Instant decidedAt,
        Instant expiresAt
) {
}
