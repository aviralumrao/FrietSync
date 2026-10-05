package com.frietsync.backend.entity.sprint;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(
        name = "sprint_assignment_requests",
        indexes = {
                @Index(columnList = "sprint_id"),
                @Index(columnList = "sprint_id, status")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class SprintAssignmentRequest extends BaseEntity {

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SprintAssignmentRequestStatus status = SprintAssignmentRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;
}
