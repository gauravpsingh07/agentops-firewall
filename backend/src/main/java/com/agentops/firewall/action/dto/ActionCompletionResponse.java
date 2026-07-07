package com.agentops.firewall.action.dto;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;

import java.util.UUID;

/**
 * Response to {@code POST /api/agent-actions/{id}/complete}: the action id
 * and its new terminal status ({@code COMPLETED} or {@code FAILED}).
 */
public record ActionCompletionResponse(
        UUID actionId,
        ActionRequestStatus status
) {
}
