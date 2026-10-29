package com.frietsync.backend.dto.issue;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class IssueLinkResponse {

    private UUID id;
    private UUID projectId;
    private UUID sourceIssueId;
    private int sourceIssueNumber;
    private String sourceTitle;
    private UUID targetIssueId;
    private int targetIssueNumber;
    private String targetTitle;
    private UUID createdBy;
    private String createdByName;
    private Instant createdAt;
}
