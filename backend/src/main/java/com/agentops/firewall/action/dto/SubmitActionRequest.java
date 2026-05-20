package com.agentops.firewall.action.dto;

import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body accepted by POST /api/agent-actions. {@code agentId} is the
 * registered agent's name; the firewall identifies the caller by the
 * combination of this field and the value in the {@code X-Agent-Key}
 * header.
 */
public record SubmitActionRequest(
        @NotBlank(message = "agentId is required")
        @Size(max = 120, message = "agentId must be at most 120 characters")
        String agentId,

        @NotNull(message = "actionType is required")
        ActionType actionType,

        @Size(max = 255, message = "resource must be at most 255 characters")
        String resource,

        @NotNull(message = "riskLevel is required")
        RiskLevel riskLevel,

        Map<String, Object> metadata
) {
}