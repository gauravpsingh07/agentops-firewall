package com.agentops.firewall.policy;

import com.agentops.firewall.policy.dto.CreatePolicyRequest;
import com.agentops.firewall.policy.dto.PolicyResponse;
import com.agentops.firewall.policy.dto.UpdatePolicyRequest;
import com.agentops.firewall.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Policy CRUD endpoints. Reads are available to any authenticated user;
 * writes require ADMIN. DELETE is a soft-disable so historical
 * PolicyDecision rows remain attributable.
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public List<PolicyResponse> list() {
        return policyService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public PolicyResponse get(@PathVariable UUID id) {
        return policyService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PolicyResponse> create(
            @Valid @RequestBody CreatePolicyRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        PolicyResponse created = policyService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePolicyRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return policyService.update(id, request, principal.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyResponse disable(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return policyService.disable(id, principal.getId());
    }
}