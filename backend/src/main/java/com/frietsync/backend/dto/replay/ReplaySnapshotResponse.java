package com.frietsync.backend.dto.replay;

import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.entity.replay.ReplayPhaseType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReplaySnapshotResponse {

    private UUID projectId;
    private Instant at;
    private ReplayPhaseType currentPhase;

    private int totalIssues;
    private int openIssues;
    private int resolvedIssues;
    private int closedIssues;
    private int overdueIssues;
    private double progressPercentage;

    private int totalSprints;
    private List<IssueSummaryResponse> issues;
}