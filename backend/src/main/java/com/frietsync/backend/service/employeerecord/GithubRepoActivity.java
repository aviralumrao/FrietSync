package com.frietsync.backend.service.employeerecord;

public record GithubRepoActivity(String repository, int commits, int pullRequests,
                                 int mergedPullRequests, int reviews) {
}