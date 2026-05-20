package com.agentops.firewall.policy;

import com.agentops.firewall.common.domain.BaseEntity;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A firewall policy. Conditions are evaluated in priority order (higher
 * wins); the first matching policy's {@link PolicyOutcome} is returned as
 * the firewall decision.
 */
@Entity
@Table(name = "policies")
public class Policy extends BaseEntity {

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Higher priority wins ties. Defaults to 100. */
    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", length = 20, nullable = false)
    private PolicyOutcome effect;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 40)
    private ActionType actionType;

    @Column(name = "resource_pattern", length = 200)
    private String resourcePattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_risk_level", length = 20)
    private RiskLevel minRiskLevel;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    public Policy() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public PolicyOutcome getEffect() { return effect; }
    public void setEffect(PolicyOutcome effect) { this.effect = effect; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public String getResourcePattern() { return resourcePattern; }
    public void setResourcePattern(String resourcePattern) { this.resourcePattern = resourcePattern; }

    public RiskLevel getMinRiskLevel() { return minRiskLevel; }
    public void setMinRiskLevel(RiskLevel minRiskLevel) { this.minRiskLevel = minRiskLevel; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
}
