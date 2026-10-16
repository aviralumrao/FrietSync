package com.frietsync.backend.dto.dashboard;

import com.frietsync.backend.entity.issue.IssueLinkType;
import com.frietsync.backend.entity.issue.IssuePriority;
import com.frietsync.backend.entity.issue.IssueStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTimelineResponse {
    private List<Group> groups;
    private List<Dependency> dependencies;
    private List<ReleaseMarker> releases;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private String type;
        private UUID id;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<IssueBar> issues;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueBar {
        private UUID id;
        private int issueNumber;
        private String title;
        private IssueStatus status;
        private IssuePriority priority;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean atRisk;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dependency {
        private UUID sourceIssueId;
        private UUID targetIssueId;
        private IssueLinkType type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReleaseMarker {
        private UUID id;
        private String name;
        private LocalDate releaseDate;
    }
}
