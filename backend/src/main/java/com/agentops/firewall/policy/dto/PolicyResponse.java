package com.agentops.firewall.policy.dto;

import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.policy.Policy;
import com.agentops.firewall.policy.PolicyCondition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read projection of a policy, including its conditions. */
public record PolicyResponse(
        UUID id,
        String name,
        String description,
        int priority,
        PolicyOutcome effect,
        boolean enabled,
        ActionType actionType,
        String resourcePattern,
        RiskLevel minRiskLevel,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        List<PolicyConditionDto> conditions
) {
    public static PolicyResponse fromEntity(Policy p, List<PolicyCondition> conditions) {
        List<PolicyConditionDto> dtos = conditions == null ? List.of() : conditions.stream()
                .map(c -> new PolicyConditionDto(c.getId(), c.getField(), c.getOperator(), c.getValue()))
                .toList();
        return new PolicyResponse(
                p.getId(), p.getName(), p.getDescription(), p.getPriority(), p.getEffect(),
                p.isEnabled(), p.getActionType(), p.getResourcePattern(), p.getMinRiskLevel(),
                p.getCreatedByUserId(), p.getCreatedAt(), p.getUpdatedAt(),
                dtos);
    }
}