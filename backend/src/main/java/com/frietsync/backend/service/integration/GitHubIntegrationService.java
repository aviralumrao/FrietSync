package com.frietsync.backend.service.integration;

import com.frietsync.backend.dto.integration.GitHubContributionResponse;
import com.frietsync.backend.dto.integration.ProjectGitHubLinkRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public interface GitHubIntegrationService {
    URI createAuthorizationUri(UUID userId);
    UUID consumeState(String state);
    void complete(UUID userId, String code);
    void disconnect(UUID userId);
    void linkRepository(UUID projectId, UUID userId, ProjectGitHubLinkRequest request);
    void unlinkRepository(UUID projectId, UUID userId);
    List<GitHubContributionResponse> contributions(UUID projectId, UUID userId);
}
