package com.agentops.firewall.policy;

import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.agent.AgentRepository;
import com.agentops.firewall.policy.dto.PolicySimulationRequest;
import com.agentops.firewall.policy.dto.PolicySimulationResponse;
import com.agentops.firewall.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Policy simulator endpoint. Accepts a hypothetical action and returns
 * the firewall decision without touching ActionRequest, PolicyDecision,
 * ApprovalRequest, or the Kafka stream. Available to any authenticated
 * role so that reviewers and viewers can validate policy hypotheses
 * before an agent submits a real action.
 *
 * <p>The simulator intentionally writes NO audit log: simulator queries
 * are read-only by design and high-frequency dashboard usage of the
 * simulator should not flood the audit table. If, in a later phase,
 * simulator usage becomes interesting to security review we can add an
 * opt-in audit flag without changing this contract.
 */
@RestController
@RequestMapping("/api/policies")
public class PolicySimulatorController {

    private final PolicyEvaluator evaluator;
    private final AgentRepository agentRepository;

    public PolicySimulatorController(PolicyEvaluator evaluator, AgentRepository agentRepository) {
        this.evaluator = evaluator;
        this.agentRepository = agentRepository;
    }

    @PostMapping("/simulate")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public PolicySimulationResponse simulate(
            @Valid @RequestBody PolicySimulationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        Agent agent = null;
        if (request.agentName() != null && !request.agentName().isBlank()) {
            agent = agentRepository.findByName(request.agentName()).orElse(null);
        }
        Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        PolicyEvaluationContext ctx = new PolicyEvaluationContext(
                request.actionType(), request.resource(), request.riskLevel(), metadata, agent);
        return PolicySimulationResponse.from(evaluator.evaluate(ctx));
    }
}