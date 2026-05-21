package com.agentops.firewall.approval.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/approvals/{id}/approve and /reject.
 * Only the optional reviewer note is accepted; the action itself is
 * identified by the path variable.
 */
public record ApprovalDecisionRequest(
        @Size(max = 1000, message = "note must be at most 1000 characters")
        String note
) {
}
