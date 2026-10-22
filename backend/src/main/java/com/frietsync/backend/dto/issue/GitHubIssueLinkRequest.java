package com.frietsync.backend.dto.issue;

import jakarta.validation.constraints.Min;

public record GitHubIssueLinkRequest(@Min(1) int issueNumber) {
}
