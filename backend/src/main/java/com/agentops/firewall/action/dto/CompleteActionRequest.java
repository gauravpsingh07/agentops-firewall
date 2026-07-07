package com.agentops.firewall.action.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body accepted by {@code POST /api/agent-actions/{id}/complete}. After the
 * firewall has cleared an action (ALLOWED, or APPROVED by a reviewer) the
 * agent executes it and reports the outcome here.
 *
 * @param success {@code true} if the agent executed the action
 *                successfully (→ COMPLETED), {@code false} if it failed
 *                (→ FAILED).
 * @param detail  optional free-form note (error message, result summary).
 */
public record CompleteActionRequest(
        @NotNull(message = "success is required")
        Boolean success,

        @Size(max = 1000, message = "detail must be at most 1000 characters")
        String detail
) {
}
