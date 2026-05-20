package com.agentops.firewall.action;

import com.agentops.firewall.common.domain.BaseEntity;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The recorded outcome of evaluating policies against an {@link ActionRequest}.
 * Immutable once written — one row per evaluation. Stored separately from
 * {@code ActionRequest} so that the historical decision trail survives
 * future status transitions (e.g. NEEDS_APPROVAL → APPROVED).
 */
@Entity
@Table(name = "policy_decisions")
public class PolicyDecision extends BaseEntity {

    @Column(name = "action_request_id", nullable = false)
    private UUID actionRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20, nullable = false)
    private PolicyOutcome decision;

    @Column(name = "matched_policy_id")
    private UUID matchedPolicyId;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "evaluation_details_json", columnDefinition = "text")
    private String evaluationDetailsJson;

    public PolicyDecision() {
    }

    public UUID getActionRequestId() { return actionRequestId; }
    public void setActionRequestId(UUID actionRequestId) { this.actionRequestId = actionRequestId; }

    public PolicyOutcome getDecision() { return decision; }
    public void setDecision(PolicyOutcome decision) { this.decision = decision; }

    public UUID getMatchedPolicyId() { return matchedPolicyId; }
    public void setMatchedPolicyId(UUID matchedPolicyId) { this.matchedPolicyId = matchedPolicyId; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public String getEvaluationDetailsJson() { return evaluationDetailsJson; }
    public void setEvaluationDetailsJson(String evaluationDetailsJson) { this.evaluationDetailsJson = evaluationDetailsJson; }
}
