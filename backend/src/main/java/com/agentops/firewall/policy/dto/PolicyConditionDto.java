package com.agentops.firewall.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Wire representation of a {@link com.agentops.firewall.policy.PolicyCondition}.
 * {@code id} is present on read responses and ignored on create/update.
 */
public record PolicyConditionDto(
        UUID id,

        @NotBlank(message = "field is required")
        @Size(max = 120, message = "field must be at most 120 characters")
        String field,

        @NotBlank(message = "operator is required")
        @Size(max = 20, message = "operator must be at most 20 characters")
        String operator,

        @NotBlank(message = "value is required")
        @Size(max = 4000, message = "value must be at most 4000 characters")
        String value
) {
}