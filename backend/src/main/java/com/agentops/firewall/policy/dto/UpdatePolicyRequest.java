package com.agentops.firewall.policy.dto;

import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PATCH payload for a policy. Any field may be omitted; only present
 * fields are applied. If {@code conditions} is non-null, the entire
 * condition set is replaced by the provided list.
 */
public record UpdatePolicyRequest(
        @Size(min = 3, max = 200, message = "name must be between 3 and 200 characters")
        String name,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        PolicyOutcome effect,

        @Min(value = 0, message = "priority must be >= 0")
        @Max(value = 1000, message = "priority must be <= 1000")
        Integer priority,

        Boolean enabled,

        ActionType actionType,

        @Size(max = 200, message = "resourcePattern must be at most 200 characters")
        String resourcePattern,

        RiskLevel minRiskLevel,

        @Valid
        List<PolicyConditionDto> conditions
) {
}