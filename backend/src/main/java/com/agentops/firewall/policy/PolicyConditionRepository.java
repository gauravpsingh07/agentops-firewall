package com.agentops.firewall.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyConditionRepository extends JpaRepository<PolicyCondition, UUID> {

    List<PolicyCondition> findByPolicyId(UUID policyId);

    /**
     * Batch-load conditions for a set of policies in a single query. Used
     * by the evaluator to avoid an N+1 query per enabled policy on the
     * action-ingestion hot path.
     */
    List<PolicyCondition> findByPolicyIdIn(Collection<UUID> policyIds);
}
