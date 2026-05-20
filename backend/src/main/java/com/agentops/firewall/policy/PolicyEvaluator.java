package com.agentops.firewall.policy;

import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.policy.condition.ConditionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pure-logic policy evaluator. Loads the enabled policies in
 * highest-priority-first / name-ascending order and returns the outcome
 * of the first matching policy.
 *
 * <h2>Match rules</h2>
 * <ul>
 *   <li>If the policy specifies an {@code actionType}, the action's
 *       type must equal it.</li>
 *   <li>If the policy specifies a {@code resourcePattern}, the action's
 *       resource must match it as a regular expression.</li>
 *   <li>If the policy specifies a {@code minRiskLevel}, the action's
 *       risk level must be greater than or equal to it.</li>
 *   <li>All {@link PolicyCondition} rows attached to the policy must
 *       match (logical AND).</li>
 * </ul>
 *
 * <h2>Safe default</h2>
 * When no policy matches the firewall returns
 * {@link PolicyOutcome#NEEDS_APPROVAL}. This is the conservative
 * default: an action the firewall does not recognise is escalated to a
 * human rather than waved through.
 */
@Service
public class PolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluator.class);

    /**
     * Outcome returned when no enabled policy matches the action. Chosen
     * to be the safer of the three possible defaults: unknown behaviour
     * gets human review rather than implicit approval or implicit deny.
     */
    public static final PolicyOutcome DEFAULT_OUTCOME = PolicyOutcome.NEEDS_APPROVAL;

    private final PolicyRepository policyRepository;
    private final PolicyConditionRepository conditionRepository;
    private final ConditionEvaluator conditionEvaluator;

    public PolicyEvaluator(PolicyRepository policyRepository,
                           PolicyConditionRepository conditionRepository,
                           ConditionEvaluator conditionEvaluator) {
        this.policyRepository = policyRepository;
        this.conditionRepository = conditionRepository;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Transactional(readOnly = true)
    public PolicyEvaluationResult evaluate(PolicyEvaluationContext ctx) {
        List<Policy> ordered = policyRepository.findByEnabledTrueOrderByPriorityDescNameAsc();
        for (Policy policy : ordered) {
            if (matches(policy, ctx)) {
                String reason = describe(policy, ctx);
                log.debug("Policy matched: name={} priority={} effect={}",
                        policy.getName(), policy.getPriority(), policy.getEffect());
                return PolicyEvaluationResult.matched(
                        policy.getEffect(), policy.getId(), policy.getName(), reason);
            }
        }
        log.debug("No policy matched action; applying default {}", DEFAULT_OUTCOME);
        return PolicyEvaluationResult.defaultOutcome(
                DEFAULT_OUTCOME,
                "No matching policy. Default outcome is " + DEFAULT_OUTCOME + " (safe default).");
    }

    private boolean matches(Policy policy, PolicyEvaluationContext ctx) {
        if (policy.getActionType() != null && policy.getActionType() != ctx.actionType()) {
            return false;
        }
        if (policy.getResourcePattern() != null && !policy.getResourcePattern().isBlank()) {
            String resource = ctx.resource() == null ? "" : ctx.resource();
            try {
                if (!Pattern.compile(policy.getResourcePattern()).matcher(resource).matches()) {
                    return false;
                }
            } catch (PatternSyntaxException ex) {
                log.warn("Policy {} has an invalid resource pattern: {}",
                        policy.getName(), ex.getDescription());
                return false;
            }
        }
        if (policy.getMinRiskLevel() != null && !atLeast(ctx.riskLevel(), policy.getMinRiskLevel())) {
            return false;
        }
        List<PolicyCondition> conditions = conditionRepository.findByPolicyId(policy.getId());
        for (PolicyCondition condition : conditions) {
            if (!conditionEvaluator.matches(condition, ctx)) {
                return false;
            }
        }
        return true;
    }

    private boolean atLeast(RiskLevel actual, RiskLevel minimum) {
        if (actual == null) return false;
        return actual.ordinal() >= minimum.ordinal();
    }

    private String describe(Policy policy, PolicyEvaluationContext ctx) {
        StringBuilder reason = new StringBuilder("Matched policy '")
                .append(policy.getName()).append("'");
        if (policy.getActionType() != null) {
            reason.append(" for action ").append(policy.getActionType().name());
        }
        if (policy.getMinRiskLevel() != null && ctx.riskLevel() != null) {
            reason.append(" at risk ").append(ctx.riskLevel().name());
        }
        return reason.toString();
    }
}