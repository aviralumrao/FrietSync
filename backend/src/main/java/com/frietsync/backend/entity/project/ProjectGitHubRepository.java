package com.frietsync.backend.entity.project;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "project_github_repositories",
        uniqueConstraints = @UniqueConstraint(columnNames = "project_id"))
@Data
@NoArgsConstructor
public class ProjectGitHubRepository extends BaseEntity {
    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "repository_owner", nullable = false, length = 255)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false, length = 255)
    private String repositoryName;
}
