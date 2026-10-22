package com.frietsync.backend.service.integration;

import com.frietsync.backend.dto.issue.IssueResponse;
import com.frietsync.backend.entity.issue.Issue;

import java.util.UUID;

public interface GitHubIssueLinkService {
    IssueResponse link(UUID issueId, int githubIssueNumber, UUID userId);
    IssueResponse createAndLink(UUID issueId, UUID userId);
    void unlink(UUID issueId, UUID userId);
    IssueResponse refreshStatus(UUID issueId, UUID userId);
    void pollStatuses();
    void syncStatus(Issue issue, UUID userId);
}
