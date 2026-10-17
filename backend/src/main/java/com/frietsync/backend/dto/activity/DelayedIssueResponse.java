package com.frietsync.backend.dto.activity;

import com.frietsync.backend.entity.issue.IssueStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class DelayedIssueResponse {

    private UUID issueId;
    private int issueNumber;
    private String title;
    private IssueStatus status;
    private LocalDate dueDate;
    private long daysLate;
    private String state;
}