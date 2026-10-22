package com.frietsync.backend.dto.issue;

import com.frietsync.backend.entity.issue.IssuePriority;
import com.frietsync.backend.entity.issue.IssueStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class IssueResponse {

    private UUID id;
    private UUID projectId;
    private int issueNumber;
    private String title;
    private String description;
    private IssueStatus status;
    private IssuePriority priority;
    private LocalDate dueDate;
    private Double estimatedHours;
    private UUID createdBy;
    private List<UUID> assigneeIds;
    private Instant resolvedAt;
    private Instant closedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private GitHubIssueResponse githubIssue;
}