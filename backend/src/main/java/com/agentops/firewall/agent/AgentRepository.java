package com.agentops.firewall.agent;

import com.agentops.firewall.common.domain.enums.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByName(String name);

    boolean existsByName(String name);

    long countByStatus(AgentStatus status);

    /**
     * Stamp lastUsedAt with a targeted UPDATE rather than a read-modify-write
     * of the whole entity. This keeps the high-frequency "agent just
     * submitted an action" write off the optimistic-locking path, so
     * concurrent submissions from the same agent do not contend on the
     * version column. {@code updated_at} is bumped to keep the audit-adjacent
     * timestamp meaningful; the JPA @Version is intentionally left untouched.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Agent a SET a.lastUsedAt = :usedAt, a.updatedAt = :usedAt WHERE a.id = :id")
    int markUsed(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
