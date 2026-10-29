package com.frietsync.backend.entity.issue;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "issues",
        indexes = @Index(name = "idx_issues_project", columnList = "project_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "issue_number"}))
public class Issue extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "issue_number", nullable = false, updatable = false)
    private int issueNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status = IssueStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuePriority priority = IssuePriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "milestone_id")
    private UUID milestoneId;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "github_issue_number")
    private Integer githubIssueNumber;

    @Column(name = "github_issue_title", length = 500)
    private String githubIssueTitle;

    @Column(name = "github_issue_url", length = 1000)
    private String githubIssueUrl;

    @Column(name = "github_issue_repository", length = 500)
    private String githubIssueRepository;
}
