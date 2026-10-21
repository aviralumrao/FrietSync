package com.frietsync.backend.service.employeerecord;

import java.util.List;

public record GithubActivity(boolean connected, String login, int commits, int pullRequests,
                             int mergedPullRequests, int reviews, int failedRepositories,
                             List<GithubRepoActivity> repositories) {

    public static GithubActivity notConnected() {
        return new GithubActivity(false, null, 0, 0, 0, 0, 0, List.of());
    }
}