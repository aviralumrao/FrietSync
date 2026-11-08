package com.frietsync.backend.dto.issue;

import java.util.UUID;

public record SemanticIssueResult(
        String entityType,
        UUID issueId,
        UUID sprintId,
        UUID projectId,
        String projectName,
        String title,
        String description,
        String status,
        String priority,
        double similarity
) {
}
