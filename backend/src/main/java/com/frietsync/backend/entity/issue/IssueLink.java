package com.frietsync.backend.entity.issue;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "issue_links",
        indexes = {
                @Index(name = "idx_issue_links_project", columnList = "project_id"),
                @Index(name = "idx_issue_links_source", columnList = "source_issue_id"),
                @Index(name = "idx_issue_links_target", columnList = "target_issue_id")
        })
public class IssueLink extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "source_issue_id", nullable = false, updatable = false)
    private UUID sourceIssueId;

    @Column(name = "target_issue_id", nullable = false, updatable = false)
    private UUID targetIssueId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
}