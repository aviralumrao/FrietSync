package com.frietsync.backend.dto.issue;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubIssueResponse {
    private int number;
    private String title;
    private String url;
    private String repository;
}
