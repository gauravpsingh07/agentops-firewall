package com.agentops.firewall.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload accepted by POST /api/agents. The agent's API key is generated
 * server-side and returned exactly once in the response.
 */
public record CreateAgentRequest(
        @NotBlank(message = "name is required")
        @Size(min = 3, max = 120, message = "name must be between 3 and 120 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "name may only contain letters, digits, dots, underscores, and hyphens")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description
) {
}
