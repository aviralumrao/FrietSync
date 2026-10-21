package com.frietsync.backend.dto.employeerecord;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RecordActivityResponse {

    private UUID userId;
    private String githubUrl;
    private boolean githubConnected;
    private String githubLogin;
    private int commits;
    private int pullRequests;
    private int mergedPullRequests;
    private int githubReviews;
    private int githubFailedRepositories;
    private List<RecordGithubRepoActivity> githubRepositories;
    private long issueReviewsApproved;
    private long issueReviewsRejected;
    private Integer designSubmissions;
    private Integer designReviews;
}