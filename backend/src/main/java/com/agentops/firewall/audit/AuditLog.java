package com.agentops.firewall.audit;

import com.agentops.firewall.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Append-only audit record. Every state transition that the firewall cares
 * about (action received, policy decision, approval, rejection, agent key
 * rotation, etc.) emits one row. The {@code AuditLog} table is the source
 * of truth for "what happened, in what order".
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    /** Symbolic name of the event, e.g. {@code ACTION_RECEIVED}. */
    @Column(name = "event_type", length = 60, nullable = false)
    private String eventType;

    /** {@code AGENT}, {@code USER}, or {@code SYSTEM}. */
    @Column(name = "actor_type", length = 20, nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    /** {@code ACTION_REQUEST}, {@code POLICY}, etc. */
    @Column(name = "subject_type", length = 40)
    private String subjectType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "summary", length = 500, nullable = false)
    private String summary;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    public AuditLog() {
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
}
