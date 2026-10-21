package com.frietsync.backend.entity.project;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "release_issues", uniqueConstraints = @UniqueConstraint(columnNames = {"release_id", "issue_id"}))
public class ReleaseIssue extends BaseEntity {
    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;
}
