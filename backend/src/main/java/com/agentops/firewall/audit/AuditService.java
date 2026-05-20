package com.agentops.firewall.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized append-only audit recording. Every state transition that
 * matters to the firewall (agent lifecycle, action receipt, policy
 * decision, policy lifecycle, future approvals) lands in
 * {@code audit_logs}. Audit writes are intentionally written in a new
 * transaction so a downstream rollback does not lose the trail.
 *
 * <p>Audit details must never include raw API keys, bearer tokens, or
 * other credentials. Helpers in this class encode this rule by accepting
 * structured, hand-curated detail maps rather than free-form objects.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String ACTOR_USER   = "USER";
    public static final String ACTOR_AGENT  = "AGENT";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    public static final String SUBJECT_AGENT          = "AGENT";
    public static final String SUBJECT_ACTION_REQUEST = "ACTION_REQUEST";
    public static final String SUBJECT_POLICY         = "POLICY";

    public static final String EVENT_AGENT_CREATED      = "AGENT_CREATED";
    public static final String EVENT_AGENT_UPDATED      = "AGENT_UPDATED";
    public static final String EVENT_AGENT_KEY_ROTATED  = "AGENT_KEY_ROTATED";
    public static final String EVENT_ACTION_RECEIVED    = "ACTION_RECEIVED";
    public static final String EVENT_POLICY_DECISION    = "POLICY_DECISION";
    public static final String EVENT_POLICY_CREATED     = "POLICY_CREATED";
    public static final String EVENT_POLICY_UPDATED     = "POLICY_UPDATED";
    public static final String EVENT_POLICY_DISABLED    = "POLICY_DISABLED";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String eventType,
                           String actorType, UUID actorId,
                           String subjectType, UUID subjectId,
                           String summary,
                           Map<String, Object> details) {
        AuditLog entry = new AuditLog();
        entry.setEventType(eventType);
        entry.setActorType(actorType);
        entry.setActorId(actorId);
        entry.setSubjectType(subjectType);
        entry.setSubjectId(subjectId);
        entry.setSummary(summary);
        entry.setDetailsJson(serialize(details));
        return repository.save(entry);
    }

    /** Convenience for events with no structured detail body. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String eventType,
                           String actorType, UUID actorId,
                           String subjectType, UUID subjectId,
                           String summary) {
        return record(eventType, actorType, actorId, subjectType, subjectId, summary, null);
    }

    /**
     * Build an ordered map literal. Convenience for callers so they can
     * write {@code Map.of("k", v)} alternatives that preserve insertion
     * order (helpful for human-readable audit details).
     */
    public static Map<String, Object> details(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("details() requires an even number of arguments");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private String serialize(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit details, storing null: {}", ex.getMessage());
            return null;
        }
    }
}
