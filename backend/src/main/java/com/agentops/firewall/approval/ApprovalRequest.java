package com.agentops.firewall.approval;

import com.agentops.firewall.common.domain.BaseEntity;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A human-in-the-loop approval task created when a policy returns
 * {@code NEEDS_APPROVAL}. Pushed to RabbitMQ for downstream notification
 * and surfaced in the reviewer inbox.
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseEntity {

    @Column(name = "action_request_id", nullable = false)
    private UUID actionRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "reviewer_user_id")
    private UUID reviewerUserId;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public ApprovalRequest() {
    }

    public UUID getActionRequestId() { return actionRequestId; }
    public void setActionRequestId(UUID actionRequestId) { this.actionRequestId = actionRequestId; }

    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }

    public UUID getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(UUID reviewerUserId) { this.reviewerUserId = reviewerUserId; }

    public String getReviewerNote() { return reviewerNote; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
