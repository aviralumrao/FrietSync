package com.frietsync.backend.dto.issue;

import com.frietsync.backend.entity.issue.AssignmentRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AssignmentRequestResponse {

    private UUID id;
    private UUID projectId;
    private UUID issueId;
    private UUID userId;
    private String requesterName;
    private String requesterEmail;
    private AssignmentRequestStatus status;
    private UUID decidedBy;
    private String decidedByName;
    private Instant decidedAt;
    private Instant createdAt;
}
