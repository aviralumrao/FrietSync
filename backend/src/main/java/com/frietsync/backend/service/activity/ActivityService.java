package com.frietsync.backend.service.activity;

import com.frietsync.backend.dto.activity.ActivityResponse;
import com.frietsync.backend.dto.activity.PresenceResponse;
import com.frietsync.backend.dto.activity.ProjectSummaryResponse;
import com.frietsync.backend.dto.issue.IssuePageResponse;
import com.frietsync.backend.entity.issue.IssueHistoryAction;

import java.util.List;
import java.util.UUID;

public interface ActivityService {

    List<PresenceResponse> presence(UUID projectId, UUID userId);

    List<ActivityResponse> recentActivity(UUID projectId, UUID userId, int limit);

    IssuePageResponse<ActivityResponse> events(UUID projectId, UUID userId, IssueHistoryAction action,
                                               int page, int size);

    ProjectSummaryResponse summary(UUID projectId, UUID userId);

    byte[] summaryPdf(UUID projectId, UUID userId);
}