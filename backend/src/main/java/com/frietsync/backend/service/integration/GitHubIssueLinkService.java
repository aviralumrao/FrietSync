package com.frietsync.backend.service.integration;

import com.frietsync.backend.dto.issue.IssueResponse;

import java.util.UUID;

public interface GitHubIssueLinkService {
    IssueResponse link(UUID issueId, int githubIssueNumber, UUID userId);
    IssueResponse createAndLink(UUID issueId, UUID userId);
    void unlink(UUID issueId, UUID userId);
}
