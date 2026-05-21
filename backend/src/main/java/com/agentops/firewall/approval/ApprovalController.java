package com.agentops.firewall.approval;

import com.agentops.firewall.approval.dto.ApprovalDecisionRequest;
import com.agentops.firewall.approval.dto.ApprovalResponse;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for the human-in-the-loop approval workflow.
 * ADMIN and REVIEWER can approve/reject; all authenticated roles
 * (including VIEWER) can list and view details.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public Page<ApprovalResponse> list(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return approvalService.findAll(status, agentId, actionType, riskLevel, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public ApprovalResponse detail(@PathVariable UUID id) {
        return approvalService.findById(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<ApprovalResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest body,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        String note = body != null ? body.note() : null;
        ApprovalResponse response = approvalService.approve(id, principal.getId(), note);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<ApprovalResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest body,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        String note = body != null ? body.note() : null;
        ApprovalResponse response = approvalService.reject(id, principal.getId(), note);
        return ResponseEntity.ok(response);
    }
}
