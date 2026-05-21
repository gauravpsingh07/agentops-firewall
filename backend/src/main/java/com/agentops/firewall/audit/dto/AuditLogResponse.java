package com.agentops.firewall.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection for GET /api/audit-logs. Maps directly from the
 * {@link com.agentops.firewall.audit.AuditLog} entity with no
 * transformation.
 */
public record AuditLogResponse(
        UUID id,
        String eventType,
        String actorType,
        UUID actorId,
        String subjectType,
        UUID subjectId,
        String summary,
        String detailsJson,
        Instant createdAt
) {
}
