package com.agentops.firewall.agent.dto;

import com.agentops.firewall.common.domain.enums.AgentStatus;
import jakarta.validation.constraints.Size;

/**
 * PATCH payload for an agent. Any field may be omitted; only present
 * fields are applied.
 */
public record UpdateAgentRequest(
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,
        AgentStatus status
) {
}
