package com.agentops.firewall.agent;

import com.agentops.firewall.common.domain.enums.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByName(String name);

    boolean existsByName(String name);

    long countByStatus(AgentStatus status);
}
