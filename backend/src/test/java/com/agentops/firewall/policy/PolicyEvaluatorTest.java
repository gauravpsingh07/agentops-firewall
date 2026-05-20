package com.agentops.firewall.policy;

import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the policy evaluator against the seeded sample policy set. The
 * tests intentionally exercise priority order, condition-based matches,
 * and the no-match safe default.
 */
@SpringBootTest
@ActiveProfiles("test")
class PolicyEvaluatorTest {

    @Autowired PolicyEvaluator evaluator;
    @Autowired PolicyRepository policyRepository;
    @Autowired PolicyConditionRepository conditionRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void seedDefaults() {
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        auditLogRepository.deleteAll();
        SamplePolicySeeder.seed(policyRepository, conditionRepository);
    }

    @AfterEach
    void cleanup() {
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    @DisplayName("READ_SECRET is denied at any risk level")
    void readSecretIsDenied() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.READ_SECRET, "vault-db-password", RiskLevel.LOW,
                Map.of(), null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.DENY);
        assertThat(result.matchedPolicyName()).contains("READ_SECRET");
    }

    @Test
    @DisplayName("DELETE_FILE requires approval")
    void deleteFileNeedsApproval() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.DELETE_FILE, "tmp-temp-file", RiskLevel.MEDIUM,
                Map.of(), null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.NEEDS_APPROVAL);
        assertThat(result.matchedPolicyName()).contains("DELETE_FILE");
    }

    @Test
    @DisplayName("DEPLOY_CODE requires approval")
    void deployCodeNeedsApproval() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.DEPLOY_CODE, "service-x", RiskLevel.HIGH,
                Map.of(), null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.NEEDS_APPROVAL);
    }

    @Test
    @DisplayName("SEND_EMAIL with attachment to external domain requires approval")
    void sendEmailExternalWithAttachmentNeedsApproval() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.SEND_EMAIL, "external_email", RiskLevel.MEDIUM,
                Map.of("recipientDomain", "external.com", "containsAttachment", true),
                null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.NEEDS_APPROVAL);
        assertThat(result.matchedPolicyName()).contains("external email");
    }

    @Test
    @DisplayName("SEND_EMAIL to internal domain falls through to default since attachment+external rule does not match")
    void sendEmailInternalFallsThroughDefault() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.SEND_EMAIL, "internal_email", RiskLevel.MEDIUM,
                Map.of("recipientDomain", "internal", "containsAttachment", true),
                null));
        assertThat(result.outcome()).isEqualTo(PolicyEvaluator.DEFAULT_OUTCOME);
        assertThat(result.matchedPolicyId()).isNull();
    }

    @Test
    @DisplayName("ACCESS_CUSTOMER_DATA is denied at HIGH risk, allowed implicitly via no-match at LOW")
    void accessCustomerDataDeniedAtHighRisk() {
        PolicyEvaluationResult high = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.ACCESS_CUSTOMER_DATA, "customers-all", RiskLevel.HIGH,
                Map.of(), null));
        assertThat(high.outcome()).isEqualTo(PolicyOutcome.DENY);

        PolicyEvaluationResult low = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.ACCESS_CUSTOMER_DATA, "customers-single", RiskLevel.LOW,
                Map.of(), null));
        assertThat(low.outcome()).isEqualTo(PolicyOutcome.ALLOW);
    }

    @Test
    @DisplayName("CALL_EXTERNAL_API to non-allowlisted domain is denied; allowlisted domain falls through")
    void callExternalApiAllowlist() {
        PolicyEvaluationResult denied = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.CALL_EXTERNAL_API, "https-evil-example", RiskLevel.MEDIUM,
                Map.of("targetDomain", "evil.example.com"), null));
        assertThat(denied.outcome()).isEqualTo(PolicyOutcome.DENY);

        PolicyEvaluationResult ok = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.CALL_EXTERNAL_API, "https-acme-data", RiskLevel.MEDIUM,
                Map.of("targetDomain", "acme.com"), null));
        assertThat(ok.outcome()).isEqualTo(PolicyEvaluator.DEFAULT_OUTCOME);
    }

    @Test
    @DisplayName("RUN_TERMINAL_COMMAND with allowlisted command falls through; otherwise approval")
    void runTerminalCommandAllowlist() {
        PolicyEvaluationResult dangerous = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.RUN_TERMINAL_COMMAND, "danger-cmd", RiskLevel.HIGH,
                Map.of("commandType", "rm"), null));
        assertThat(dangerous.outcome()).isEqualTo(PolicyOutcome.NEEDS_APPROVAL);

        PolicyEvaluationResult safe = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.RUN_TERMINAL_COMMAND, "list-dir", RiskLevel.LOW,
                Map.of("commandType", "ls"), null));
        assertThat(safe.outcome()).isEqualTo(PolicyOutcome.ALLOW);
    }

    @Test
    @DisplayName("LOW-risk unknown action is allowed by the LOW-risk default-allow rule")
    void lowRiskUnknownActionIsAllowed() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.CALL_EXTERNAL_API, "ping-acme", RiskLevel.LOW,
                Map.of("targetDomain", "acme.com"), null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.ALLOW);
        assertThat(result.matchedPolicyName()).contains("LOW-risk");
    }

    @Test
    @DisplayName("higher-priority policy wins when multiple rules match")
    void higherPriorityWins() {
        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.ACCESS_CUSTOMER_DATA, "customers-all", RiskLevel.CRITICAL,
                Map.of(), null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.DENY);
    }

    @Test
    @DisplayName("disabled policies are ignored")
    void disabledPoliciesIgnored() {
        Policy readSecret = policyRepository.findByName("Deny READ_SECRET").orElseThrow();
        readSecret.setEnabled(false);
        policyRepository.save(readSecret);

        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.READ_SECRET, "vault-x", RiskLevel.MEDIUM,
                Map.of(), null));
        assertThat(result.outcome()).isEqualTo(PolicyEvaluator.DEFAULT_OUTCOME);
    }

    @Test
    @DisplayName("no-match falls back to NEEDS_APPROVAL safe default")
    void noMatchDefaultsToNeedsApproval() {
        Policy lowAllow = policyRepository.findByName("Allow LOW-risk actions").orElseThrow();
        lowAllow.setEnabled(false);
        policyRepository.save(lowAllow);

        PolicyEvaluationResult result = evaluator.evaluate(new PolicyEvaluationContext(
                ActionType.CALL_EXTERNAL_API, "low-call-acme", RiskLevel.LOW,
                Map.of("targetDomain", "acme.com"), null));
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.NEEDS_APPROVAL);
        assertThat(result.matchedPolicyId()).isNull();
    }
}