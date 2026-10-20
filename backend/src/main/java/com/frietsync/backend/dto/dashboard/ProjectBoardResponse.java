package com.frietsync.backend.dto.dashboard;

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
public class ProjectBoardResponse {
    private List<Column> columns;
    private long myTasksCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column {
        private IssueStatus status;
        private List<IssueCard> issues;
        private List<IssueStatus> allowedTransitions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueCard {
        private UUID id;
        private int issueNumber;
        private String title;
        private IssuePriority priority;
        private LocalDate dueDate;
        private Assignee assignee;
        private List<Label> labels;
        private boolean assignedToCurrentUser;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assignee {
        private UUID id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Label {
        private UUID id;
        private String name;
        private String color;
    }
}
