package com.agentops.firewall.policy;

import com.agentops.firewall.audit.AuditService;
import com.agentops.firewall.common.error.ConflictException;
import com.agentops.firewall.common.error.NotFoundException;
import com.agentops.firewall.policy.dto.CreatePolicyRequest;
import com.agentops.firewall.policy.dto.PolicyConditionDto;
import com.agentops.firewall.policy.dto.PolicyResponse;
import com.agentops.firewall.policy.dto.UpdatePolicyRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * CRUD operations for policies. Mutating operations write an audit
 * trail (POLICY_CREATED / POLICY_UPDATED / POLICY_DISABLED) and never
 * physically delete a policy: DELETE flips {@code enabled} to false so
 * historical decisions stay attributable.
 */
@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyConditionRepository conditionRepository;
    private final AuditService auditService;

    public PolicyService(PolicyRepository policyRepository,
                          PolicyConditionRepository conditionRepository,
                          AuditService auditService) {
        this.policyRepository = policyRepository;
        this.conditionRepository = conditionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        List<Policy> policies = policyRepository.findAll();
        policies.sort(Comparator.comparingInt(Policy::getPriority).reversed()
                .thenComparing(Policy::getName));
        return policies.stream()
                .map(p -> PolicyResponse.fromEntity(p, conditionRepository.findByPolicyId(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(UUID id) {
        Policy policy = loadById(id);
        return PolicyResponse.fromEntity(policy, conditionRepository.findByPolicyId(id));
    }

    @Transactional
    public PolicyResponse create(CreatePolicyRequest request, UUID byUserId) {
        if (policyRepository.existsByName(request.name())) {
            throw new ConflictException("Policy with name " + request.name() + " already exists.");
        }
        Policy policy = new Policy();
        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setEffect(request.effect());
        policy.setPriority(request.priority());
        policy.setEnabled(true);
        policy.setActionType(request.actionType());
        policy.setResourcePattern(request.resourcePattern());
        policy.setMinRiskLevel(request.minRiskLevel());
        policy.setCreatedByUserId(byUserId);
        Policy saved = policyRepository.save(policy);

        if (request.conditions() != null) {
            for (PolicyConditionDto c : request.conditions()) {
                persistCondition(saved.getId(), c);
            }
        }

        auditService.record(
                AuditService.EVENT_POLICY_CREATED,
                AuditService.ACTOR_USER, byUserId,
                AuditService.SUBJECT_POLICY, saved.getId(),
                "Policy " + saved.getName() + " created.",
                AuditService.details(
                        "name", saved.getName(),
                        "effect", saved.getEffect().name(),
                        "priority", saved.getPriority()
                )
        );
        return PolicyResponse.fromEntity(saved, conditionRepository.findByPolicyId(saved.getId()));
    }

    @Transactional
    public PolicyResponse update(UUID id, UpdatePolicyRequest request, UUID byUserId) {
        Policy policy = loadById(id);
        if (request.name() != null) policy.setName(request.name());
        if (request.description() != null) policy.setDescription(request.description());
        if (request.effect() != null) policy.setEffect(request.effect());
        if (request.priority() != null) policy.setPriority(request.priority());
        if (request.enabled() != null) policy.setEnabled(request.enabled());
        if (request.actionType() != null) policy.setActionType(request.actionType());
        if (request.resourcePattern() != null) policy.setResourcePattern(request.resourcePattern());
        if (request.minRiskLevel() != null) policy.setMinRiskLevel(request.minRiskLevel());

        if (request.conditions() != null) {
            List<PolicyCondition> existing = conditionRepository.findByPolicyId(id);
            conditionRepository.deleteAll(existing);
            for (PolicyConditionDto c : request.conditions()) {
                persistCondition(id, c);
            }
        }

        auditService.record(
                AuditService.EVENT_POLICY_UPDATED,
                AuditService.ACTOR_USER, byUserId,
                AuditService.SUBJECT_POLICY, policy.getId(),
                "Policy " + policy.getName() + " updated.",
                AuditService.details(
                        "name", policy.getName(),
                        "effect", policy.getEffect().name(),
                        "enabled", policy.isEnabled()
                )
        );
        return PolicyResponse.fromEntity(policy, conditionRepository.findByPolicyId(id));
    }

    /**
     * Soft-delete: flip enabled=false so historical PolicyDecision rows
     * still reference a real Policy row. Returns the disabled projection.
     */
    @Transactional
    public PolicyResponse disable(UUID id, UUID byUserId) {
        Policy policy = loadById(id);
        if (!policy.isEnabled()) {
            return PolicyResponse.fromEntity(policy, conditionRepository.findByPolicyId(id));
        }
        policy.setEnabled(false);
        auditService.record(
                AuditService.EVENT_POLICY_DISABLED,
                AuditService.ACTOR_USER, byUserId,
                AuditService.SUBJECT_POLICY, policy.getId(),
                "Policy " + policy.getName() + " disabled.",
                AuditService.details(
                        "name", policy.getName()
                )
        );
        return PolicyResponse.fromEntity(policy, conditionRepository.findByPolicyId(id));
    }

    private void persistCondition(UUID policyId, PolicyConditionDto dto) {
        PolicyCondition c = new PolicyCondition();
        c.setPolicyId(policyId);
        c.setField(dto.field());
        c.setOperator(dto.operator());
        c.setValue(dto.value());
        conditionRepository.save(c);
    }

    private Policy loadById(UUID id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Policy not found: " + id));
    }
}