package com.frietsync.backend.dto.integration;

import java.util.List;
import java.util.UUID;

public record GitHubContributionResponse(UUID userId, String name, String githubLogin,
                                         int commits, int pullRequests, int mergedPullRequests,
                                         List<GitHubContributionDay> activity) {
    public record GitHubContributionDay(String date, int commits) { }
}
