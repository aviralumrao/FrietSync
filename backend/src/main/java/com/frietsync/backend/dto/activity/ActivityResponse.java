package com.frietsync.backend.dto.activity;

import com.frietsync.backend.entity.issue.IssueHistoryAction;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ActivityResponse {

    private UUID id;
    private UUID issueId;
    private int issueNumber;
    private String issueTitle;
    private UUID actorId;
    private String actorName;
    private IssueHistoryAction action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String comment;
    private Instant createdAt;
}