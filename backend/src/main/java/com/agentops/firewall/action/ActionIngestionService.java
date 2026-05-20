package com.agentops.firewall.action;

import com.agentops.firewall.action.dto.ActionDecisionResponse;
import com.agentops.firewall.action.dto.SubmitActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.agent.AgentAuthenticationService;
import com.agentops.firewall.agent.AgentService;
import com.agentops.firewall.audit.AuditService;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.policy.PolicyEvaluationContext;
import com.agentops.firewall.policy.PolicyEvaluationResult;
import com.agentops.firewall.policy.PolicyEvaluator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Orchestrates the action-ingestion pipeline:
 * <ol>
 *   <li>Validate the X-Agent-Key / agent name pair via
 *       {@link AgentAuthenticationService}.</li>
 *   <li>Persist the ActionRequest in RECEIVED state.</li>
 *   <li>Emit ACTION_RECEIVED audit log.</li>
 *   <li>Run the policy evaluator.</li>
 *   <li>Persist a PolicyDecision row.</li>
 *   <li>Map the outcome to a terminal-or-pending action status
 *       (ALLOWED / DENIED / PENDING_APPROVAL) and persist it.</li>
 *   <li>Emit POLICY_DECISION audit log.</li>
 *   <li>Mark the agent's lastUsedAt timestamp.</li>
 * </ol>
 *
 * <p>This commit deliberately does not create ApprovalRequest rows or
 * publish Kafka events; those land in the next commits of Phase 3.
 */
@Service
public class ActionIngestionService {

    private final AgentAuthenticationService agentAuthenticationService;
    private final AgentService agentService;
    private final ActionRequestRepository actionRequestRepository;
    private final PolicyDecisionRepository policyDecisionRepository;
    private final PolicyEvaluator policyEvaluator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ActionIngestionService(AgentAuthenticationService agentAuthenticationService,
                                   AgentService agentService,
                                   ActionRequestRepository actionRequestRepository,
                                   PolicyDecisionRepository policyDecisionRepository,
                                   PolicyEvaluator policyEvaluator,
                                   AuditService auditService,
                                   ObjectMapper objectMapper) {
        this.agentAuthenticationService = agentAuthenticationService;
        this.agentService = agentService;
        this.actionRequestRepository = actionRequestRepository;
        this.policyDecisionRepository = policyDecisionRepository;
        this.policyEvaluator = policyEvaluator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ActionDecisionResponse submit(SubmitActionRequest body, String rawAgentKey) {
        Agent agent = agentAuthenticationService.authenticate(body.agentId(), rawAgentKey);

        ActionRequest action = new ActionRequest();
        action.setAgentId(agent.getId());
        action.setActionType(body.actionType());
        action.setResource(body.resource());
        action.setRiskLevel(body.riskLevel());
        action.setStatus(ActionRequestStatus.RECEIVED);
        Map<String, Object> metadata = body.metadata() == null ? Map.of() : body.metadata();
        action.setMetadataJson(serialize(metadata));
        ActionRequest saved = actionRequestRepository.save(action);

        auditService.record(
                AuditService.EVENT_ACTION_RECEIVED,
                AuditService.ACTOR_AGENT, agent.getId(),
                AuditService.SUBJECT_ACTION_REQUEST, saved.getId(),
                "Action received: " + body.actionType().name(),
                AuditService.details(
                        "agentName", agent.getName(),
                        "actionType", body.actionType().name(),
                        "riskLevel", body.riskLevel().name(),
                        "resource", body.resource()
                )
        );

        PolicyEvaluationContext ctx = new PolicyEvaluationContext(
                body.actionType(), body.resource(), body.riskLevel(), metadata, agent);
        PolicyEvaluationResult evaluation = policyEvaluator.evaluate(ctx);

        PolicyDecision decision = new PolicyDecision();
        decision.setActionRequestId(saved.getId());
        decision.setDecision(evaluation.outcome());
        decision.setMatchedPolicyId(evaluation.matchedPolicyId());
        decision.setEvaluatedAt(Instant.now());
        decision.setEvaluationDetailsJson(serialize(Map.of(
                "matchedPolicyName", String.valueOf(evaluation.matchedPolicyName()),
                "reason", String.valueOf(evaluation.reason())
        )));
        policyDecisionRepository.save(decision);

        ActionRequestStatus newStatus = mapStatus(evaluation.outcome());
        saved.setStatus(newStatus);
        saved.setMatchedPolicyId(evaluation.matchedPolicyId());
        saved.setDecisionReason(evaluation.reason());
        actionRequestRepository.save(saved);

        auditService.record(
                AuditService.EVENT_POLICY_DECISION,
                AuditService.ACTOR_SYSTEM, null,
                AuditService.SUBJECT_ACTION_REQUEST, saved.getId(),
                "Policy decision: " + evaluation.outcome().name(),
                AuditService.details(
                        "decision", evaluation.outcome().name(),
                        "matchedPolicyId", evaluation.matchedPolicyId(),
                        "matchedPolicyName", evaluation.matchedPolicyName(),
                        "reason", evaluation.reason()
                )
        );

        agentService.markUsed(agent.getId());

        return new ActionDecisionResponse(
                saved.getId(),
                evaluation.outcome(),
                newStatus,
                evaluation.matchedPolicyId(),
                evaluation.matchedPolicyName(),
                evaluation.reason());
    }

    private ActionRequestStatus mapStatus(PolicyOutcome outcome) {
        return switch (outcome) {
            case ALLOW -> ActionRequestStatus.ALLOWED;
            case DENY -> ActionRequestStatus.DENIED;
            case NEEDS_APPROVAL -> ActionRequestStatus.PENDING_APPROVAL;
        };
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}