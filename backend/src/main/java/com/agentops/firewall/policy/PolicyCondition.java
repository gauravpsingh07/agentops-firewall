package com.agentops.firewall.policy;

import com.agentops.firewall.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single condition belonging to a {@link Policy}. Conditions within the
 * same policy are combined with logical AND during evaluation. The
 * evaluation engine (added in Phase 4) interprets {@code field},
 * {@code operator}, and {@code value} against the incoming action request.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code field=metadata.recipientDomain, operator=NOT_IN, value=["acme.com","internal"]}</li>
 *   <li>{@code field=riskLevel, operator=GTE, value=HIGH}</li>
 * </ul>
 */
@Entity
@Table(name = "policy_conditions")
public class PolicyCondition extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "field", length = 120, nullable = false)
    private String field;

    @Column(name = "operator", length = 20, nullable = false)
    private String operator;

    @Column(name = "value", columnDefinition = "text", nullable = false)
    private String value;

    public PolicyCondition() {
    }

    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
