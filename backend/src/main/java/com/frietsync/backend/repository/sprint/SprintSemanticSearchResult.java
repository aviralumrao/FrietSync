package com.frietsync.backend.repository.sprint;

import java.util.UUID;

public interface SprintSemanticSearchResult {
    UUID getSprintId();
    UUID getProjectId();
    String getProjectName();
    String getTitle();
    String getDescription();
    String getStatus();
    String getPriority();
    double getSimilarity();
}
