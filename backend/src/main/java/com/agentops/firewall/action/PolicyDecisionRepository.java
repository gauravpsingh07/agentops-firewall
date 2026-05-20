package com.agentops.firewall.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyDecisionRepository extends JpaRepository<PolicyDecision, UUID> {

    List<PolicyDecision> findByActionRequestIdOrderByEvaluatedAtAsc(UUID actionRequestId);
}
