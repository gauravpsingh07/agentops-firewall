package com.agentops.firewall.policy.dto;

import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Hypothetical action submitted to POST /api/policies/simulate.
 *
 * <p>The simulator never persists any of the entities the live ingestion
 * pipeline creates (ActionRequest, PolicyDecision, ApprovalRequest); it
 * only runs the evaluator against the supplied snapshot and returns the
 * outcome.
 */
public record PolicySimulationRequest(
        @NotNull(message = "actionType is required")
        ActionType actionType,

        @Size(max = 255, message = "resource must be at most 255 characters")
        String resource,

        @NotNull(message = "riskLevel is required")
        RiskLevel riskLevel,

        Map<String, Object> metadata,

        @Size(max = 120, message = "agentName must be at most 120 characters")
        String agentName
) {
}