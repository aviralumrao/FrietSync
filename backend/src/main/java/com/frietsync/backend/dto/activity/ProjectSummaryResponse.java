package com.frietsync.backend.dto.activity;

import com.frietsync.backend.entity.project.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProjectSummaryResponse {

    private UUID projectId;
    private String projectName;
    private ProjectStatus projectStatus;
    private LocalDate deadline;
    private boolean projectDelayed;
    private long projectDaysLate;
    private Instant generatedAt;

    private int totalIssues;
    private int openIssues;
    private int inProgressIssues;
    private int resolvedIssues;
    private int reopenedIssues;
    private int closedIssues;

    private int overdueIssues;
    private int completedLateIssues;
    private List<DelayedIssueResponse> delayedIssues;

    private double totalEstimatedHours;
    private double finishedEstimatedHours;
    private double finishedActualHours;

    private int totalSprints;
    private int plannedSprints;
    private int activeSprints;
    private int completedSprints;
    private int overdueSprints;
}