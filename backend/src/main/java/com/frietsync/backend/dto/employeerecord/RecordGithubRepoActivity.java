package com.frietsync.backend.dto.employeerecord;

public record RecordGithubRepoActivity(String repository, int commits, int pullRequests,
                                       int mergedPullRequests, int reviews) {
}