package com.agentops.firewall.approval;

import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID>,
        JpaSpecificationExecutor<ApprovalRequest> {

    Optional<ApprovalRequest> findByActionRequestId(UUID actionRequestId);

    long countByStatus(ApprovalStatus status);
}
