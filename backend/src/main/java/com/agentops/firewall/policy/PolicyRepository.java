package com.agentops.firewall.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    /** Enabled policies ordered for evaluation: highest priority first. */
    List<Policy> findByEnabledTrueOrderByPriorityDescNameAsc();

    Optional<Policy> findByName(String name);

    boolean existsByName(String name);

    long countByEnabledTrue();
}
