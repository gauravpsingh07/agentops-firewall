package com.agentops.firewall.agent;

import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.error.AgentAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Validates an inbound agent key/name pair. Uniform failure mode keeps
 * the call surface from leaking whether the agent exists, the key is
 * wrong, or the agent is disabled.
 */
@Service
public class AgentAuthenticationService {

    private final AgentRepository agentRepository;
    private final AgentKeyService agentKeyService;

    public AgentAuthenticationService(AgentRepository agentRepository,
                                       AgentKeyService agentKeyService) {
        this.agentRepository = agentRepository;
        this.agentKeyService = agentKeyService;
    }

    /**
     * Verifies the supplied raw key against the named agent and returns
     * the matching ACTIVE agent. Throws {@link AgentAuthenticationException}
     * for any failure (missing key, missing agent, wrong key, non-ACTIVE
     * status).
     */
    @Transactional(readOnly = true)
    public Agent authenticate(String agentName, String rawKey) {
        if (rawKey == null || rawKey.isBlank() || agentName == null || agentName.isBlank()) {
            throw new AgentAuthenticationException("Missing X-Agent-Key header or agent identifier.");
        }
        Agent agent = agentRepository.findByName(agentName)
                .orElseThrow(() -> new AgentAuthenticationException("No matching agent."));
        return verify(agent, rawKey);
    }

    /**
     * Verify the supplied key against a known agent id. Used by the action
     * completion callback, where the caller is identified by the action it
     * is completing rather than by an agent name in the body. Only the
     * action's own agent (holding the right key) can authenticate.
     */
    @Transactional(readOnly = true)
    public Agent authenticate(UUID agentId, String rawKey) {
        if (rawKey == null || rawKey.isBlank() || agentId == null) {
            throw new AgentAuthenticationException("Missing X-Agent-Key header or agent identifier.");
        }
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new AgentAuthenticationException("No matching agent."));
        return verify(agent, rawKey);
    }

    private Agent verify(Agent agent, String rawKey) {
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new AgentAuthenticationException("Agent is not in ACTIVE status.");
        }
        if (!agentKeyService.matches(rawKey, agent.getApiKeyHash())) {
            throw new AgentAuthenticationException("API key does not match.");
        }
        return agent;
    }
}