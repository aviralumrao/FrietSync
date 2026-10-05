package com.frietsync.backend.dto.sprint;

import com.frietsync.backend.entity.sprint.SprintAssignmentRequest;
import com.frietsync.backend.entity.sprint.SprintAssignmentRequestStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SprintAssignmentRequestResponse {
    private UUID id;
    private UUID sprintId;
    private UUID requestedBy;
    private SprintAssignmentRequestStatus status;
    private UUID reviewedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static SprintAssignmentRequestResponse fromEntity(SprintAssignmentRequest request) {
        SprintAssignmentRequestResponse response = new SprintAssignmentRequestResponse();
        response.setId(request.getId());
        response.setSprintId(request.getSprintId());
        response.setRequestedBy(request.getRequestedBy());
        response.setStatus(request.getStatus());
        response.setReviewedBy(request.getReviewedBy());
        response.setCreatedAt(request.getCreatedAt());
        response.setUpdatedAt(request.getUpdatedAt());
        return response;
    }
}
