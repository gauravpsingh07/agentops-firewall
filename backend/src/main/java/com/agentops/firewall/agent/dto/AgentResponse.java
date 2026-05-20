package com.agentops.firewall.agent.dto;

import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.common.domain.enums.AgentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of an agent. Never includes the raw key or the
 * BCrypt hash.
 */
public record AgentResponse(
        UUID id,
        String name,
        String description,
        AgentStatus status,
        UUID ownerUserId,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AgentResponse fromEntity(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getStatus(),
                agent.getOwnerUserId(),
                agent.getLastUsedAt(),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}
