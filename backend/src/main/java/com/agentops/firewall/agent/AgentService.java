package com.agentops.firewall.agent;

import com.agentops.firewall.agent.dto.AgentCreatedResponse;
import com.agentops.firewall.agent.dto.AgentResponse;
import com.agentops.firewall.agent.dto.CreateAgentRequest;
import com.agentops.firewall.agent.dto.UpdateAgentRequest;
import com.agentops.firewall.audit.AuditService;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.error.ConflictException;
import com.agentops.firewall.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service-layer operations for the agent registry. Encapsulates raw-key
 * generation, BCrypt hashing, and audit-trail emission so controllers
 * stay thin.
 */
@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentKeyService agentKeyService;
    private final AuditService auditService;

    public AgentService(AgentRepository agentRepository,
                        AgentKeyService agentKeyService,
                        AuditService auditService) {
        this.agentRepository = agentRepository;
        this.agentKeyService = agentKeyService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list() {
        return agentRepository.findAll().stream()
                .map(AgentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentResponse getById(UUID id) {
        return AgentResponse.fromEntity(loadById(id));
    }

    @Transactional(readOnly = true)
    public Optional<Agent> findActiveByName(String name) {
        return agentRepository.findByName(name)
                .filter(a -> a.getStatus() == AgentStatus.ACTIVE);
    }

    @Transactional
    public AgentCreatedResponse create(CreateAgentRequest request, UUID createdByUserId) {
        if (agentRepository.existsByName(request.name())) {
            throw new ConflictException("Agent with name " + request.name() + " already exists.");
        }
        String rawKey = agentKeyService.generateRawKey();
        Agent agent = new Agent();
        agent.setName(request.name());
        agent.setDescription(request.description());
        agent.setApiKeyHash(agentKeyService.hash(rawKey));
        agent.setStatus(AgentStatus.ACTIVE);
        agent.setOwnerUserId(createdByUserId);
        Agent saved = agentRepository.save(agent);

        auditService.record(
                AuditService.EVENT_AGENT_CREATED,
                AuditService.ACTOR_USER, createdByUserId,
                AuditService.SUBJECT_AGENT, saved.getId(),
                "Agent " + saved.getName() + " created.",
                AuditService.details(
                        "agentName", saved.getName(),
                        "status", saved.getStatus().name()
                )
        );
        return AgentCreatedResponse.of(AgentResponse.fromEntity(saved), rawKey);
    }

    @Transactional
    public AgentResponse update(UUID id, UpdateAgentRequest request, UUID byUserId) {
        Agent agent = loadById(id);
        boolean changed = false;
        if (request.description() != null && !request.description().equals(agent.getDescription())) {
            agent.setDescription(request.description());
            changed = true;
        }
        if (request.status() != null && request.status() != agent.getStatus()) {
            agent.setStatus(request.status());
            changed = true;
        }
        if (changed) {
            auditService.record(
                    AuditService.EVENT_AGENT_UPDATED,
                    AuditService.ACTOR_USER, byUserId,
                    AuditService.SUBJECT_AGENT, agent.getId(),
                    "Agent " + agent.getName() + " updated.",
                    AuditService.details(
                            "status", agent.getStatus().name()
                    )
            );
        }
        return AgentResponse.fromEntity(agent);
    }

    @Transactional
    public AgentCreatedResponse rotateKey(UUID id, UUID byUserId) {
        Agent agent = loadById(id);
        if (agent.getStatus() == AgentStatus.DELETED) {
            throw new ConflictException("Cannot rotate the key of a deleted agent.");
        }
        String newRawKey = agentKeyService.generateRawKey();
        agent.setApiKeyHash(agentKeyService.hash(newRawKey));

        auditService.record(
                AuditService.EVENT_AGENT_KEY_ROTATED,
                AuditService.ACTOR_USER, byUserId,
                AuditService.SUBJECT_AGENT, agent.getId(),
                "Agent " + agent.getName() + " API key rotated.",
                AuditService.details(
                        "agentName", agent.getName()
                        // Intentionally NO raw key and NO hash in audit details.
                )
        );
        return AgentCreatedResponse.of(AgentResponse.fromEntity(agent), newRawKey);
    }

    /**
     * Update lastUsedAt after a successful action submission. Uses a
     * targeted UPDATE rather than a read-modify-write so the high-frequency
     * "agent just submitted an action" write does not contend on the
     * optimistic-lock version column under concurrent submissions.
     */
    @Transactional
    public void markUsed(UUID agentId) {
        agentRepository.markUsed(agentId, Instant.now());
    }

    private Agent loadById(UUID id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Agent not found: " + id));
    }
}
