package com.frietsync.backend.entity.poll;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "polls")
public class Poll extends BaseEntity {
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(nullable = false, length = 200)
    private String question;
    @Column(length = 4000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PollMode mode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PollStatus status = PollStatus.OPEN;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "issue_id")
    private UUID issueId;
    @Column(name = "sprint_id")
    private UUID sprintId;
    @Column(name = "milestone_id")
    private UUID milestoneId;
    @Column(name = "closes_at")
    private Instant endsAt;
    @Column(name = "decision_record", length = 4000)
    private String decisionRecord;
    @Column(name = "closed_at")
    private Instant closedAt;
}
