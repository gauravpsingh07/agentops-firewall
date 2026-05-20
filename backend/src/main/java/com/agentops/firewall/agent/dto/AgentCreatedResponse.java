package com.agentops.firewall.agent.dto;

/**
 * Response body for POST /api/agents and POST /api/agents/{id}/rotate-key.
 *
 * <p>The {@code apiKey} field is the only place the raw key ever appears.
 * It is shown once and is not persisted in cleartext anywhere. Clients
 * must store it themselves; the firewall cannot recover it.
 */
public record AgentCreatedResponse(
        AgentResponse agent,
        String apiKey,
        String warning
) {
    public static AgentCreatedResponse of(AgentResponse agent, String rawKey) {
        return new AgentCreatedResponse(
                agent,
                rawKey,
                "Store this key now. It is shown exactly once and cannot be recovered.");
    }
}
