package com.agentops.firewall.agent;

import com.agentops.firewall.common.domain.BaseEntity;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Registered AI agent. Authenticates to the firewall using an API key whose
 * BCrypt hash is stored in {@code apiKeyHash}; the raw key is shown to the
 * operator only once at creation / rotation time.
 */
@Entity
@Table(
    name = "agents",
    uniqueConstraints = @UniqueConstraint(name = "uk_agents_name", columnNames = "name")
)
public class Agent extends BaseEntity {

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "api_key_hash", length = 255, nullable = false)
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AgentStatus status = AgentStatus.ACTIVE;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public Agent() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }

    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
