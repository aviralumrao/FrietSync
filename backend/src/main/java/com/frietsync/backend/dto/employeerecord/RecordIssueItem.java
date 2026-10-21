package com.frietsync.backend.dto.employeerecord;

import com.frietsync.backend.entity.issue.IssuePriority;
import com.frietsync.backend.entity.issue.IssueStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RecordIssueItem {

    private UUID issueId;
    private UUID projectId;
    private int issueNumber;
    private String title;
    private IssueStatus status;
    private IssuePriority priority;
    private boolean createdByUser;
    private boolean resolvedByUser;
    private boolean assignedToUser;
    private boolean reopened;
    private Instant createdAt;
    private Instant resolvedAt;
    private Double hoursToResolve;
}