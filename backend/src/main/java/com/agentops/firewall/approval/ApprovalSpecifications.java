package com.agentops.firewall.approval;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Criteria-based specifications for dynamic filtering of
 * {@link ApprovalRequest} queries. Joins to {@link ActionRequest} are
 * performed lazily so filters on action fields work correctly.
 */
public final class ApprovalSpecifications {

    private ApprovalSpecifications() {
    }

    public static Specification<ApprovalRequest> withFilters(
            ApprovalStatus status,
            UUID agentId,
            ActionType actionType,
            RiskLevel riskLevel,
            Instant from,
            Instant to) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            // Filters that require a join to ActionRequest.
            if (agentId != null || actionType != null || riskLevel != null) {
                Join<ApprovalRequest, ActionRequest> actionJoin =
                        root.join("actionRequestId", JoinType.INNER);
                // We cannot use a relationship join because ApprovalRequest
                // stores actionRequestId as a plain UUID, not a @ManyToOne.
                // Instead, we use a subquery / cross-entity filter via an
                // explicit correlated subquery.
            }

            // For agentId, actionType, riskLevel we need to query
            // against action_requests. Since ApprovalRequest uses a
            // plain UUID column rather than a @ManyToOne, we use a
            // subquery approach.
            if (agentId != null) {
                var subquery = query.subquery(UUID.class);
                var actionRoot = subquery.from(ActionRequest.class);
                subquery.select(actionRoot.get("id"))
                        .where(cb.equal(actionRoot.get("agentId"), agentId));
                predicates.add(root.get("actionRequestId").in(subquery));
            }
            if (actionType != null) {
                var subquery = query.subquery(UUID.class);
                var actionRoot = subquery.from(ActionRequest.class);
                subquery.select(actionRoot.get("id"))
                        .where(cb.equal(actionRoot.get("actionType"), actionType));
                predicates.add(root.get("actionRequestId").in(subquery));
            }
            if (riskLevel != null) {
                var subquery = query.subquery(UUID.class);
                var actionRoot = subquery.from(ActionRequest.class);
                subquery.select(actionRoot.get("id"))
                        .where(cb.equal(actionRoot.get("riskLevel"), riskLevel));
                predicates.add(root.get("actionRequestId").in(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
