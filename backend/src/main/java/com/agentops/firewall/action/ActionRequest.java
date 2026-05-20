package com.agentops.firewall.action;

import com.agentops.firewall.common.domain.BaseEntity;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A proposed action submitted by an AI agent. Status transitions are
 * append-only via the audit log; the snapshot here always reflects the
 * latest known state.
 *
 * <p>{@code metadataJson} stores the free-form {@code metadata} object from
 * the original request as a JSON string. It is stored as TEXT for portable
 * persistence across H2 (tests) and PostgreSQL (runtime); a future
 * migration can convert the column to {@code jsonb} once the policy engine
 * needs to query into it.
 */
@Entity
@Table(name = "action_requests")
public class ActionRequest extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 40, nullable = false)
    private ActionType actionType;

    @Column(name = "resource", length = 255)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20, nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ActionRequestStatus status = ActionRequestStatus.RECEIVED;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "matched_policy_id")
    private UUID matchedPolicyId;

    public ActionRequest() {
    }

    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID agentId) { this.agentId = agentId; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public ActionRequestStatus getStatus() { return status; }
    public void setStatus(ActionRequestStatus status) { this.status = status; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public UUID getMatchedPolicyId() { return matchedPolicyId; }
    public void setMatchedPolicyId(UUID matchedPolicyId) { this.matchedPolicyId = matchedPolicyId; }
}
