package com.agentops.firewall.policy.condition;

import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.policy.PolicyCondition;
import com.agentops.firewall.policy.PolicyEvaluationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the operator semantics in {@link ConditionEvaluator}.
 * Driven by hand-rolled PolicyEvaluationContext instances; no Spring
 * context, no database.
 */
class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;
    private PolicyEvaluationContext ctx;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator(new ObjectMapper().findAndRegisterModules());
        ctx = new PolicyEvaluationContext(
                ActionType.SEND_EMAIL,
                "external_email",
                RiskLevel.MEDIUM,
                Map.of(
                        "recipientDomain", "external.com",
                        "containsAttachment", true,
                        "subject", "hello world"
                ),
                null);
    }

    @Test
    @DisplayName("EQUALS matches metadata strings case-insensitively")
    void equalsMatchesString() {
        assertThat(evaluator.matches(cond("metadata.recipientDomain", "EQUALS", "EXTERNAL.COM"), ctx)).isTrue();
        assertThat(evaluator.matches(cond("metadata.recipientDomain", "EQUALS", "internal"), ctx)).isFalse();
    }

    @Test
    @DisplayName("EQUALS matches boolean values via toString")
    void equalsMatchesBoolean() {
        assertThat(evaluator.matches(cond("metadata.containsAttachment", "EQUALS", "true"), ctx)).isTrue();
        assertThat(evaluator.matches(cond("metadata.containsAttachment", "EQUALS", "false"), ctx)).isFalse();
    }

    @Test
    @DisplayName("IN accepts JSON-array values and matches case-insensitively")
    void inJsonArrayMatches() {
        assertThat(evaluator.matches(
                cond("metadata.recipientDomain", "IN", "[\"acme.com\",\"external.com\"]"), ctx)).isTrue();
        assertThat(evaluator.matches(
                cond("metadata.recipientDomain", "IN", "[\"acme.com\",\"internal\"]"), ctx)).isFalse();
    }

    @Test
    @DisplayName("IN accepts comma-separated fallback")
    void inCommaSeparatedMatches() {
        assertThat(evaluator.matches(
                cond("metadata.recipientDomain", "IN", "acme.com, external.com"), ctx)).isTrue();
    }

    @Test
    @DisplayName("NOT_IN is the negation of IN")
    void notInIsNegation() {
        assertThat(evaluator.matches(
                cond("metadata.recipientDomain", "NOT_IN", "[\"acme.com\",\"internal\"]"), ctx)).isTrue();
        assertThat(evaluator.matches(
                cond("metadata.recipientDomain", "NOT_IN", "[\"external.com\"]"), ctx)).isFalse();
    }

    @Test
    @DisplayName("EXISTS / NOT_EXISTS detect presence")
    void existsAndNotExists() {
        assertThat(evaluator.matches(cond("metadata.subject", "EXISTS", ""), ctx)).isTrue();
        assertThat(evaluator.matches(cond("metadata.missingField", "EXISTS", ""), ctx)).isFalse();
        assertThat(evaluator.matches(cond("metadata.missingField", "NOT_EXISTS", ""), ctx)).isTrue();
    }

    @Test
    @DisplayName("CONTAINS finds a substring case-insensitively")
    void containsSubstring() {
        assertThat(evaluator.matches(cond("metadata.subject", "CONTAINS", "HELLO"), ctx)).isTrue();
        assertThat(evaluator.matches(cond("metadata.subject", "CONTAINS", "goodbye"), ctx)).isFalse();
    }

    @Test
    @DisplayName("MATCHES uses a regular expression")
    void matchesRegex() {
        assertThat(evaluator.matches(cond("metadata.recipientDomain", "MATCHES", ".*\\.com"), ctx)).isTrue();
        assertThat(evaluator.matches(cond("metadata.recipientDomain", "MATCHES", "internal-only"), ctx)).isFalse();
    }

    @Test
    @DisplayName("top-level actionType and riskLevel fields resolve")
    void topLevelFields() {
        assertThat(evaluator.matches(cond("actionType", "EQUALS", "SEND_EMAIL"), ctx)).isTrue();
        assertThat(evaluator.matches(cond("riskLevel", "EQUALS", "MEDIUM"), ctx)).isTrue();
    }

    @Test
    @DisplayName("unknown operator on a condition row is treated as non-match")
    void unknownOperatorIsNonMatch() {
        assertThat(evaluator.matches(cond("metadata.recipientDomain", "GLORP", "external.com"), ctx)).isFalse();
    }

    @Test
    @DisplayName("unknown field root resolves to null and falls through to non-match")
    void unknownFieldRootIsNonMatch() {
        assertThat(evaluator.matches(cond("planet.mars", "EQUALS", "external.com"), ctx)).isFalse();
    }

    private PolicyCondition cond(String field, String operator, String value) {
        PolicyCondition c = new PolicyCondition();
        c.setField(field);
        c.setOperator(operator);
        c.setValue(value);
        return c;
    }
}