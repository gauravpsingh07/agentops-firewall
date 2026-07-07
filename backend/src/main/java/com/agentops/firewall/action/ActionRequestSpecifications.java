package com.agentops.firewall.action;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Criteria-based specifications for dynamic filtering of
 * {@link ActionRequest} queries. All filter fields map to plain columns
 * on {@code action_requests}, so the database performs the filtering,
 * ordering, and pagination — the controller no longer materializes the
 * whole table into memory.
 */
public final class ActionRequestSpecifications {

    private ActionRequestSpecifications() {
    }

    public static Specification<ActionRequest> withFilters(
            UUID agentId,
            ActionType actionType,
            ActionRequestStatus status,
            RiskLevel riskLevel) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (agentId != null) {
                predicates.add(cb.equal(root.get("agentId"), agentId));
            }
            if (actionType != null) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
