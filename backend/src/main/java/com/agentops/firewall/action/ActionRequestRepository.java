package com.agentops.firewall.action;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActionRequestRepository
        extends JpaRepository<ActionRequest, UUID>, JpaSpecificationExecutor<ActionRequest> {

    long countByStatus(ActionRequestStatus status);

    @Query("SELECT a.riskLevel, COUNT(a) FROM ActionRequest a GROUP BY a.riskLevel")
    List<Object[]> countGroupedByRiskLevel();

    @Query("SELECT a.status, COUNT(a) FROM ActionRequest a GROUP BY a.status")
    List<Object[]> countGroupedByStatus();

    List<ActionRequest> findTop20ByOrderByCreatedAtDesc();
}
