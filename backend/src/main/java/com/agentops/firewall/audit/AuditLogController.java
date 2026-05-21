package com.agentops.firewall.audit;

import com.agentops.firewall.audit.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for searching and paginating the append-only audit
 * log. All authenticated users with ADMIN, REVIEWER, or VIEWER role can
 * access the audit trail; no mutation endpoints exist because the audit
 * log is append-only by design.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public Page<AuditLogResponse> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                eventType, actorType, actorId, subjectType, subjectId, from, to);

        return auditLogRepository.findAll(spec, PageRequest.of(page, size, Sort.by(dir, sort)))
                .map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEventType(),
                log.getActorType(),
                log.getActorId(),
                log.getSubjectType(),
                log.getSubjectId(),
                log.getSummary(),
                log.getDetailsJson(),
                log.getCreatedAt()
        );
    }
}
